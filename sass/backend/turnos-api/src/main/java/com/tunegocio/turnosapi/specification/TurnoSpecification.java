package com.tunegocio.turnosapi.specification;

import com.tunegocio.turnosapi.entity.Turno;
import com.tunegocio.turnosapi.entity.TurnoStatus;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class TurnoSpecification {

    private TurnoSpecification() {
    }

    public static Specification<Turno> desdeFecha(Instant desde) {
        return (root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("fechaHoraInicio"), desde);
    }

    public static Specification<Turno> hastaFecha(Instant hastaExclusiva) {
        return (root, query, cb) ->
                cb.lessThan(root.get("fechaHoraInicio"), hastaExclusiva);
    }

    public static Specification<Turno> tieneProfesional(Long profesionalId) {
        return (root, query, cb) ->
                cb.equal(root.get("profesional").get("id"), profesionalId);
    }

    public static Specification<Turno> tieneCliente(Long clienteId) {
        return (root, query, cb) ->
                cb.equal(root.get("cliente").get("id"), clienteId);
    }

    public static Specification<Turno> tieneEstado(TurnoStatus estado) {
        return (root, query, cb) ->
                cb.equal(root.get("estado"), estado);
    }

    public static Specification<Turno> conFetchCompleto() {
        return (root, query, cb) -> {
            if (query != null && Long.class != query.getResultType()) {
                root.fetch("cliente", JoinType.LEFT);
                root.fetch("profesional", JoinType.LEFT);
                root.fetch("turnoPadre", JoinType.LEFT);
                query.distinct(true);
            }
            return cb.conjunction();
        };
    }
}
