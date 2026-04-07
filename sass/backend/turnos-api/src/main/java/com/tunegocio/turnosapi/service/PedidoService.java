package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.config.TenantContext;
import com.tunegocio.turnosapi.dto.CheckoutPublicoRequestDTO;
import com.tunegocio.turnosapi.dto.CheckoutResponseDTO;
import com.tunegocio.turnosapi.dto.PedidoEstadoUpdateDTO;
import com.tunegocio.turnosapi.dto.PedidoItemRequestDTO;
import com.tunegocio.turnosapi.dto.PedidoItemResponseDTO;
import com.tunegocio.turnosapi.dto.PedidoRequestDTO;
import com.tunegocio.turnosapi.dto.PedidoResponseDTO;
import com.tunegocio.turnosapi.entity.Cliente;
import com.tunegocio.turnosapi.entity.Pedido;
import com.tunegocio.turnosapi.entity.PedidoEstado;
import com.tunegocio.turnosapi.entity.PedidoItem;
import com.tunegocio.turnosapi.entity.PaymentMethod;
import com.tunegocio.turnosapi.entity.Producto;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.entity.Turno;
import com.tunegocio.turnosapi.entity.TurnoHistorial;
import com.tunegocio.turnosapi.entity.TurnoStatus;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.exception.BusinessException;
import com.tunegocio.turnosapi.exception.ConflictException;
import com.tunegocio.turnosapi.exception.ResourceNotFoundException;
import com.tunegocio.turnosapi.repository.PedidoRepository;
import com.tunegocio.turnosapi.repository.TenantRepository;
import com.tunegocio.turnosapi.repository.TurnoHistorialRepository;
import com.tunegocio.turnosapi.repository.TurnoRepository;
import com.tunegocio.turnosapi.specification.PedidoSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final TenantRepository tenantRepository;
    private final TurnoRepository turnoRepository;
    private final TurnoHistorialRepository turnoHistorialRepository;
    private final ClienteService clienteService;
    private final ProductoService productoService;
    private final PlanValidator planValidator;
    private final NotificacionService notificacionService;
    private final MercadoPagoService mercadoPagoService;
    private final StripeService stripeService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<PedidoResponseDTO> listar(PedidoEstado estado, String busqueda, Usuario actor, Pageable pageable) {
        planValidator.validarPuedeUsarEcommerce(actor.getTenant());
        Specification<Pedido> spec = Specification.where(PedidoSpecification.porEstado(estado))
                .and(PedidoSpecification.porBusqueda(busqueda));
        return pedidoRepository.findAll(spec, pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO obtener(Long id, Usuario actor) {
        planValidator.validarPuedeUsarEcommerce(actor.getTenant());
        return toDTO(buscarPorId(id));
    }

    @Transactional
    public PedidoResponseDTO crear(PedidoRequestDTO dto, Usuario actor) {
        planValidator.validarPuedeUsarEcommerce(actor.getTenant());
        Pedido pedido = construirPedido(
                dto.getClienteId(),
                dto.getNombreCliente(),
                dto.getEmailCliente(),
                dto.getTelefonoCliente(),
                dto.getItems(),
                dto.getPaymentMethod(),
                dto.getDescuento(),
                dto.getCostoEnvio(),
                dto.getDireccionEnvio(),
                dto.getNotas(),
                actor.getTenant()
        );
        Pedido guardado = pedidoRepository.save(pedido);
        auditService.log("CREATE", "Pedido", guardado.getId(), actor, Map.of("estado", guardado.getEstado().name()));
        return toDTO(guardado);
    }

    @Transactional
    public CheckoutResponseDTO checkoutPublico(CheckoutPublicoRequestDTO dto, Tenant tenant) {
        planValidator.validarPuedeUsarApiPublica(tenant);
        planValidator.validarPuedeUsarEcommerce(tenant);

        Pedido pedido = construirPedido(
                null,
                dto.getNombreCliente(),
                dto.getEmailCliente(),
                dto.getTelefonoCliente(),
                dto.getItems(),
                dto.getPaymentMethod(),
                dto.getDescuento(),
                dto.getCostoEnvio(),
                dto.getDireccionEnvio(),
                dto.getNotas(),
                tenant
        );
        Pedido guardado = pedidoRepository.save(pedido);
        PaymentGatewayResult payment = iniciarPago(guardado, tenant);

        if (payment.externalReference() != null && !payment.externalReference().isBlank()) {
            guardado.setPaymentIntentId(payment.externalReference());
            pedidoRepository.save(guardado);
        }

        auditService.log("CHECKOUT", "Pedido", guardado.getId(), tenant, null, Map.of("paymentMethod", guardado.getPaymentMethod().name()));

        return CheckoutResponseDTO.builder()
                .pedido(toDTO(guardado))
                .paymentMethod(guardado.getPaymentMethod().name())
                .externalReference(payment.externalReference())
                .checkoutUrl(payment.checkoutUrl())
                .clientSecret(payment.clientSecret())
                .build();
    }

    @Transactional
    public PedidoResponseDTO actualizarEstado(Long id, PedidoEstadoUpdateDTO dto, Usuario actor) {
        planValidator.validarPuedeUsarEcommerce(actor.getTenant());
        Pedido pedido = buscarLockedPorId(id);

        if (dto.getEstado() == PedidoEstado.PAGO_CONFIRMADO) {
            confirmarPago(pedido, pedido.getPaymentMethod(), pedido.getPaymentIntentId(), actor, actor.getTenant());
            return toDTO(pedido);
        }

        validarTransicion(pedido.getEstado(), dto.getEstado());
        if (dto.getEstado() == PedidoEstado.CANCELADO) {
            cancelarPedido(pedido, actor);
            return toDTO(pedido);
        }

        pedido.setEstado(dto.getEstado());
        Pedido guardado = pedidoRepository.save(pedido);
        auditService.log("UPDATE", "Pedido", guardado.getId(), actor, Map.of("estado", guardado.getEstado().name()));
        return toDTO(guardado);
    }

    @Transactional
    public void confirmarPagoDesdeWebhook(Long pedidoId,
                                          PaymentMethod paymentMethod,
                                          String providerPaymentId,
                                          Tenant tenant) {
        Pedido pedido = buscarLockedPorId(pedidoId);
        confirmarPago(pedido, paymentMethod, providerPaymentId, null, tenant);
    }

    @Transactional
    public void procesarWebhookMercadoPago(String payload, String signatureHeader) {
        mercadoPagoService.validarYResolverPago(payload, signatureHeader)
                .ifPresent(resolution -> ejecutarEnTenant(
                        resolution.tenantSlug(),
                        tenant -> confirmarPagoDesdeWebhook(
                                resolution.pedidoId(),
                                PaymentMethod.MERCADOPAGO,
                                resolution.providerPaymentId(),
                                tenant
                        )
                ));
    }

    @Transactional
    public void procesarWebhookStripe(String payload, String signatureHeader) {
        stripeService.validarYResolverPago(payload, signatureHeader)
                .ifPresent(resolution -> ejecutarEnTenant(
                        resolution.tenantSlug(),
                        tenant -> confirmarPagoDesdeWebhook(
                                resolution.pedidoId(),
                                PaymentMethod.STRIPE,
                                resolution.providerPaymentId(),
                                tenant
                        )
                ));
    }

    private void confirmarPago(Pedido pedido,
                               PaymentMethod paymentMethod,
                               String providerPaymentId,
                               Usuario actor,
                               Tenant tenant) {
        if (pedido.getEstado() == PedidoEstado.PAGO_CONFIRMADO
                || pedido.getEstado() == PedidoEstado.EN_PREPARACION
                || pedido.getEstado() == PedidoEstado.ENVIADO
                || pedido.getEstado() == PedidoEstado.ENTREGADO) {
            return;
        }
        validarTransicion(pedido.getEstado(), PedidoEstado.PAGO_CONFIRMADO);

        descontarStock(pedido);
        confirmarTurnosPagados(pedido, actor != null ? actor.getId() : null);

        pedido.setEstado(PedidoEstado.PAGO_CONFIRMADO);
        pedido.setPaidAt(Instant.now());
        if (paymentMethod != null) {
            pedido.setPaymentMethod(paymentMethod);
        }
        if (providerPaymentId != null && !providerPaymentId.isBlank()) {
            pedido.setPaymentIntentId(providerPaymentId);
        }

        Pedido guardado = pedidoRepository.save(pedido);
        if (actor != null) {
            auditService.log("UPDATE", "Pedido", guardado.getId(), actor, Map.of("estado", guardado.getEstado().name()));
        } else {
            auditService.log("WEBHOOK_CONFIRM", "Pedido", guardado.getId(), guardado.getCliente() != null ? guardado.getCliente().getId() : null, null, Map.of("estado", guardado.getEstado().name()));
        }
        notificacionService.enviarConfirmacionPedido(guardado, actor != null ? actor.getTenant() : tenant);
    }

    private void cancelarPedido(Pedido pedido, Usuario actor) {
        if (pedido.getEstado() == PedidoEstado.PAGO_CONFIRMADO || pedido.getEstado() == PedidoEstado.EN_PREPARACION) {
            reponerStock(pedido);
            cancelarTurnosPagados(pedido, actor.getId());
        }
        pedido.setEstado(PedidoEstado.CANCELADO);
        Pedido guardado = pedidoRepository.save(pedido);
        auditService.log("UPDATE", "Pedido", guardado.getId(), actor, Map.of("estado", guardado.getEstado().name()));
    }

    private void descontarStock(Pedido pedido) {
        for (PedidoItem item : pedido.getItems()) {
            if (item.getProducto() == null) {
                continue;
            }

            Producto producto = productoService.buscarLockedPorId(item.getProducto().getId());
            if (!producto.hasStockLimitado()) {
                continue;
            }
            if (producto.getStock() < item.getCantidad()) {
                throw new ConflictException("Stock insuficiente para el producto '" + producto.getNombre() + "'");
            }
            producto.setStock(producto.getStock() - item.getCantidad());
        }
    }

    private void reponerStock(Pedido pedido) {
        for (PedidoItem item : pedido.getItems()) {
            if (item.getProducto() == null) {
                continue;
            }

            Producto producto = productoService.buscarLockedPorId(item.getProducto().getId());
            if (!producto.hasStockLimitado()) {
                continue;
            }
            producto.setStock(producto.getStock() + item.getCantidad());
        }
    }

    private void confirmarTurnosPagados(Pedido pedido, Long actorId) {
        for (PedidoItem item : pedido.getItems()) {
            Turno turno = item.getTurno();
            if (turno == null || turno.getEstado() != TurnoStatus.PENDIENTE) {
                continue;
            }

            TurnoStatus anterior = turno.getEstado();
            turno.setEstado(TurnoStatus.CONFIRMADO);
            turnoRepository.save(turno);
            registrarHistorialTurno(turno, anterior, TurnoStatus.CONFIRMADO, actorId, "Confirmado por pago del pedido");
        }
    }

    private void cancelarTurnosPagados(Pedido pedido, Long actorId) {
        for (PedidoItem item : pedido.getItems()) {
            Turno turno = item.getTurno();
            if (turno == null) {
                continue;
            }
            if (turno.getEstado() == TurnoStatus.CANCELADO || turno.getEstado() == TurnoStatus.COMPLETADO) {
                continue;
            }

            TurnoStatus anterior = turno.getEstado();
            turno.setEstado(TurnoStatus.CANCELADO);
            turnoRepository.save(turno);
            registrarHistorialTurno(turno, anterior, TurnoStatus.CANCELADO, actorId, "Cancelado por anulacion del pedido");
        }
    }

    private Pedido construirPedido(Long clienteId,
                                   String nombreCliente,
                                   String emailCliente,
                                   String telefonoCliente,
                                   List<PedidoItemRequestDTO> items,
                                   PaymentMethod paymentMethod,
                                   BigDecimal descuento,
                                   BigDecimal costoEnvio,
                                   String direccionEnvio,
                                   String notas,
                                   Tenant tenant) {
        Cliente cliente = resolveCliente(clienteId, nombreCliente, emailCliente, telefonoCliente, tenant);
        List<PedidoItem> pedidoItems = resolverItems(items);

        BigDecimal subtotal = pedidoItems.stream()
                .map(PedidoItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal descuentoSeguro = descuento != null ? descuento : BigDecimal.ZERO;
        BigDecimal envioSeguro = costoEnvio != null ? costoEnvio : BigDecimal.ZERO;
        BigDecimal total = subtotal.subtract(descuentoSeguro).add(envioSeguro);

        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("El total del pedido no puede ser negativo");
        }

        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setNombreCliente(cliente != null ? cliente.getNombreCompleto() : nombreCliente.trim());
        pedido.setEmailCliente(cliente != null && cliente.getEmail() != null ? cliente.getEmail() : emailCliente);
        pedido.setTelefonoCliente(cliente != null && cliente.getTelefono() != null ? cliente.getTelefono() : telefonoCliente);
        pedido.setEstado(PedidoEstado.PENDIENTE_PAGO);
        pedido.setSubtotal(subtotal);
        pedido.setDescuento(descuentoSeguro);
        pedido.setCostoEnvio(envioSeguro);
        pedido.setTotal(total);
        pedido.setDireccionEnvio(direccionEnvio);
        pedido.setNotas(notas);
        pedido.setPaymentMethod(paymentMethod);
        pedidoItems.forEach(pedido::addItem);
        return pedido;
    }

    private Cliente resolveCliente(Long clienteId, String nombreCliente, String emailCliente, String telefonoCliente, Tenant tenant) {
        if (clienteId != null) {
            return clienteService.buscarPorId(clienteId);
        }
        return clienteService.buscarOCrear(emailCliente, nombreCliente.trim(), null, telefonoCliente, tenant);
    }

    private List<PedidoItem> resolverItems(List<PedidoItemRequestDTO> items) {
        Set<Long> turnosYaUsados = new HashSet<>();
        List<PedidoItem> resolved = new ArrayList<>();

        for (PedidoItemRequestDTO itemDto : items) {
            if (itemDto.getProductoId() != null) {
                resolved.add(resolveProductoItem(itemDto));
                continue;
            }

            if (!turnosYaUsados.add(itemDto.getTurnoId())) {
                throw new BusinessException("No puede repetir el mismo turno dentro del pedido");
            }
            resolved.add(resolveTurnoItem(itemDto));
        }

        return resolved;
    }

    private PedidoItem resolveProductoItem(PedidoItemRequestDTO itemDto) {
        Producto producto = productoService.buscarActivoPorId(itemDto.getProductoId());
        if (producto.hasStockLimitado() && producto.getStock() < itemDto.getCantidad()) {
            throw new ConflictException("Stock insuficiente para el producto '" + producto.getNombre() + "'");
        }

        PedidoItem item = new PedidoItem();
        item.setProducto(producto);
        item.setNombre(producto.getNombre());
        item.setPrecioUnitario(producto.getPrecioVigente());
        item.setCantidad(itemDto.getCantidad());
        item.setSubtotal(producto.getPrecioVigente().multiply(BigDecimal.valueOf(itemDto.getCantidad())));
        return item;
    }

    private PedidoItem resolveTurnoItem(PedidoItemRequestDTO itemDto) {
        if (itemDto.getCantidad() != null && itemDto.getCantidad() != 1) {
            throw new BusinessException("Los items de turno solo admiten cantidad 1");
        }

        Turno turno = turnoRepository.findById(itemDto.getTurnoId())
                .orElseThrow(() -> new ResourceNotFoundException("Turno", itemDto.getTurnoId()));
        if (turno.getEstado() == TurnoStatus.CANCELADO || turno.getEstado() == TurnoStatus.COMPLETADO) {
            throw new BusinessException("El turno seleccionado no se puede cobrar en su estado actual");
        }

        PedidoItem item = new PedidoItem();
        item.setTurno(turno);
        item.setNombre(buildNombreTurno(turno));
        item.setPrecioUnitario(turno.getPrecioTotal());
        item.setCantidad(1);
        item.setSubtotal(turno.getPrecioTotal());
        return item;
    }

    private String buildNombreTurno(Turno turno) {
        String servicios = turno.getServicios().stream()
                .sorted(Comparator.comparing(servicio -> servicio.getNombre().toLowerCase()))
                .map(servicio -> servicio.getNombre())
                .reduce((a, b) -> a + ", " + b)
                .orElse("Servicios");
        return "Turno - " + servicios;
    }

    private PaymentGatewayResult iniciarPago(Pedido pedido, Tenant tenant) {
        return switch (pedido.getPaymentMethod()) {
            case MERCADOPAGO -> mercadoPagoService.crearPreferencia(pedido, tenant);
            case STRIPE -> stripeService.crearPaymentIntent(pedido, tenant);
            case EFECTIVO, TRANSFERENCIA -> new PaymentGatewayResult(pedido.getId().toString(), null, null);
        };
    }

    private void ejecutarEnTenant(String tenantSlug, java.util.function.Consumer<Tenant> action) {
        Tenant tenant = tenantRepository.findBySlug(tenantSlug)
                .filter(Tenant::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "slug", tenantSlug));

        try (TenantContext.Scope ignored = TenantContext.openTenantSlug(tenantSlug)) {
            action.accept(tenant);
        }
    }

    private void validarTransicion(PedidoEstado actual, PedidoEstado nuevo) {
        boolean invalida = switch (nuevo) {
            case PENDIENTE_PAGO -> true;
            case PAGO_CONFIRMADO -> actual != PedidoEstado.PENDIENTE_PAGO;
            case EN_PREPARACION -> actual != PedidoEstado.PAGO_CONFIRMADO;
            case ENVIADO -> actual != PedidoEstado.EN_PREPARACION;
            case ENTREGADO -> actual != PedidoEstado.ENVIADO;
            case CANCELADO -> actual == PedidoEstado.ENTREGADO || actual == PedidoEstado.CANCELADO;
        };

        if (invalida) {
            throw new BusinessException("No se puede cambiar el estado del pedido de '" + actual + "' a '" + nuevo + "'");
        }
    }

    private void registrarHistorialTurno(Turno turno,
                                         TurnoStatus anterior,
                                         TurnoStatus nuevo,
                                         Long actorId,
                                         String notas) {
        TurnoHistorial historial = new TurnoHistorial();
        historial.setTurno(turno);
        historial.setEstadoAnterior(anterior);
        historial.setEstadoNuevo(nuevo);
        historial.setUsuarioId(actorId);
        historial.setFechaAnterior(turno.getFechaHoraInicio());
        historial.setFechaNueva(turno.getFechaHoraInicio());
        historial.setNotas(notas);
        turnoHistorialRepository.save(historial);
    }

    private Pedido buscarPorId(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", id));
    }

    private Pedido buscarLockedPorId(Long id) {
        return pedidoRepository.findLockedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", id));
    }

    private PedidoResponseDTO toDTO(Pedido pedido) {
        return PedidoResponseDTO.builder()
                .id(pedido.getId())
                .clienteId(pedido.getCliente() != null ? pedido.getCliente().getId() : null)
                .nombreCliente(pedido.getNombreCliente())
                .emailCliente(pedido.getEmailCliente())
                .telefonoCliente(pedido.getTelefonoCliente())
                .estado(pedido.getEstado().name())
                .subtotal(pedido.getSubtotal())
                .descuento(pedido.getDescuento())
                .costoEnvio(pedido.getCostoEnvio())
                .total(pedido.getTotal())
                .direccionEnvio(pedido.getDireccionEnvio())
                .notas(pedido.getNotas())
                .paymentIntentId(pedido.getPaymentIntentId())
                .paymentMethod(pedido.getPaymentMethod() != null ? pedido.getPaymentMethod().name() : null)
                .paidAt(pedido.getPaidAt())
                .items(pedido.getItems().stream().map(item -> PedidoItemResponseDTO.builder()
                        .id(item.getId())
                        .productoId(item.getProducto() != null ? item.getProducto().getId() : null)
                        .turnoId(item.getTurno() != null ? item.getTurno().getId() : null)
                        .nombre(item.getNombre())
                        .precioUnitario(item.getPrecioUnitario())
                        .cantidad(item.getCantidad())
                        .subtotal(item.getSubtotal())
                        .build()).toList())
                .createdAt(pedido.getCreatedAt())
                .build();
    }
}
