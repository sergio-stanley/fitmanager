package com.fitmanager.api.dto;

import com.fitmanager.api.entity.StudentStatus;
import jakarta.validation.constraints.NotNull;

/** Corpo de PATCH /api/students/{id}/status (API_CONTRACT.md, seção 2.8). */
public record StudentStatusRequest(

        @NotNull(message = "O status é obrigatório")
        StudentStatus status) {
}
