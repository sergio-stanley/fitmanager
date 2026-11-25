package com.fitmanager.api.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.fitmanager.api.entity.Student;
import com.fitmanager.api.entity.StudentStatus;

/** Modelo Student da resposta (API_CONTRACT.md, seção 2.1). Mapeamento manual, sem expor a entidade. */
public record StudentResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        LocalDate birthDate,
        StudentStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static StudentResponse from(Student student) {
        return new StudentResponse(
                student.getId(),
                student.getFullName(),
                student.getEmail(),
                student.getPhone(),
                student.getBirthDate(),
                student.getStatus(),
                student.getCreatedAt(),
                student.getUpdatedAt());
    }
}
