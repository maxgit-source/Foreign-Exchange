package com.tunegocio.turnosapi.dto;

import com.tunegocio.turnosapi.entity.TipoProducto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductoRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 200, message = "El nombre no puede superar 200 caracteres")
    private String nombre;

    private String descripcion;

    private Long categoriaId;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El precio debe ser mayor o igual a 0")
    private BigDecimal precio;

    @DecimalMin(value = "0.0", inclusive = true, message = "El precio de oferta debe ser mayor o igual a 0")
    private BigDecimal precioOferta;

    @NotNull(message = "El stock es obligatorio")
    @Min(value = -1, message = "El stock minimo permitido es -1")
    private Integer stock;

    @Size(max = 100, message = "El SKU no puede superar 100 caracteres")
    private String sku;

    @NotNull(message = "El tipo de producto es obligatorio")
    private TipoProducto tipo = TipoProducto.FISICO;

    @DecimalMin(value = "0.0", inclusive = true, message = "El peso debe ser mayor o igual a 0")
    private BigDecimal pesoKg;

    private boolean activo = true;
}
