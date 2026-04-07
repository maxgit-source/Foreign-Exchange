package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.ClienteRequestDTO;
import com.tunegocio.turnosapi.dto.ClienteResponseDTO;
import com.tunegocio.turnosapi.dto.TurnoResponseDTO;
import com.tunegocio.turnosapi.entity.Cliente;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.exception.ConflictException;
import com.tunegocio.turnosapi.exception.ResourceNotFoundException;
import com.tunegocio.turnosapi.repository.ClienteRepository;
import com.tunegocio.turnosapi.repository.TurnoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final TurnoRepository turnoRepository;
    private final TurnoMapper turnoMapper;
    private final PlanValidator planValidator;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<ClienteResponseDTO> listar(String busqueda, Pageable pageable) {
        return clienteRepository.buscar(busqueda, pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public ClienteResponseDTO obtenerPorId(Long id) {
        return toDTO(buscarPorId(id));
    }

    @Transactional(readOnly = true)
    public List<TurnoResponseDTO> historial(Long clienteId, Usuario actor) {
        buscarPorId(clienteId);
        return turnoRepository.findByCliente_IdOrderByFechaHoraInicioDesc(clienteId)
                .stream()
                .map(turno -> turnoMapper.toResponseDTO(turno, actor.getTenant()))
                .toList();
    }

    @Transactional
    public ClienteResponseDTO crear(ClienteRequestDTO dto, Usuario actor) {
        planValidator.validarPuedeCrearCliente(actor.getTenant());
        String email = normalizeEmail(dto.getEmail());
        if (email != null && clienteRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ConflictException("Ya existe un cliente con el email '" + email + "'");
        }

        Cliente cliente = fromDTO(dto);
        cliente.setEmail(email);
        Cliente guardado = clienteRepository.save(cliente);

        auditService.log("CREATE", "Cliente", guardado.getId(), actor, Map.of("nombre", guardado.getNombreCompleto()));
        log.info("Cliente creado: id={}", guardado.getId());
        return toDTO(guardado);
    }

    @Transactional
    public ClienteResponseDTO actualizar(Long id, ClienteRequestDTO dto, Usuario actor) {
        Cliente cliente = buscarPorId(id);
        String email = normalizeEmail(dto.getEmail());

        if (email != null && !email.equalsIgnoreCase(cliente.getEmail())) {
            clienteRepository.findByEmailIgnoreCase(email)
                    .ifPresent(existing -> {
                        throw new ConflictException("Ya existe un cliente con el email '" + email + "'");
                    });
        }

        cliente.setNombre(dto.getNombre());
        cliente.setApellido(dto.getApellido());
        cliente.setEmail(email);
        cliente.setTelefono(dto.getTelefono());
        cliente.setNotas(dto.getNotas());

        Cliente guardado = clienteRepository.save(cliente);
        auditService.log("UPDATE", "Cliente", guardado.getId(), actor, Map.of("nombre", guardado.getNombreCompleto()));
        return toDTO(guardado);
    }

    @Transactional
    public void archivar(Long id, Usuario actor) {
        Cliente cliente = buscarPorId(id);
        cliente.setActivo(false);
        clienteRepository.save(cliente);
        auditService.log("DELETE", "Cliente", cliente.getId(), actor, Map.of("activo", false));
        log.info("Cliente archivado: id={}", id);
    }

    @Transactional
    public Cliente buscarOCrear(String email, String nombre, String apellido, String telefono, Tenant tenant) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail != null) {
            return clienteRepository.findByEmailIgnoreCase(normalizedEmail)
                    .orElseGet(() -> crearClienteDesdeBooking(normalizedEmail, nombre, apellido, telefono, tenant));
        }
        return crearClienteDesdeBooking(null, nombre, apellido, telefono, tenant);
    }

    @Transactional(readOnly = true)
    Cliente buscarPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", id));
    }

    private Cliente crearClienteDesdeBooking(String email, String nombre, String apellido, String telefono, Tenant tenant) {
        planValidator.validarPuedeCrearCliente(tenant);
        Cliente nuevo = new Cliente();
        nuevo.setNombre(nombre);
        nuevo.setApellido(apellido);
        nuevo.setEmail(email);
        nuevo.setTelefono(telefono);
        return clienteRepository.save(nuevo);
    }

    private Cliente fromDTO(ClienteRequestDTO dto) {
        Cliente cliente = new Cliente();
        cliente.setNombre(dto.getNombre());
        cliente.setApellido(dto.getApellido());
        cliente.setTelefono(dto.getTelefono());
        cliente.setNotas(dto.getNotas());
        return cliente;
    }

    ClienteResponseDTO toDTO(Cliente cliente) {
        return ClienteResponseDTO.builder()
                .id(cliente.getId())
                .nombre(cliente.getNombre())
                .apellido(cliente.getApellido())
                .nombreCompleto(cliente.getNombreCompleto())
                .email(cliente.getEmail())
                .telefono(cliente.getTelefono())
                .notas(cliente.getNotas())
                .activo(cliente.isActivo())
                .createdAt(cliente.getCreatedAt())
                .build();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
