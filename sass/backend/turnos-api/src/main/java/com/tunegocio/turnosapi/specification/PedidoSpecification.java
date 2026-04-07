package com.tunegocio.turnosapi.specification;

import com.tunegocio.turnosapi.entity.Pedido;
import com.tunegocio.turnosapi.entity.PedidoEstado;
import org.springframework.data.jpa.domain.Specification;

public final class PedidoSpecification {

    private PedidoSpecification() {
    }

    public static Specification<Pedido> conRelaciones() {
        return (root, query, cb) -> {
            root.fetch("cliente", jakarta.persistence.criteria.JoinType.LEFT);
            root.fetch("items", jakarta.persistence.criteria.JoinType.LEFT);
            query.distinct(true);
            return cb.conjunction();
        };
    }

    public static Specification<Pedido> porEstado(PedidoEstado estado) {
        return (root, query, cb) -> estado == null ? cb.conjunction() : cb.equal(root.get("estado"), estado);
    }

    public static Specification<Pedido> porBusqueda(String busqueda) {
        return (root, query, cb) -> {
            if (busqueda == null || busqueda.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + busqueda.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("nombreCliente")), pattern),
                    cb.like(cb.lower(root.get("emailCliente")), pattern),
                    cb.like(cb.lower(root.get("telefonoCliente")), pattern)
            );
        };
    }
}
