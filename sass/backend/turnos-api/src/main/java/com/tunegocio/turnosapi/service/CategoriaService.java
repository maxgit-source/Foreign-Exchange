package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.CategoriaRequestDTO;
import com.tunegocio.turnosapi.dto.CategoriaResponseDTO;
import com.tunegocio.turnosapi.entity.Categoria;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.exception.ConflictException;
import com.tunegocio.turnosapi.exception.ResourceNotFoundException;
import com.tunegocio.turnosapi.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> listar(boolean incluirInactivas) {
        List<Categoria> categorias = incluirInactivas
                ? categoriaRepository.findByOrderByOrdenAscNombreAsc()
                : categoriaRepository.findByActivoTrueOrderByOrdenAscNombreAsc();
        return categorias.stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public Categoria buscarPorId(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", id));
    }

    @Transactional
    public CategoriaResponseDTO crear(CategoriaRequestDTO dto, Usuario actor) {
        String nombre = dto.getNombre().trim();
        validarNombreDisponible(nombre, null);
        Categoria categoria = fromDTO(new Categoria(), dto);
        Categoria guardada = categoriaRepository.save(categoria);
        auditService.log("CREATE", "Categoria", guardada.getId(), actor, Map.of("nombre", guardada.getNombre()));
        return toDTO(guardada);
    }

    @Transactional
    public CategoriaResponseDTO actualizar(Long id, CategoriaRequestDTO dto, Usuario actor) {
        Categoria categoria = buscarPorId(id);
        validarNombreDisponible(dto.getNombre().trim(), categoria.getId());
        Categoria guardada = categoriaRepository.save(fromDTO(categoria, dto));
        auditService.log("UPDATE", "Categoria", guardada.getId(), actor, Map.of("nombre", guardada.getNombre()));
        return toDTO(guardada);
    }

    @Transactional
    public void eliminar(Long id, Usuario actor) {
        Categoria categoria = buscarPorId(id);
        categoria.setActivo(false);
        categoriaRepository.save(categoria);
        auditService.log("DELETE", "Categoria", categoria.getId(), actor, Map.of("activo", false));
    }

    private void validarNombreDisponible(String nombre, Long categoriaId) {
        boolean exists = categoriaId == null
                ? categoriaRepository.existsByNombreIgnoreCase(nombre)
                : categoriaRepository.existsByNombreIgnoreCaseAndIdNot(nombre, categoriaId);
        if (exists) {
            throw new ConflictException("Ya existe una categoría con el nombre '" + nombre + "'");
        }
    }

    private Categoria fromDTO(Categoria categoria, CategoriaRequestDTO dto) {
        categoria.setNombre(dto.getNombre().trim());
        categoria.setDescripcion(dto.getDescripcion());
        categoria.setOrden(dto.getOrden() != null ? dto.getOrden() : 0);
        categoria.setActivo(dto.isActivo());
        return categoria;
    }

    private CategoriaResponseDTO toDTO(Categoria categoria) {
        return CategoriaResponseDTO.builder()
                .id(categoria.getId())
                .nombre(categoria.getNombre())
                .descripcion(categoria.getDescripcion())
                .orden(categoria.getOrden())
                .activo(categoria.isActivo())
                .createdAt(categoria.getCreatedAt())
                .build();
    }
}
