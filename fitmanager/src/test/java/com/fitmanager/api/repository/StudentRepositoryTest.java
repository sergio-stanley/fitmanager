package com.fitmanager.api.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import com.fitmanager.api.TestcontainersConfiguration;
import com.fitmanager.api.entity.Student;
import com.fitmanager.api.entity.StudentStatus;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class StudentRepositoryTest {

    private static final Sort BY_NAME_THEN_ID = Sort.by("fullName").and(Sort.by("id"));

    @Autowired
    private StudentRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Value("${spring.jpa.hibernate.ddl-auto}")
    private String ddlAuto;

    // ---------- mapeamento e ciclo de vida ----------

    @Test
    void shouldRunWithSchemaValidationAgainstFlywayMigration() {
        // Se a entidade não batesse com a V1, o contexto nem subiria com ddl-auto=validate.
        assertThat(ddlAuto).isEqualTo("validate");
    }

    @Test
    void shouldPersistNewStudentAsActiveWithEqualTimestamps() {
        Student saved = repository.saveAndFlush(newStudent("Ana Souza", "ana.souza@email.com", "11987654321"));

        assertThat(saved.getId()).isNotNull().isPositive();
        assertThat(saved.getStatus()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(saved.getCreatedAt()).isNotNull().isEqualTo(saved.getUpdatedAt());
    }

    @Test
    void shouldReloadExactlyTheSameValuesFromDatabase() {
        Student saved = repository.saveAndFlush(newStudent("Ana Souza", "ana.souza@email.com", null));
        entityManager.clear(); // força a próxima leitura a vir do banco, não do cache da sessão

        Student loaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getFullName()).isEqualTo("Ana Souza");
        assertThat(loaded.getEmail()).isEqualTo("ana.souza@email.com");
        assertThat(loaded.getPhone()).isNull();
        assertThat(loaded.getBirthDate()).isEqualTo(LocalDate.of(1998, 4, 15));
        assertThat(loaded.getStatus()).isEqualTo(StudentStatus.ACTIVE);
        // Truncado em microssegundos no @PrePersist: memória e banco têm o mesmo valor.
        assertThat(loaded.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(loaded.getUpdatedAt()).isEqualTo(saved.getUpdatedAt());
    }

    @Test
    void shouldPreserveCreatedAtAndAdvanceUpdatedAtOnUpdate() throws InterruptedException {
        Student student = repository.saveAndFlush(newStudent("Ana Souza", "ana.souza@email.com", null));
        Instant createdAt = student.getCreatedAt();
        Thread.sleep(5); // garante que o relógio avance entre a criação e a edição

        student.updateData("Ana Souza Lima", "ana.lima@email.com", "11987654321", LocalDate.of(1998, 4, 15));
        repository.saveAndFlush(student);
        entityManager.clear();

        Student loaded = repository.findById(student.getId()).orElseThrow();
        assertThat(loaded.getFullName()).isEqualTo("Ana Souza Lima");
        assertThat(loaded.getCreatedAt()).isEqualTo(createdAt);
        assertThat(loaded.getUpdatedAt()).isAfter(createdAt);
    }

    @Test
    void shouldPersistStatusChange() {
        Student student = repository.saveAndFlush(newStudent("Ana Souza", "ana.souza@email.com", null));

        student.changeStatus(StudentStatus.INACTIVE);
        repository.saveAndFlush(student);
        entityManager.clear();

        assertThat(repository.findById(student.getId()).orElseThrow().getStatus())
                .isEqualTo(StudentStatus.INACTIVE);
    }

    @Test
    void shouldNotTouchUpdatedAtWhenStatusIsUnchanged() throws InterruptedException {
        Student student = repository.saveAndFlush(newStudent("Ana Souza", "ana.souza@email.com", null));
        Instant updatedAt = student.getUpdatedAt();
        Thread.sleep(5);

        student.changeStatus(StudentStatus.ACTIVE); // mesmo status: operação idempotente
        repository.saveAndFlush(student);
        entityManager.clear();

        assertThat(repository.findById(student.getId()).orElseThrow().getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void shouldExposeUniqueEmailConstraintNameOnDuplicate() {
        repository.saveAndFlush(newStudent("Ana Souza", "ana.souza@email.com", null));

        // O Passo 4 usará o nome da constraint para converter SOMENTE esta violação em 409.
        assertThatThrownBy(() -> repository.saveAndFlush(newStudent("Outra Ana", "ana.souza@email.com", null)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .cause()
                .isInstanceOf(ConstraintViolationException.class)
                .extracting(e -> ((ConstraintViolationException) e).getConstraintName())
                .isEqualTo("uk_students_email");
    }

    // ---------- existência de e-mail ----------

    @Test
    void shouldCheckEmailExistence() {
        Student ana = repository.save(newStudent("Ana Souza", "ana.souza@email.com", null));
        Student bruno = repository.save(newStudent("Bruno Lima", "bruno.lima@email.com", null));

        assertThat(repository.existsByEmail("ana.souza@email.com")).isTrue();
        assertThat(repository.existsByEmail("nao.existe@email.com")).isFalse();
        // Edição: o próprio e-mail não conta como duplicidade; o de outro aluno, sim.
        assertThat(repository.existsByEmailAndIdNot("ana.souza@email.com", ana.getId())).isFalse();
        assertThat(repository.existsByEmailAndIdNot("ana.souza@email.com", bruno.getId())).isTrue();
    }

    // ---------- pesquisa e filtro ----------

    @Test
    void shouldSearchByNameIgnoringCase() {
        seedThreeStudents();

        assertThat(namesOf(search("ANA", null))).containsExactly("Ana Souza");
    }

    @Test
    void shouldSearchByEmailFragment() {
        seedThreeStudents();

        assertThat(namesOf(search("fit.com", null))).containsExactly("Bruno Lima");
    }

    @Test
    void shouldReturnAllWhenSearchIsEmptyOrBlank() {
        seedThreeStudents();

        assertThat(search(null, null).getTotalElements()).isEqualTo(3);
        assertThat(search("   ", null).getTotalElements()).isEqualTo(3);
    }

    @Test
    void shouldFilterByStatus() {
        seedThreeStudents();

        assertThat(namesOf(search("", StudentStatus.INACTIVE))).containsExactly("Carla Mendes");
        assertThat(namesOf(search("", StudentStatus.ACTIVE))).containsExactly("Ana Souza", "Bruno Lima");
    }

    @Test
    void shouldCombineSearchAndStatus() {
        seedThreeStudents(); // Ana (ativa) e Carla (inativa) têm e-mail em email.com

        assertThat(namesOf(search("email.com", StudentStatus.ACTIVE))).containsExactly("Ana Souza");
    }

    @Test
    void shouldTreatLikeWildcardsLiterally() {
        seedThreeStudents();

        // Sem escape, "_" e "%" casariam com qualquer texto e retornariam todos os alunos.
        assertThat(search("_", null).getTotalElements()).isZero();
        assertThat(search("%", null).getTotalElements()).isZero();
    }

    @Test
    void shouldPaginateWithTotals() {
        seedThreeStudents();

        Page<Student> firstPage = repository.search(StudentRepository.containsPattern(""), null,
                PageRequest.of(0, 2, BY_NAME_THEN_ID));

        assertThat(namesOf(firstPage)).containsExactly("Ana Souza", "Bruno Lima");
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    void shouldBreakTiesByIdForStableOrdering() {
        Student first = repository.save(newStudent("Ana Souza", "ana1@email.com", null));
        Student second = repository.save(newStudent("Ana Souza", "ana2@email.com", null));

        Page<Student> page = search("ana souza", null);

        assertThat(page.getContent()).extracting(Student::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    void shouldBuildEscapedContainsPattern() {
        assertThat(StudentRepository.containsPattern("  Ana_1%\\ ")).isEqualTo("%ana\\_1\\%\\\\%");
        assertThat(StudentRepository.containsPattern(null)).isEqualTo("%%");
    }

    // ---------- auxiliares ----------

    private Page<Student> search(String text, StudentStatus status) {
        return repository.search(StudentRepository.containsPattern(text), status,
                PageRequest.of(0, 20, BY_NAME_THEN_ID));
    }

    private void seedThreeStudents() {
        repository.save(newStudent("Ana Souza", "ana.souza@email.com", "11987654321"));
        repository.save(newStudent("Bruno Lima", "bruno.lima@fit.com", null));
        Student carla = newStudent("Carla Mendes", "carla.mendes@email.com", "2133334444");
        carla.changeStatus(StudentStatus.INACTIVE);
        repository.save(carla);
        repository.flush();
    }

    private static Student newStudent(String name, String email, String phone) {
        return new Student(name, email, phone, LocalDate.of(1998, 4, 15));
    }

    private static java.util.List<String> namesOf(Page<Student> page) {
        return page.getContent().stream().map(Student::getFullName).toList();
    }
}
