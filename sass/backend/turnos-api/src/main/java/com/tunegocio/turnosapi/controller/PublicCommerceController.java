package com.tunegocio.turnosapi.controller;

import com.tunegocio.turnosapi.dto.CategoriaProductoResponseDTO;
import com.tunegocio.turnosapi.dto.CheckoutPublicoRequestDTO;
import com.tunegocio.turnosapi.dto.CheckoutResponseDTO;
import com.tunegocio.turnosapi.dto.ProductoResponseDTO;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.service.CategoriaProductoService;
import com.tunegocio.turnosapi.service.PedidoService;
import com.tunegocio.turnosapi.service.PlanValidator;
import com.tunegocio.turnosapi.service.ProductoService;
import com.tunegocio.turnosapi.service.PublicTenantResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Commerce Publico", description = "Catalogo y checkout publico del tenant")
@RestController
@RequestMapping("/public/{slug}")
@RequiredArgsConstructor
public class PublicCommerceController {

    private final PublicTenantResolver publicTenantResolver;
    private final PlanValidator planValidator;
    private final CategoriaProductoService categoriaProductoService;
    private final ProductoService productoService;
    private final PedidoService pedidoService;

    @Operation(summary = "Listar categorias publicas de productos")
    @GetMapping("/categorias-producto")
    public ResponseEntity<List<CategoriaProductoResponseDTO>> categorias(
            @PathVariable String slug,
            HttpServletRequest request) {
        Tenant tenant = resolveTenant(slug, request);
        return ResponseEntity.ok(categoriaProductoService.listarPublico(tenant));
    }

    @Operation(summary = "Listar productos publicos")
    @GetMapping("/productos")
    public ResponseEntity<Page<ProductoResponseDTO>> listarProductos(
            @PathVariable String slug,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) BigDecimal precioMin,
            @RequestParam(required = false) BigDecimal precioMax,
            @PageableDefault(size = 20, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable,
            HttpServletRequest request) {
        Tenant tenant = resolveTenant(slug, request);
        return ResponseEntity.ok(productoService.listarPublico(nombre, categoriaId, precioMin, precioMax, tenant, pageable));
    }

    @Operation(summary = "Obtener producto publico por ID")
    @GetMapping("/productos/{id}")
    public ResponseEntity<ProductoResponseDTO> obtenerProducto(
            @PathVariable String slug,
            @PathVariable Long id,
            HttpServletRequest request) {
        Tenant tenant = resolveTenant(slug, request);
        return ResponseEntity.ok(productoService.obtenerPublico(id, tenant));
    }

    @Operation(summary = "Checkout publico")
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponseDTO> checkout(
            @PathVariable String slug,
            @Valid @RequestBody CheckoutPublicoRequestDTO dto,
            HttpServletRequest request) {
        Tenant tenant = resolveTenant(slug, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.checkoutPublico(dto, tenant));
    }

    private Tenant resolveTenant(String slug, HttpServletRequest request) {
        Tenant tenant = publicTenantResolver.resolve(slug, request);
        planValidator.validarPuedeUsarApiPublica(tenant);
        planValidator.validarPuedeUsarEcommerce(tenant);
        return tenant;
    }
}
