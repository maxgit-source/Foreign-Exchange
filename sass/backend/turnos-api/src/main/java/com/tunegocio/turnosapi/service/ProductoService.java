package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.ProductoRequestDTO;
import com.tunegocio.turnosapi.dto.ProductoResponseDTO;
import com.tunegocio.turnosapi.dto.ProductoStockUpdateDTO;
import com.tunegocio.turnosapi.entity.CategoriaProducto;
import com.tunegocio.turnosapi.entity.Producto;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.exception.BusinessException;
import com.tunegocio.turnosapi.exception.ConflictException;
import com.tunegocio.turnosapi.exception.ResourceNotFoundException;
import com.tunegocio.turnosapi.repository.ProductoRepository;
import com.tunegocio.turnosapi.specification.ProductoSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaProductoService categoriaProductoService;
    private final UploadService uploadService;
    private final PlanValidator planValidator;
    private final AuditService auditService;

    @Value("${app.upload.max-product-images:8}")
    private int maxProductImages;

    @Transactional(readOnly = true)
    public Page<ProductoResponseDTO> listar(String nombre,
                                            Long categoriaId,
                                            java.math.BigDecimal precioMin,
                                            java.math.BigDecimal precioMax,
                                            boolean incluirInactivos,
                                            Usuario actor,
                                            Pageable pageable) {
        planValidator.validarPuedeUsarEcommerce(actor.getTenant());
        return productoRepository.findAll(spec(nombre, categoriaId, precioMin, precioMax, !incluirInactivos), pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductoResponseDTO> listarPublico(String nombre,
                                                   Long categoriaId,
                                                   java.math.BigDecimal precioMin,
                                                   java.math.BigDecimal precioMax,
                                                   Tenant tenant,
                                                   Pageable pageable) {
        planValidator.validarPuedeUsarEcommerce(tenant);
        planValidator.validarPuedeUsarApiPublica(tenant);
        return productoRepository.findAll(spec(nombre, categoriaId, precioMin, precioMax, true), pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public ProductoResponseDTO obtener(Long id, Usuario actor) {
        planValidator.validarPuedeUsarEcommerce(actor.getTenant());
        return toDTO(buscarPorId(id));
    }

    @Transactional(readOnly = true)
    public ProductoResponseDTO obtenerPublico(Long id, Tenant tenant) {
        planValidator.validarPuedeUsarEcommerce(tenant);
        planValidator.validarPuedeUsarApiPublica(tenant);
        Producto producto = productoRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
        return toDTO(producto);
    }

    @Transactional
    public ProductoResponseDTO crear(ProductoRequestDTO dto, Usuario actor) {
        planValidator.validarPuedeCrearProducto(actor.getTenant());
        validarSkuDisponible(dto.getSku(), null);
        Producto guardado = productoRepository.save(fromDto(new Producto(), dto));
        auditService.log("CREATE", "Producto", guardado.getId(), actor, Map.of("nombre", guardado.getNombre()));
        return toDTO(guardado);
    }

    @Transactional
    public ProductoResponseDTO actualizar(Long id, ProductoRequestDTO dto, Usuario actor) {
        planValidator.validarPuedeUsarEcommerce(actor.getTenant());
        Producto producto = buscarPorId(id);
        validarSkuDisponible(dto.getSku(), id);
        Producto guardado = productoRepository.save(fromDto(producto, dto));
        auditService.log("UPDATE", "Producto", guardado.getId(), actor, Map.of("nombre", guardado.getNombre()));
        return toDTO(guardado);
    }

    @Transactional
    public ProductoResponseDTO actualizarStock(Long id, ProductoStockUpdateDTO dto, Usuario actor) {
        planValidator.validarPuedeUsarEcommerce(actor.getTenant());
        Producto producto = buscarPorId(id);
        producto.setStock(dto.getStock());
        Producto guardado = productoRepository.save(producto);
        auditService.log("UPDATE", "Producto", guardado.getId(), actor, Map.of("stock", guardado.getStock()));
        return toDTO(guardado);
    }

    @Transactional
    public ProductoResponseDTO agregarImagenes(Long id, List<MultipartFile> files, Usuario actor) {
        planValidator.validarPuedeUsarEcommerce(actor.getTenant());
        Producto producto = buscarPorId(id);

        if (files == null || files.isEmpty()) {
            throw new BusinessException("Debe enviar al menos una imagen");
        }

        List<String> imagenes = new ArrayList<>(imagenes(producto));
        for (MultipartFile file : files) {
            imagenes.add(uploadService.uploadImage(file, "productos"));
        }

        List<String> normalizadas = imagenes.stream()
                .filter(url -> url != null && !url.isBlank())
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new
                ));

        if (normalizadas.size() > maxProductImages) {
            throw new BusinessException("El producto no puede superar " + maxProductImages + " imagenes");
        }

        producto.setImagenes(normalizadas.toArray(String[]::new));
        Producto guardado = productoRepository.save(producto);
        auditService.log("UPDATE", "Producto", guardado.getId(), actor, Map.of("imagenes", normalizadas.size()));
        return toDTO(guardado);
    }

    @Transactional
    public void eliminar(Long id, Usuario actor) {
        planValidator.validarPuedeUsarEcommerce(actor.getTenant());
        Producto producto = buscarPorId(id);
        producto.setActivo(false);
        productoRepository.save(producto);
        auditService.log("DELETE", "Producto", producto.getId(), actor, Map.of("activo", false));
    }

    @Transactional(readOnly = true)
    Producto buscarPorId(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
    }

    @Transactional(readOnly = true)
    Producto buscarActivoPorId(Long id) {
        return productoRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
    }

    @Transactional(readOnly = true)
    Producto buscarLockedPorId(Long id) {
        return productoRepository.findLockedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
    }

    private Specification<Producto> spec(String nombre,
                                         Long categoriaId,
                                         java.math.BigDecimal precioMin,
                                         java.math.BigDecimal precioMax,
                                         boolean soloActivos) {
        return Specification.where(ProductoSpecification.conCategoria())
                .and(ProductoSpecification.soloActivos(soloActivos))
                .and(ProductoSpecification.porNombre(nombre))
                .and(ProductoSpecification.porCategoria(categoriaId))
                .and(ProductoSpecification.precioMin(precioMin))
                .and(ProductoSpecification.precioMax(precioMax));
    }

    private void validarSkuDisponible(String sku, Long excludeId) {
        String normalized = normalizeSku(sku);
        if (normalized == null) {
            return;
        }

        boolean exists = excludeId == null
                ? productoRepository.existsBySkuIgnoreCase(normalized)
                : productoRepository.existsBySkuIgnoreCaseAndIdNot(normalized, excludeId);
        if (exists) {
            throw new ConflictException("Ya existe un producto con el SKU '" + normalized + "'");
        }
    }

    private Producto fromDto(Producto producto, ProductoRequestDTO dto) {
        if (dto.getPrecioOferta() != null && dto.getPrecioOferta().compareTo(dto.getPrecio()) > 0) {
            throw new BusinessException("El precio de oferta no puede superar el precio regular");
        }

        producto.setNombre(dto.getNombre().trim());
        producto.setDescripcion(dto.getDescripcion());
        producto.setCategoria(resolveCategoria(dto.getCategoriaId()));
        producto.setPrecio(dto.getPrecio());
        producto.setPrecioOferta(dto.getPrecioOferta());
        producto.setStock(dto.getStock());
        producto.setSku(normalizeSku(dto.getSku()));
        producto.setTipo(dto.getTipo());
        producto.setPesoKg(dto.getPesoKg());
        producto.setActivo(dto.isActivo());
        if (producto.getImagenes() == null) {
            producto.setImagenes(new String[0]);
        }
        return producto;
    }

    private CategoriaProducto resolveCategoria(Long categoriaId) {
        if (categoriaId == null) {
            return null;
        }
        CategoriaProducto categoria = categoriaProductoService.buscarPorId(categoriaId);
        if (!categoria.isActivo()) {
            throw new BusinessException("La categoria seleccionada no esta activa");
        }
        return categoria;
    }

    private String normalizeSku(String sku) {
        if (sku == null || sku.isBlank()) {
            return null;
        }
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> imagenes(Producto producto) {
        return producto.getImagenes() == null ? List.of() : Arrays.stream(producto.getImagenes()).toList();
    }

    ProductoResponseDTO toDTO(Producto producto) {
        return ProductoResponseDTO.builder()
                .id(producto.getId())
                .categoriaId(producto.getCategoria() != null ? producto.getCategoria().getId() : null)
                .categoriaNombre(producto.getCategoria() != null ? producto.getCategoria().getNombre() : null)
                .nombre(producto.getNombre())
                .descripcion(producto.getDescripcion())
                .precio(producto.getPrecio())
                .precioOferta(producto.getPrecioOferta())
                .precioVigente(producto.getPrecioVigente())
                .stock(producto.getStock())
                .sku(producto.getSku())
                .imagenes(imagenes(producto))
                .tipo(producto.getTipo().name())
                .pesoKg(producto.getPesoKg())
                .activo(producto.isActivo())
                .createdAt(producto.getCreatedAt())
                .build();
    }
}
