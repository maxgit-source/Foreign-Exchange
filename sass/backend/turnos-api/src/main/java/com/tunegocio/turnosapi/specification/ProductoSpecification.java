package com.tunegocio.turnosapi.specification;

import com.tunegocio.turnosapi.entity.Producto;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public final class ProductoSpecification {

    private ProductoSpecification() {
    }

    public static Specification<Producto> conCategoria() {
        return (root, query, cb) -> {
            root.fetch("categoria", jakarta.persistence.criteria.JoinType.LEFT);
            query.distinct(true);
            return cb.conjunction();
        };
    }

    public static Specification<Producto> soloActivos(boolean soloActivos) {
        return (root, query, cb) -> soloActivos ? cb.isTrue(root.get("activo")) : cb.conjunction();
    }

    public static Specification<Producto> porCategoria(Long categoriaId) {
        return (root, query, cb) ->
                categoriaId == null ? cb.conjunction() : cb.equal(root.get("categoria").get("id"), categoriaId);
    }

    public static Specification<Producto> porNombre(String nombre) {
        return (root, query, cb) -> {
            if (nombre == null || nombre.isBlank()) {
                return cb.conjunction();
            }
            return cb.like(cb.lower(root.get("nombre")), "%" + nombre.trim().toLowerCase() + "%");
        };
    }

    public static Specification<Producto> precioMin(BigDecimal precioMin) {
        return (root, query, cb) ->
                precioMin == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("precio"), precioMin);
    }

    public static Specification<Producto> precioMax(BigDecimal precioMax) {
        return (root, query, cb) ->
                precioMax == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("precio"), precioMax);
    }
}
