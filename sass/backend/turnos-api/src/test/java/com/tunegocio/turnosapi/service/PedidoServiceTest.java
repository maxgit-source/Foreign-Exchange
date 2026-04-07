package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.config.TenantContext;
import com.tunegocio.turnosapi.dto.CheckoutPublicoRequestDTO;
import com.tunegocio.turnosapi.dto.CheckoutResponseDTO;
import com.tunegocio.turnosapi.dto.PedidoItemRequestDTO;
import com.tunegocio.turnosapi.entity.Cliente;
import com.tunegocio.turnosapi.entity.Pedido;
import com.tunegocio.turnosapi.entity.PedidoEstado;
import com.tunegocio.turnosapi.entity.PaymentMethod;
import com.tunegocio.turnosapi.entity.Producto;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.repository.PedidoRepository;
import com.tunegocio.turnosapi.repository.TenantRepository;
import com.tunegocio.turnosapi.repository.TurnoHistorialRepository;
import com.tunegocio.turnosapi.repository.TurnoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TurnoRepository turnoRepository;
    @Mock
    private TurnoHistorialRepository turnoHistorialRepository;
    @Mock
    private ClienteService clienteService;
    @Mock
    private ProductoService productoService;
    @Mock
    private PlanValidator planValidator;
    @Mock
    private NotificacionService notificacionService;
    @Mock
    private MercadoPagoService mercadoPagoService;
    @Mock
    private StripeService stripeService;
    @Mock
    private AuditService auditService;

    private PedidoService pedidoService;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        pedidoService = new PedidoService(
                pedidoRepository,
                tenantRepository,
                turnoRepository,
                turnoHistorialRepository,
                clienteService,
                productoService,
                planValidator,
                notificacionService,
                mercadoPagoService,
                stripeService,
                auditService
        );

        tenant = new Tenant();
        tenant.setId(1L);
        tenant.setNombre("Demo");
        tenant.setSlug("demo");
        tenant.setTimezone("America/Argentina/Buenos_Aires");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void checkoutPublico_debeIniciarPagoStripeConElTenantCorrecto() {
        Producto producto = new Producto();
        producto.setId(20L);
        producto.setNombre("Shampoo");
        producto.setPrecio(BigDecimal.valueOf(12000));
        producto.setStock(10);

        Cliente cliente = new Cliente();
        cliente.setId(30L);
        cliente.setNombre("Ana");
        cliente.setApellido("Cliente");
        cliente.setEmail("ana@test.com");

        when(clienteService.buscarOCrear(eq("ana@test.com"), eq("Ana"), isNull(), eq("123"), eq(tenant))).thenReturn(cliente);
        when(productoService.buscarActivoPorId(20L)).thenReturn(producto);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> {
            Pedido pedido = invocation.getArgument(0);
            if (pedido.getId() == null) {
                pedido.setId(44L);
                pedido.setCreatedAt(LocalDateTime.now());
                pedido.setUpdatedAt(LocalDateTime.now());
            }
            return pedido;
        });
        when(stripeService.crearPaymentIntent(any(Pedido.class), eq(tenant)))
                .thenReturn(new PaymentGatewayResult("pi_44", null, "secret_44"));

        CheckoutPublicoRequestDTO dto = new CheckoutPublicoRequestDTO();
        dto.setNombreCliente("Ana");
        dto.setEmailCliente("ana@test.com");
        dto.setTelefonoCliente("123");
        dto.setPaymentMethod(PaymentMethod.STRIPE);
        dto.setItems(List.of(productoItem(20L, 2)));
        dto.setCostoEnvio(BigDecimal.ZERO);
        dto.setDescuento(BigDecimal.ZERO);

        CheckoutResponseDTO response = pedidoService.checkoutPublico(dto, tenant);

        assertEquals("secret_44", response.getClientSecret());
        assertEquals("pi_44", response.getExternalReference());
        assertEquals(Long.valueOf(44L), response.getPedido().getId());
        verify(stripeService).crearPaymentIntent(any(Pedido.class), eq(tenant));
    }

    @Test
    void procesarWebhookStripe_debeAbrirElSchemaDelTenantYConfirmarElPedido() {
        Pedido pedido = new Pedido();
        pedido.setId(10L);
        pedido.setNombreCliente("Ana");
        pedido.setEmailCliente("ana@test.com");
        pedido.setEstado(PedidoEstado.PENDIENTE_PAGO);
        pedido.setSubtotal(BigDecimal.valueOf(10000));
        pedido.setTotal(BigDecimal.valueOf(10000));
        pedido.setPaymentMethod(PaymentMethod.STRIPE);

        when(stripeService.validarYResolverPago("payload", "t=1,v1=firma"))
                .thenReturn(Optional.of(new PaymentWebhookResolution("demo", 10L, "pi_ok")));
        when(tenantRepository.findBySlug("demo")).thenReturn(Optional.of(tenant));
        when(pedidoRepository.findLockedById(10L)).thenAnswer(invocation -> {
            assertEquals("tenant_demo", TenantContext.getCurrentSchema());
            return Optional.of(pedido);
        });
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        pedidoService.procesarWebhookStripe("payload", "t=1,v1=firma");

        assertEquals(PedidoEstado.PAGO_CONFIRMADO, pedido.getEstado());
        assertEquals("pi_ok", pedido.getPaymentIntentId());
        assertNotNull(pedido.getPaidAt());
        assertEquals(TenantContext.PUBLIC_SCHEMA, TenantContext.getCurrentSchema());
        verify(notificacionService).enviarConfirmacionPedido(pedido, tenant);
    }

    private PedidoItemRequestDTO productoItem(Long productoId, int cantidad) {
        PedidoItemRequestDTO item = new PedidoItemRequestDTO();
        item.setProductoId(productoId);
        item.setCantidad(cantidad);
        return item;
    }
}
