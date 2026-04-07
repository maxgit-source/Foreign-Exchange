package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.CategoriaProductoRequestDTO;
import com.tunegocio.turnosapi.dto.CategoriaProductoResponseDTO;
import com.tunegocio.turnosapi.entity.CategoriaProducto;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.exception.ConflictException;
import com.tunegocio.turnosapi.exception.ResourceNotFoundException;
import com.tunegocio.turnosapi.repository.CategoriaProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoriaProductoService {

    private final CategoriaProductoRepository categoriaProductoRepository;
    private final PlanValidator planValidator;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<CategoriaProductoResponseDTO> listar(boolean incluirInactivas, Usuario actor) {
        planValidator.validarPuedeUsarEcommerce(actor.getTenant());
        List<CategoriaProducto> categorias = incluirInactivas
                ? categoriaProductoRepository.findByOrderByOrdenAscNombreAsc()
                : categoriaProductoRepository.findByActivoTrueOrderByOrdenAscNombreAsc();
        return categorias.stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoriaProductoResponseDTO> listarPublico(Tenant tenant) {
        planValidator.validarPuedeUsarEcommerce(tenant);
        planValidator.validarPuedeUsarApiPublica(tenant);
        return categoriaProductoRepository.findByActivoTrueOrderByOrdenAscNombreAsc()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public CategoriaProductoResponseDTO crear(CategoriaProductoRequestDTO dto, Usuario actor) {
        planValidator.validarPuedeUsarEcommerce(actor.getTenant());
        validarNombreDisponible(dto.getNombre().trim(), null);

        CategoriaProducto categoria = fromDto(new CategoriaProducto(), dto);
        CategoriaProducto guardada = categoriaProductoRepository.save(categoria);
        auditService.log("CREATE", "CategoriaProducto", guardada.getId(), actor, Map.of("nombre", guardada.getNombre()));
        return toDTO(guardada);
    }

    @Transactional
    public CategoriaProductoResponseDTO actualizar(Long id, CategoriaProductoRequestDTO dto, Usuario actor) {
        planValidator.validarPuedeUsarEcommerce(actor.getTenant());
        CategoriaProducto categoria = buscarPorId(id);
        validarNombreDisponible(dto.getNombre().trim(), id);

        CategoriaProducto guardada = categoriaProductoRepository.save(fromDto(categoria, dto));
        auditService.log("UPDATE", "CategoriaProducto", guardada.getId(), actor, Map.of("nombre", guardada.getNombre()));
        return toDTO(guardada);
    }

    @Transactional
    public void eliminar(Long id, Usuario actor) {
        planValidator.validarPuedeUsarEcommerce(actor.getTenant());
        CategoriaProducto categoria = buscarPorId(id);
        categoria.setActivo(false);
        categoriaProductoRepository.save(categoria);
        auditService.log("DELETE", "CategoriaProducto", categoria.getId(), actor, Map.of("activo", false));
    }

    @Transactional(readOnly = true)
    CategoriaProducto buscarPorId(Long id) {
        return categoriaProductoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CategoriaProducto", id));
    }

    private void validarNombreDisponible(String nombre, Long excludeId) {
        boolean exists = excludeId == null
                ? categoriaProductoRepository.existsByNombreIgnoreCase(nombre)
                : categoriaProductoRepository.existsByNombreIgnoreCaseAndIdNot(nombre, excludeId);
        if (exists) {
            throw new ConflictException("Ya existe una categoria de producto con el nombre '" + nombre + "'");
        }
    }

    private CategoriaProducto fromDto(CategoriaProducto categoria, CategoriaProductoRequestDTO dto) {
        categoria.setNombre(dto.getNombre().trim());
        categoria.setDescripcion(dto.getDescripcion());
        categoria.setImagenUrl(dto.getImagenUrl());
        categoria.setOrden(dto.getOrden() != null ? dto.getOrden() : 0);
        categoria.setActivo(dto.isActivo());
        return categoria;
    }

    private CategoriaProductoResponseDTO toDTO(CategoriaProducto categoria) {
        return CategoriaProductoResponseDTO.builder()
                .id(categoria.getId())
                .nombre(categoria.getNombre())
                .descripcion(categoria.getDescripcion())
                .imagenUrl(categoria.getImagenUrl())
                .orden(categoria.getOrden())
                .activo(categoria.isActivo())
                .createdAt(categoria.getCreatedAt())
                .build();
    }
}
