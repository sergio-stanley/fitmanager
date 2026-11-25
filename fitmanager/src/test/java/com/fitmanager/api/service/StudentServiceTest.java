package com.fitmanager.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.fitmanager.api.dto.StudentRequest;
import com.fitmanager.api.dto.StudentResponse;
import com.fitmanager.api.entity.Student;
import com.fitmanager.api.entity.StudentStatus;
import com.fitmanager.api.exception.DuplicateResourceException;
import com.fitmanager.api.exception.ResourceNotFoundException;
import com.fitmanager.api.repository.StudentRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    private static final LocalDate BIRTH = LocalDate.of(1998, 4, 15);

    @Mock
    private StudentRepository repository;

    @InjectMocks
    private StudentService service;

    // ---------- cadastro ----------

    @Test
    void shouldCreateStudentAsActive() {
        when(repository.existsByEmail("ana@email.com")).thenReturn(false);
        when(repository.saveAndFlush(any(Student.class))).thenAnswer(inv -> withId(inv.getArgument(0), 1L));

        StudentResponse response = service.create(request("Ana Souza", "ana@email.com"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.fullName()).isEqualTo("Ana Souza");
        assertThat(response.email()).isEqualTo("ana@email.com");
        assertThat(response.status()).isEqualTo(StudentStatus.ACTIVE);
    }

    @Test
    void shouldRejectDuplicateEmailOnCreateWithoutExposingIt() {
        when(repository.existsByEmail("ana@email.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("Ana Souza", "ana@email.com")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Já existe um aluno cadastrado com este e-mail.")
                .hasMessageNotContaining("ana@email.com")
                .extracting("field").isEqualTo("email");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldTranslateConcurrentDuplicateDetectedByUniqueConstraint() {
        when(repository.existsByEmail("ana@email.com")).thenReturn(false); // a outra requisição ainda não gravou
        DataIntegrityViolationException dbError = integrityViolation("uk_students_email");
        when(repository.saveAndFlush(any(Student.class))).thenThrow(dbError);

        assertThatThrownBy(() -> service.create(request("Ana Souza", "ana@email.com")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Já existe um aluno cadastrado com este e-mail.")
                .hasCause(dbError);
    }

    @Test
    void shouldNotTranslateOtherIntegrityViolations() {
        when(repository.existsByEmail("ana@email.com")).thenReturn(false);
        DataIntegrityViolationException dbError = integrityViolation("ck_students_phone_digits");
        when(repository.saveAndFlush(any(Student.class))).thenThrow(dbError);

        assertThatThrownBy(() -> service.create(request("Ana Souza", "ana@email.com"))).isSameAs(dbError);
    }

    @Test
    void shouldNotTranslateIntegrityViolationWithoutConstraintName() {
        when(repository.existsByEmail("ana@email.com")).thenReturn(false);
        DataIntegrityViolationException dbError = new DataIntegrityViolationException("sem causa do Hibernate");
        when(repository.saveAndFlush(any(Student.class))).thenThrow(dbError);

        assertThatThrownBy(() -> service.create(request("Ana Souza", "ana@email.com"))).isSameAs(dbError);
    }

    // ---------- consulta ----------

    @Test
    void shouldFindById() {
        when(repository.findById(1L)).thenReturn(Optional.of(student(1L, "Ana Souza", "ana@email.com")));

        assertThat(service.findById(1L).fullName()).isEqualTo("Ana Souza");
    }

    @Test
    void shouldThrowNotFoundWithGenericMessage() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Aluno não encontrado.");
    }

    // ---------- listagem ----------

    @Test
    void shouldSearchWithEscapedPatternAndIdTieBreaker() {
        Page<Student> page = new PageImpl<>(List.of(student(1L, "Ana Souza", "ana@email.com")));
        when(repository.search(eq("%ana\\_%"), eq(StudentStatus.ACTIVE), any(Pageable.class))).thenReturn(page);

        Page<StudentResponse> result = service.search(" Ana_ ", StudentStatus.ACTIVE,
                PageRequest.of(0, 20, Sort.by("fullName")));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(eq("%ana\\_%"), eq(StudentStatus.ACTIVE), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("fullName").and(Sort.by("id")));
        assertThat(result.getContent()).extracting(StudentResponse::fullName).containsExactly("Ana Souza");
    }

    @Test
    void shouldAddIdTieBreakerOnlyWhenMissing() {
        Pageable unsorted = PageRequest.of(2, 10);
        Pageable alreadyById = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"));

        assertThat(StudentService.withIdTieBreaker(unsorted).getSort()).isEqualTo(Sort.by("id"));
        assertThat(StudentService.withIdTieBreaker(unsorted).getPageNumber()).isEqualTo(2);
        assertThat(StudentService.withIdTieBreaker(alreadyById)).isSameAs(alreadyById);
    }

    // ---------- edição ----------

    @Test
    void shouldUpdateKeepingOwnEmail() {
        Student ana = student(1L, "Ana Souza", "ana@email.com");
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        when(repository.existsByEmailAndIdNot("ana@email.com", 1L)).thenReturn(false);
        when(repository.saveAndFlush(ana)).thenReturn(ana);

        StudentResponse response = service.update(1L,
                new StudentRequest("Ana Souza Lima", "ana@email.com", "11987654321", BIRTH));

        assertThat(response.fullName()).isEqualTo("Ana Souza Lima");
        assertThat(response.phone()).isEqualTo("11987654321");
        verify(repository).saveAndFlush(ana); // flush antes de montar a resposta
    }

    @Test
    void shouldRemovePhoneWhenOmittedOnUpdate() {
        Student ana = new Student("Ana Souza", "ana@email.com", "11987654321", BIRTH);
        withId(ana, 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        when(repository.existsByEmailAndIdNot("ana@email.com", 1L)).thenReturn(false);
        when(repository.saveAndFlush(ana)).thenReturn(ana);

        assertThat(service.update(1L, new StudentRequest("Ana Souza", "ana@email.com", "", BIRTH)).phone()).isNull();
    }

    @Test
    void shouldRejectEmailOfAnotherStudentOnUpdateWithoutChangingEntity() {
        Student ana = student(1L, "Ana Souza", "ana@email.com");
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        when(repository.existsByEmailAndIdNot("bruno@email.com", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, request("Ana Souza", "bruno@email.com")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageNotContaining("bruno@email.com");
        assertThat(ana.getEmail()).isEqualTo("ana@email.com");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldTranslateConcurrentDuplicateOnUpdate() {
        Student ana = student(1L, "Ana Souza", "ana@email.com");
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        when(repository.existsByEmailAndIdNot("bruno@email.com", 1L)).thenReturn(false);
        when(repository.saveAndFlush(ana)).thenThrow(integrityViolation("uk_students_email"));

        assertThatThrownBy(() -> service.update(1L, request("Ana Souza", "bruno@email.com")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void shouldThrowNotFoundOnUpdateOfMissingStudent() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, request("Ana Souza", "ana@email.com")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).existsByEmailAndIdNot(any(), any());
    }

    // ---------- status ----------

    @Test
    void shouldDeactivateStudent() {
        Student ana = student(1L, "Ana Souza", "ana@email.com");
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        when(repository.saveAndFlush(ana)).thenReturn(ana);

        assertThat(service.changeStatus(1L, StudentStatus.INACTIVE).status()).isEqualTo(StudentStatus.INACTIVE);
    }

    @Test
    void shouldReactivateStudent() {
        Student ana = student(1L, "Ana Souza", "ana@email.com");
        ana.changeStatus(StudentStatus.INACTIVE);
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        when(repository.saveAndFlush(ana)).thenReturn(ana);

        assertThat(service.changeStatus(1L, StudentStatus.ACTIVE).status()).isEqualTo(StudentStatus.ACTIVE);
    }

    @Test
    void shouldAcceptSameStatusIdempotently() {
        Student ana = student(1L, "Ana Souza", "ana@email.com");
        when(repository.findById(1L)).thenReturn(Optional.of(ana));
        when(repository.saveAndFlush(ana)).thenReturn(ana);

        assertThat(service.changeStatus(1L, StudentStatus.ACTIVE).status()).isEqualTo(StudentStatus.ACTIVE);
    }

    @Test
    void shouldThrowNotFoundOnStatusChangeOfMissingStudent() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(999L, StudentStatus.INACTIVE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- auxiliares ----------

    private static StudentRequest request(String name, String email) {
        return new StudentRequest(name, email, null, BIRTH);
    }

    private static Student student(Long id, String name, String email) {
        return withId(new Student(name, email, null, BIRTH), id);
    }

    /** O id é gerado pelo banco; no teste unitário ele é atribuído por reflexão. */
    private static Student withId(Student student, Long id) {
        ReflectionTestUtils.setField(student, "id", id);
        return student;
    }

    private static DataIntegrityViolationException integrityViolation(String constraintName) {
        ConstraintViolationException hibernate = new ConstraintViolationException(
                "violação simulada", new SQLException("simulada", "23505"), constraintName);
        return new DataIntegrityViolationException("could not execute statement", hibernate);
    }
}
