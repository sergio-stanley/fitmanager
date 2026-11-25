package com.fitmanager.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.fitmanager.api.TestcontainersConfiguration;
import com.fitmanager.api.dto.StudentRequest;
import com.fitmanager.api.dto.StudentResponse;
import com.fitmanager.api.entity.StudentStatus;
import com.fitmanager.api.repository.StudentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * O que o Mockito não consegue provar: com o banco real, a resposta do service já sai com os
 * timestamps definitivos (graças ao flush), iguais aos que ficam gravados.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, StudentService.class})
class StudentServicePersistenceTest {

    private static final LocalDate BIRTH = LocalDate.of(1998, 4, 15);

    @Autowired
    private StudentService service;

    @Autowired
    private StudentRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldReturnTimestampsOnCreate() {
        StudentResponse created = service.create(new StudentRequest("Ana Souza", "ana@email.com", null, BIRTH));

        assertThat(created.id()).isNotNull();
        assertThat(created.createdAt()).isNotNull().isEqualTo(created.updatedAt());
    }

    @Test
    void shouldReturnNewUpdatedAtAndPreserveCreatedAtOnUpdate() throws InterruptedException {
        StudentResponse created = service.create(new StudentRequest("Ana Souza", "ana@email.com", null, BIRTH));
        Thread.sleep(5);

        StudentResponse updated = service.update(created.id(),
                new StudentRequest("Ana Souza Lima", "ana@email.com", null, BIRTH));
        entityManager.clear();
        var stored = repository.findById(created.id()).orElseThrow();

        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
        assertThat(updated.updatedAt()).isAfter(created.updatedAt());
        // A resposta mostra exatamente o que foi gravado.
        assertThat(updated.updatedAt()).isEqualTo(stored.getUpdatedAt());
        assertThat(stored.getCreatedAt()).isEqualTo(created.createdAt());
    }

    @Test
    void shouldReturnNewUpdatedAtOnStatusChangeAndKeepItWhenRepeated() throws InterruptedException {
        StudentResponse created = service.create(new StudentRequest("Ana Souza", "ana@email.com", null, BIRTH));
        Thread.sleep(5);

        StudentResponse deactivated = service.changeStatus(created.id(), StudentStatus.INACTIVE);
        Thread.sleep(5);
        StudentResponse repeated = service.changeStatus(created.id(), StudentStatus.INACTIVE);

        assertThat(deactivated.updatedAt()).isAfter(created.updatedAt());
        assertThat(repeated.updatedAt()).isEqualTo(deactivated.updatedAt()); // idempotente
        assertThat(repeated.status()).isEqualTo(StudentStatus.INACTIVE);
    }
}
