package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.TurnoResponseDTO;
import com.tunegocio.turnosapi.dto.TurnoServicioDTO;
import com.tunegocio.turnosapi.entity.Servicio;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.entity.Turno;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TurnoMapper {

    private final TenantDateTimeMapper tenantDateTimeMapper;

    public TurnoResponseDTO toResponseDTO(Turno turno, Tenant tenant) {
        List<Servicio> servicios = turno.getServicios().stream()
                .sorted(Comparator.comparing(Servicio::getNombre, String.CASE_INSENSITIVE_ORDER))
                .toList();

        Servicio principal = servicios.isEmpty() ? null : servicios.get(0);

        return TurnoResponseDTO.builder()
                .id(turno.getId())
                .clienteId(turno.getCliente().getId())
                .nombreCliente(turno.getCliente().getNombreCompleto())
                .emailCliente(turno.getCliente().getEmail())
                .telefonoCliente(turno.getCliente().getTelefono())
                .profesionalId(turno.getProfesional().getId())
                .nombreProfesional(turno.getProfesional().getNombre())
                .servicioId(principal != null ? principal.getId() : null)
                .nombreServicio(principal != null ? principal.getNombre() : null)
                .duracionMinutos(turno.getDuracionTotalMinutos())
                .precioServicio(turno.getPrecioTotal())
                .servicioIds(servicios.stream().map(Servicio::getId).toList())
                .servicios(servicios.stream().map(this::toTurnoServicioDTO).toList())
                .duracionTotalMinutos(turno.getDuracionTotalMinutos())
                .precioTotal(turno.getPrecioTotal())
                .fechaHoraInicio(tenantDateTimeMapper.toLocalDateTime(turno.getFechaHoraInicio(), tenant))
                .fechaHoraFin(tenantDateTimeMapper.toLocalDateTime(turno.getFechaHoraFin(), tenant))
                .estado(turno.getEstado().name())
                .notas(turno.getNotas())
                .turnoPadreId(turno.getTurnoPadre() != null ? turno.getTurnoPadre().getId() : null)
                .recurrente(turno.isRecurrente())
                .recurrenciaSemanas(turno.getRecurrenciaSemanas())
                .timezone(tenant.getTimezone())
                .createdAt(turno.getCreatedAt())
                .build();
    }

    private TurnoServicioDTO toTurnoServicioDTO(Servicio servicio) {
        return TurnoServicioDTO.builder()
                .id(servicio.getId())
                .nombre(servicio.getNombre())
                .duracionMinutos(servicio.getDuracionMinutos())
                .precio(servicio.getPrecio())
                .categoriaId(servicio.getCategoria() != null ? servicio.getCategoria().getId() : null)
                .categoriaNombre(servicio.getCategoria() != null ? servicio.getCategoria().getNombre() : null)
                .imagenUrl(servicio.getImagenUrl())
                .build();
    }
}
