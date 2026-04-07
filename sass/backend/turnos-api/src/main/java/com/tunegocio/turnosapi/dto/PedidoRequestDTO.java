package com.tunegocio.turnosapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tunegocio.turnosapi.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class PedidoRequestDTO {

    private Long clienteId;

    private String nombreCliente;

    @Email(message = "El email del cliente no es valido")
    private String emailCliente;

    private String telefonoCliente;

    @Valid
    @NotEmpty(message = "Debe incluir al menos un item")
    private List<PedidoItemRequestDTO> items;

    @NotNull(message = "El metodo de pago es obligatorio")
    private PaymentMethod paymentMethod;

    @DecimalMin(value = "0.0", inclusive = true, message = "El descuento debe ser mayor o igual a 0")
    private BigDecimal descuento = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", inclusive = true, message = "El costo de envio debe ser mayor o igual a 0")
    private BigDecimal costoEnvio = BigDecimal.ZERO;

    private String direccionEnvio;

    private String notas;

    @AssertTrue(message = "Debe informar un cliente existente o nombre y email del comprador")
    @JsonIgnore
    public boolean isClienteValido() {
        if (clienteId != null) {
            return true;
        }
        return nombreCliente != null && !nombreCliente.isBlank()
                && emailCliente != null && !emailCliente.isBlank();
    }
}
