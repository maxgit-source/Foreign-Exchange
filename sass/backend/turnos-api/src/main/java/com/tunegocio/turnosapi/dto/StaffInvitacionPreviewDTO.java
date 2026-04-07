package com.tunegocio.turnosapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class StaffInvitacionPreviewDTO {

    private String email;
    private String tenantNombre;
    private LocalDateTime expiresAt;
}
