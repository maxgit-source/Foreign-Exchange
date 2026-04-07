package com.tunegocio.turnosapi.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "pedidos",
        indexes = {
                @Index(name = "idx_pedidos_created", columnList = "created_at"),
                @Index(name = "idx_pedidos_cliente", columnList = "cliente_id"),
                @Index(name = "idx_pedidos_estado", columnList = "estado"),
                @Index(name = "idx_pedidos_payment_intent", columnList = "payment_intent_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Pedido extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "nombre_cliente", length = 200)
    private String nombreCliente;

    @Column(name = "email_cliente", length = 200)
    private String emailCliente;

    @Column(name = "telefono_cliente", length = 30)
    private String telefonoCliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PedidoEstado estado = PedidoEstado.PENDIENTE_PAGO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(name = "costo_envio", nullable = false, precision = 10, scale = 2)
    private BigDecimal costoEnvio = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "direccion_envio", columnDefinition = "TEXT")
    private String direccionEnvio;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Column(name = "payment_intent_id", length = 200)
    private String paymentIntentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 50)
    private PaymentMethod paymentMethod;

    @Column(name = "paid_at")
    private Instant paidAt;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PedidoItem> items = new ArrayList<>();

    public void addItem(PedidoItem item) {
        items.add(item);
        item.setPedido(this);
    }
}
