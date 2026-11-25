package com.fitmanager.api.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fitmanager.api.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Valida a migration V1 no PostgreSQL real (Testcontainers), sem entidades JPA:
 * histórico do Flyway, estrutura da tabela e constraints.
 * Cada teste roda em transação revertida ao final.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class FlywayMigrationTest {

    private static final String INSERT = """
            INSERT INTO students (full_name, email, phone, birth_date, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, now(), now())
            """;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void shouldRecordV1AsSuccessfullyApplied() {
        Map<String, Object> v1 = jdbc.queryForMap(
                "SELECT version, description, success FROM flyway_schema_history WHERE version = '1'");

        assertThat(v1.get("description")).isEqualTo("create students table");
        assertThat(v1.get("success")).isEqualTo(true);
    }

    @Test
    void shouldCreateStudentsTableWithExpectedColumns() {
        List<Map<String, Object>> columns = jdbc.queryForList("""
                SELECT column_name, data_type, is_nullable, character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'students'
                ORDER BY ordinal_position
                """);

        assertThat(columns).extracting(c -> c.get("column_name")).containsExactly(
                "id", "full_name", "email", "phone", "birth_date", "status", "created_at", "updated_at");
        assertThat(columns).extracting(c -> c.get("column_name") + ":" + c.get("is_nullable")).containsExactly(
                "id:NO", "full_name:NO", "email:NO", "phone:YES",
                "birth_date:NO", "status:NO", "created_at:NO", "updated_at:NO");
        assertThat(columns).extracting(c -> c.get("column_name") + ":" + c.get("data_type")).contains(
                "id:bigint", "birth_date:date",
                "created_at:timestamp with time zone", "updated_at:timestamp with time zone");
        assertThat(columns).extracting(c -> c.get("column_name") + ":" + c.get("character_maximum_length")).contains(
                "full_name:120", "email:160", "phone:11", "status:20");
    }

    @Test
    void shouldGenerateIdAutomatically() {
        insert("Ana Souza", "ana@email.com", "11987654321", "ACTIVE");

        Long id = jdbc.queryForObject("SELECT id FROM students WHERE email = 'ana@email.com'", Long.class);
        assertThat(id).isNotNull().isPositive();
    }

    @Test
    void shouldAcceptNullPhone() {
        insert("Bruno Lima", "bruno@email.com", null, "ACTIVE");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM students WHERE phone IS NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void shouldRejectDuplicateEmailWithNamedConstraint() {
        insert("Ana Souza", "ana@email.com", null, "ACTIVE");

        assertThatThrownBy(() -> insert("Outra Ana", "ana@email.com", null, "INACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_students_email");
    }

    @Test
    void shouldRejectUppercaseEmail() {
        assertThatThrownBy(() -> insert("Ana Souza", "Ana@Email.com", null, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_students_email_lowercase");
    }

    @Test
    void shouldRejectUnknownStatus() {
        assertThatThrownBy(() -> insert("Ana Souza", "ana@email.com", null, "DELETED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_students_status");
    }

    @Test
    void shouldRejectPhoneWithNonDigits() {
        // 11 caracteres: cabe no VARCHAR(11), então quem rejeita é o CHECK (e não o tamanho da coluna).
        assertThatThrownBy(() -> insert("Ana Souza", "ana@email.com", "11-98765432", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_students_phone_digits");
    }

    @Test
    void shouldRejectMissingRequiredField() {
        assertThatThrownBy(() -> insert(null, "ana@email.com", null, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("full_name");
    }

    @Test
    void shouldNotAllowManualId() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO students (id, full_name, email, birth_date, status, created_at, updated_at)
                VALUES (999, 'Ana Souza', 'ana@email.com', DATE '1998-04-15', 'ACTIVE', now(), now())
                """))
                .rootCause()
                .hasMessageContaining("cannot insert a non-DEFAULT value into column \"id\"");
    }

    private void insert(String fullName, String email, String phone, String status) {
        jdbc.update(INSERT, fullName, email, phone, LocalDate.of(1998, 4, 15), status);
    }
}
