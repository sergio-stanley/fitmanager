package com.fitmanager.api.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Aluno da academia. Mapeia a tabela criada pela migration V1.
 * Sem setters públicos: o estado muda apenas pelos métodos de domínio.
 * Os valores recebidos já devem estar normalizados e validados (responsabilidade do DTO/service).
 */
@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "email", nullable = false, length = 160)
    private String email;

    @Column(name = "phone", length = 11)
    private String phone;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StudentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Exigido pelo JPA. Não deve ser usado pelo código da aplicação. */
    protected Student() {
    }

    /** Novo aluno: sempre começa como ACTIVE. */
    public Student(String fullName, String email, String phone, LocalDate birthDate) {
        updateData(fullName, email, phone, birthDate);
        this.status = StudentStatus.ACTIVE;
    }

    /** Substitui os dados editáveis (PUT). Não altera status nem createdAt. */
    public void updateData(String fullName, String email, String phone, LocalDate birthDate) {
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.email = Objects.requireNonNull(email, "email");
        this.phone = phone;
        this.birthDate = Objects.requireNonNull(birthDate, "birthDate");
    }

    /** Idempotente: repetir o status atual não gera alteração (nem atualiza updatedAt). */
    public void changeStatus(StudentStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "status");
    }

    // Um único instante para os dois campos na criação, truncado em microssegundos
    // (precisão do TIMESTAMPTZ): o valor em memória é igual ao que o banco devolve depois.
    @PrePersist
    void onCreate() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.createdAt = now;
        this.updatedAt = now;
    }

    // Só é chamado quando o Hibernate detecta alguma mudança na entidade.
    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public StudentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
