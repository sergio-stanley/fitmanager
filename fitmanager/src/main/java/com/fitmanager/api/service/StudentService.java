package com.fitmanager.api.service;

import com.fitmanager.api.dto.StudentRequest;
import com.fitmanager.api.dto.StudentResponse;
import com.fitmanager.api.entity.Student;
import com.fitmanager.api.entity.StudentStatus;
import com.fitmanager.api.exception.DuplicateResourceException;
import com.fitmanager.api.exception.ResourceNotFoundException;
import com.fitmanager.api.repository.StudentRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio de alunos (API_CONTRACT.md, seção 2).
 * Leitura por padrão (readOnly); métodos de escrita sobrescrevem com @Transactional.
 */
@Service
@Transactional(readOnly = true)
public class StudentService {

    static final String EMAIL_UNIQUE_CONSTRAINT = "uk_students_email";
    static final String DUPLICATE_EMAIL_MESSAGE = "Já existe um aluno cadastrado com este e-mail.";
    static final String NOT_FOUND_MESSAGE = "Aluno não encontrado.";

    private final StudentRepository repository;

    public StudentService(StudentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public StudentResponse create(StudentRequest request) {
        if (repository.existsByEmail(request.email())) {
            throw duplicateEmail(null);
        }
        Student student = new Student(request.fullName(), request.email(), request.phone(), request.birthDate());
        return StudentResponse.from(saveAndFlush(student));
    }

    public StudentResponse findById(Long id) {
        return StudentResponse.from(findStudent(id));
    }

    /** {@code search} vazio = todos; {@code status} nulo = todos. Sempre desempata por id. */
    public Page<StudentResponse> search(String search, StudentStatus status, Pageable pageable) {
        return repository.search(StudentRepository.containsPattern(search), status, withIdTieBreaker(pageable))
                .map(StudentResponse::from);
    }

    @Transactional
    public StudentResponse update(Long id, StudentRequest request) {
        Student student = findStudent(id);
        // "AndIdNot": o aluno pode manter o próprio e-mail.
        if (repository.existsByEmailAndIdNot(request.email(), id)) {
            throw duplicateEmail(null);
        }
        student.updateData(request.fullName(), request.email(), request.phone(), request.birthDate());
        return StudentResponse.from(saveAndFlush(student));
    }

    /** Desativa ou reativa. Idempotente: repetir o status atual não altera nada (nem updatedAt). */
    @Transactional
    public StudentResponse changeStatus(Long id, StudentStatus status) {
        Student student = findStudent(id);
        student.changeStatus(status);
        return StudentResponse.from(saveAndFlush(student));
    }

    private Student findStudent(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }

    /**
     * O flush executa o SQL agora, dentro do método: (1) o @PreUpdate atualiza updatedAt antes de
     * montarmos a resposta; (2) uma violação de unicidade aparece aqui, onde pode ser traduzida,
     * e não no commit, depois que o método já retornou.
     */
    private Student saveAndFlush(Student student) {
        try {
            return repository.saveAndFlush(student);
        } catch (DataIntegrityViolationException e) {
            // Corrida: outro cadastro com o mesmo e-mail entre a verificação e o INSERT/UPDATE.
            if (isEmailUniqueViolation(e)) {
                throw duplicateEmail(e);
            }
            throw e; // qualquer outra violação de integridade NÃO é duplicidade de e-mail
        }
    }

    static boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }

    private static DuplicateResourceException duplicateEmail(Throwable cause) {
        return new DuplicateResourceException("email", DUPLICATE_EMAIL_MESSAGE, cause);
    }

    /** Ordenação determinística na paginação: acrescenta id ASC se ainda não estiver na ordenação. */
    static Pageable withIdTieBreaker(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getSort().getOrderFor("id") != null) {
            return pageable;
        }
        Sort sort = pageable.getSort().and(Sort.by(Sort.Direction.ASC, "id"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
