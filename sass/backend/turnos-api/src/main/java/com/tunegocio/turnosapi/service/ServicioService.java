package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.ServicioRequestDTO;
import com.tunegocio.turnosapi.dto.ServicioResponseDTO;
import com.tunegocio.turnosapi.entity.Categoria;
import com.tunegocio.turnosapi.entity.Servicio;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.exception.BusinessException;
import com.tunegocio.turnosapi.exception.ConflictException;
import com.tunegocio.turnosapi.exception.ResourceNotFoundException;
import com.tunegocio.turnosapi.repository.ServicioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServicioService {

    private final ServicioRepository servicioRepository;
    private final CategoriaService categoriaService;
    private final UploadService uploadService;
    private final PlanValidator planValidator;
    private final AuditService auditService;

    @Cacheable(value = "servicios", key = "'activos:' + #categoriaId")
    @Transactional(readOnly = true)
    public List<ServicioResponseDTO> listarActivos(Long categoriaId) {
        List<Servicio> servicios = categoriaId == null
                ? servicioRepository.findByActivoTrueOrderByNombreAsc()
                : servicioRepository.findByActivoTrueAndCategoria_IdOrderByNombreAsc(categoriaId);
        return servicios.stream().map(this::toDTO).toList();
    }

    @Cacheable(value = "servicios", key = "'todos:' + #categoriaId")
    @Transactional(readOnly = true)
    public List<ServicioResponseDTO> listarTodos(Long categoriaId) {
        List<Servicio> servicios = categoriaId == null
                ? servicioRepository.findByOrderByNombreAsc()
                : servicioRepository.findByCategoria_IdOrderByNombreAsc(categoriaId);
        return servicios.stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public ServicioResponseDTO obtenerPorId(Long id) {
        return toDTO(buscarPorId(id));
    }

    @CacheEvict(value = "servicios", allEntries = true)
    @Transactional
    public ServicioResponseDTO crear(ServicioRequestDTO dto, Usuario actor) {
        planValidator.validarPuedeCrearServicio(actor.getTenant());
        String nombre = dto.getNombre().trim();
        validarNombreDisponible(nombre, null);
        Servicio guardado = servicioRepository.save(fromDTO(new Servicio(), dto));
        auditService.log("CREATE", "Servicio", guardado.getId(), actor, Map.of("nombre", guardado.getNombre()));
        log.info("Servicio creado: id={}, nombre='{}'", guardado.getId(), guardado.getNombre());
        return toDTO(guardado);
    }

    @CacheEvict(value = "servicios", allEntries = true)
    @Transactional
    public ServicioResponseDTO actualizar(Long id, ServicioRequestDTO dto, Usuario actor) {
        Servicio servicio = buscarPorId(id);
        validarNombreDisponible(dto.getNombre().trim(), servicio.getId());
        Servicio guardado = servicioRepository.save(fromDTO(servicio, dto));
        auditService.log("UPDATE", "Servicio", guardado.getId(), actor, Map.of("nombre", guardado.getNombre()));
        return toDTO(guardado);
    }

    @CacheEvict(value = "servicios", allEntries = true)
    @Transactional
    public ServicioResponseDTO actualizarImagen(Long id, MultipartFile file, Usuario actor) {
        Servicio servicio = buscarPorId(id);
        String imageUrl = uploadService.uploadImage(file, "servicios");
        servicio.setImagenUrl(imageUrl);
        Servicio guardado = servicioRepository.save(servicio);
        auditService.log("UPDATE", "Servicio", guardado.getId(), actor, Map.of("imagenUrl", imageUrl));
        return toDTO(guardado);
    }

    @CacheEvict(value = "servicios", allEntries = true)
    @Transactional
    public void eliminar(Long id, Usuario actor) {
        Servicio servicio = buscarPorId(id);
        servicio.setActivo(false);
        servicioRepository.save(servicio);
        auditService.log("DELETE", "Servicio", servicio.getId(), actor, Map.of("activo", false));
        log.info("Servicio desactivado: id={}", id);
    }

    @Transactional(readOnly = true)
    public List<Servicio> buscarPorIdsActivos(List<Long> servicioIds) {
        List<Long> ids = normalizarIds(servicioIds);
        if (ids.isEmpty()) {
            throw new BusinessException("Debe seleccionar al menos un servicio");
        }

        List<Servicio> servicios = servicioRepository.findByIdIn(ids);
        if (servicios.size() != ids.size()) {
            throw new ResourceNotFoundException("Servicio", "ids", ids);
        }

        Map<Long, Servicio> byId = new HashMap<>();
        servicios.forEach(servicio -> byId.put(servicio.getId(), servicio));

        List<Servicio> ordered = ids.stream()
                .map(byId::get)
                .toList();

        ordered.forEach(servicio -> {
            if (!servicio.isActivo()) {
                throw new BusinessException("El servicio '" + servicio.getNombre() + "' no está disponible");
            }
        });

        return ordered;
    }

    @Transactional(readOnly = true)
    Servicio buscarPorId(Long id) {
        return servicioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio", id));
    }

    private void validarNombreDisponible(String nombre, Long servicioId) {
        boolean exists = servicioId == null
                ? servicioRepository.existsByNombreIgnoreCase(nombre)
                : servicioRepository.existsByNombreIgnoreCaseAndIdNot(nombre, servicioId);

        if (exists) {
            throw new ConflictException("Ya existe un servicio con el nombre '" + nombre + "'");
        }
    }

    private Servicio fromDTO(Servicio servicio, ServicioRequestDTO dto) {
        servicio.setNombre(dto.getNombre().trim());
        servicio.setDescripcion(dto.getDescripcion());
        servicio.setDuracionMinutos(dto.getDuracionMinutos());
        servicio.setPrecio(dto.getPrecio());
        servicio.setCategoria(resolveCategoria(dto.getCategoriaId()));
        servicio.setActivo(dto.isActivo());
        return servicio;
    }

    private Categoria resolveCategoria(Long categoriaId) {
        if (categoriaId == null) {
            return null;
        }

        Categoria categoria = categoriaService.buscarPorId(categoriaId);
        if (!categoria.isActivo()) {
            throw new BusinessException("La categoría seleccionada no está activa");
        }
        return categoria;
    }

    private List<Long> normalizarIds(List<Long> servicioIds) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (servicioIds != null) {
            servicioIds.stream()
                    .filter(id -> id != null && id > 0)
                    .forEach(ids::add);
        }
        return new ArrayList<>(ids);
    }

    private ServicioResponseDTO toDTO(Servicio servicio) {
        return ServicioResponseDTO.builder()
                .id(servicio.getId())
                .nombre(servicio.getNombre())
                .descripcion(servicio.getDescripcion())
                .duracionMinutos(servicio.getDuracionMinutos())
                .precio(servicio.getPrecio())
                .categoriaId(servicio.getCategoria() != null ? servicio.getCategoria().getId() : null)
                .categoriaNombre(servicio.getCategoria() != null ? servicio.getCategoria().getNombre() : null)
                .imagenUrl(servicio.getImagenUrl())
                .activo(servicio.isActivo())
                .createdAt(servicio.getCreatedAt())
                .build();
    }
}
