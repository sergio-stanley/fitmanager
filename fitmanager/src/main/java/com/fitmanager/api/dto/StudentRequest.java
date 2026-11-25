package com.fitmanager.api.dto;

import java.time.LocalDate;
import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /api/students e PUT /api/students/{id} (API_CONTRACT.md, seção 2.2).
 * O construtor compacto normaliza os valores; como o Jackson cria o record antes do Bean Validation
 * rodar, as regras abaixo valem para o valor JÁ normalizado, como exige o contrato.
 */
public record StudentRequest(

        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 3, max = 120, message = "O nome deve ter entre 3 e 120 caracteres")
        String fullName,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "O e-mail deve ser válido")
        @Size(max = 160, message = "O e-mail deve ter no máximo 160 caracteres")
        String email,

        @Pattern(regexp = "\\d{10,11}", message = "O telefone deve conter 10 ou 11 dígitos, apenas números")
        String phone,

        @NotNull(message = "A data de nascimento é obrigatória")
        @Past(message = "A data de nascimento deve estar no passado")
        LocalDate birthDate) {

    public StudentRequest {
        fullName = fullName == null ? null : fullName.strip().replaceAll("\\s+", " ");
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        // Ausente, "" ou só espaços = sem telefone. A máscara NÃO é removida: "(11) 9..." é inválido.
        phone = phone == null || phone.isBlank() ? null : phone.strip();
    }
}
