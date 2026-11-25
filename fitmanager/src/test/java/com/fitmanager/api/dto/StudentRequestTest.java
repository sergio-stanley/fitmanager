package com.fitmanager.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Normalização e mensagens de validação exatamente como no API_CONTRACT.md (seção 2.2). */
class StudentRequestTest {

    private static final LocalDate BIRTH = LocalDate.of(1998, 4, 15);

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    // ---------- normalização ----------

    @Test
    void shouldNormalizeNameEmailAndPhone() {
        StudentRequest request = new StudentRequest("  Ana   Souza ", "  Ana.Souza@Email.COM ", " 11987654321 ", BIRTH);

        assertThat(request.fullName()).isEqualTo("Ana Souza");
        assertThat(request.email()).isEqualTo("ana.souza@email.com");
        assertThat(request.phone()).isEqualTo("11987654321");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldTreatMissingEmptyOrBlankPhoneAsNull() {
        assertThat(new StudentRequest("Ana Souza", "ana@email.com", null, BIRTH).phone()).isNull();
        assertThat(new StudentRequest("Ana Souza", "ana@email.com", "", BIRTH).phone()).isNull();
        assertThat(new StudentRequest("Ana Souza", "ana@email.com", "   ", BIRTH).phone()).isNull();
        assertThat(validator.validate(new StudentRequest("Ana Souza", "ana@email.com", "   ", BIRTH))).isEmpty();
    }

    @Test
    void shouldValidateNormalizedValueNotRawValue() {
        // "  ab  " tem 6 caracteres brutos, mas apenas 2 depois da normalização: deve ser rejeitado.
        assertThat(messages(new StudentRequest("  ab  ", "ana@email.com", null, BIRTH)))
                .containsExactly("O nome deve ter entre 3 e 120 caracteres");
    }

    // ---------- mensagens do contrato ----------

    @Test
    void shouldReportBothNameMessagesWhenNameIsBlank() {
        assertThat(messages(new StudentRequest("   ", "ana@email.com", null, BIRTH)))
                .containsExactlyInAnyOrder("O nome é obrigatório", "O nome deve ter entre 3 e 120 caracteres");
    }

    @Test
    void shouldRejectNameLongerThan120() {
        assertThat(messages(new StudentRequest("a".repeat(121), "ana@email.com", null, BIRTH)))
                .containsExactly("O nome deve ter entre 3 e 120 caracteres");
    }

    @Test
    void shouldRequireValidEmail() {
        assertThat(messages(new StudentRequest("Ana Souza", null, null, BIRTH)))
                .containsExactly("O e-mail é obrigatório");
        assertThat(messages(new StudentRequest("Ana Souza", "email-invalido", null, BIRTH)))
                .containsExactly("O e-mail deve ser válido");
    }

    @Test
    void shouldRejectEmailLongerThan160() {
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(40); // 169 caracteres, formato válido
        assertThat(messages(new StudentRequest("Ana Souza", email, null, BIRTH)))
                .containsExactly("O e-mail deve ter no máximo 160 caracteres");
    }

    @Test
    void shouldRejectPhoneWithMaskOrWrongLength() {
        String expected = "O telefone deve conter 10 ou 11 dígitos, apenas números";
        assertThat(messages(new StudentRequest("Ana Souza", "ana@email.com", "(11) 98765-4321", BIRTH)))
                .containsExactly(expected);
        assertThat(messages(new StudentRequest("Ana Souza", "ana@email.com", "123456789", BIRTH)))
                .containsExactly(expected);
        assertThat(messages(new StudentRequest("Ana Souza", "ana@email.com", "1234567890", BIRTH))).isEmpty();
    }

    @Test
    void shouldRequireBirthDateInThePast() {
        assertThat(messages(new StudentRequest("Ana Souza", "ana@email.com", null, null)))
                .containsExactly("A data de nascimento é obrigatória");
        assertThat(messages(new StudentRequest("Ana Souza", "ana@email.com", null, LocalDate.now())))
                .containsExactly("A data de nascimento deve estar no passado");
    }

    @Test
    void shouldRequireStatusInStatusRequest() {
        List<String> messages = validator.validate(new StudentStatusRequest(null)).stream()
                .map(ConstraintViolation::getMessage).toList();
        assertThat(messages).containsExactly("O status é obrigatório");
    }

    private List<String> messages(StudentRequest request) {
        return validator.validate(request).stream().map(ConstraintViolation::getMessage).toList();
    }
}
