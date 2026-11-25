package com.fitmanager.api.repository;

import java.util.Locale;

import com.fitmanager.api.entity.Student;
import com.fitmanager.api.entity.StudentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long> {

    /** O e-mail deve estar normalizado (minúsculas), como é gravado. */
    boolean existsByEmail(String email);

    /** Para edição: verifica se o e-mail pertence a OUTRO aluno. */
    boolean existsByEmailAndIdNot(String email, Long id);

    /**
     * Busca por trecho de nome OU e-mail (sem diferenciar maiúsculas), com filtro opcional de status.
     * {@code pattern} deve vir de {@link #containsPattern(String)}; {@code status} nulo = todos.
     */
    @Query("""
            select s from Student s
            where (:status is null or s.status = :status)
              and (lower(s.fullName) like :pattern escape '\\' or s.email like :pattern escape '\\')
            """)
    Page<Student> search(@Param("pattern") String pattern,
                         @Param("status") StudentStatus status,
                         Pageable pageable);

    /**
     * Monta o padrão LIKE "contém" a partir do texto digitado: remove espaços nas pontas,
     * converte para minúsculas e escapa os curingas do LIKE (% e _), para que sejam buscados
     * literalmente. Texto nulo ou vazio resulta em "%%", que corresponde a todos.
     */
    static String containsPattern(String text) {
        String normalized = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        String escaped = normalized
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
