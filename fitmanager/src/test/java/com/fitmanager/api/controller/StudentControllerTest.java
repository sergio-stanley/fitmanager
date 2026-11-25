package com.fitmanager.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fitmanager.api.dto.StudentRequest;
import com.fitmanager.api.dto.StudentResponse;
import com.fitmanager.api.entity.StudentStatus;
import com.fitmanager.api.exception.DuplicateResourceException;
import com.fitmanager.api.exception.ResourceNotFoundException;
import com.fitmanager.api.service.StudentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(StudentController.class)
class StudentControllerTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String DUPLICATE = "Já existe um aluno cadastrado com este e-mail.";
    private static final Instant CREATED = Instant.parse("2026-11-24T20:15:30.123456Z");

    private static final String VALID_BODY = """
            {"fullName": "  Ana   Souza ", "email": "Ana.Souza@Email.com", "phone": "11987654321", "birthDate": "1998-04-15"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentService service;

    // ---------- POST /api/students ----------

    @Test
    void shouldCreateWith201LocationAndContractBody() throws Exception {
        when(service.create(any())).thenReturn(ana(StudentStatus.ACTIVE));

        postJson("/api/students", VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/students/1"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fullName").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value("ana.souza@email.com"))
                .andExpect(jsonPath("$.phone").value("11987654321"))
                .andExpect(jsonPath("$.birthDate").value("1998-04-15"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-24T20:15:30.123456Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-11-24T20:15:30.123456Z"));
    }

    @Test
    void shouldPassNormalizedRequestToService() throws Exception {
        when(service.create(any())).thenReturn(ana(StudentStatus.ACTIVE));

        postJson("/api/students", VALID_BODY).andExpect(status().isCreated());

        ArgumentCaptor<StudentRequest> captor = ArgumentCaptor.forClass(StudentRequest.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new StudentRequest("Ana Souza", "ana.souza@email.com", "11987654321", LocalDate.of(1998, 4, 15)));
    }

    @Test
    void shouldSerializeMissingPhoneAsNull() throws Exception {
        StudentResponse noPhone = new StudentResponse(2L, "Bruno Lima", "bruno@email.com", null,
                LocalDate.of(1985, 9, 2), StudentStatus.ACTIVE, CREATED, CREATED);
        when(service.findById(2L)).thenReturn(noPhone);

        mockMvc.perform(get("/api/students/2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"phone\":null")))
                .andExpect(jsonPath("$.phone").value(nullValue()));
    }

    @Test
    void shouldReturn400WithContractMessagesAndNotCallService() throws Exception {
        postJson("/api/students", """
                {"fullName": "", "email": "invalido", "phone": "(11) 98765-4321", "birthDate": "2999-01-01"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.instance").value("/api/students"))
                .andExpect(jsonPath("$.errors[*].message").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "O nome é obrigatório",
                        "O nome deve ter entre 3 e 120 caracteres",
                        "O e-mail deve ser válido",
                        "O telefone deve conter 10 ou 11 dígitos, apenas números",
                        "A data de nascimento deve estar no passado")));
        verifyNoInteractions(service);
    }

    @Test
    void shouldReturn400WithoutErrorsForInvalidDateFormat() throws Exception {
        postJson("/api/students", """
                {"fullName": "Ana Souza", "email": "ana@email.com", "birthDate": "15/04/1998"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.errors").doesNotExist());
        verifyNoInteractions(service);
    }

    @Test
    void shouldReturn409WithFieldErrorAndWithoutExposingEmail() throws Exception {
        when(service.create(any())).thenThrow(new DuplicateResourceException("email", DUPLICATE));

        postJson("/api/students", VALID_BODY)
                .andExpect(status().isConflict())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Conflito"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(DUPLICATE))
                .andExpect(jsonPath("$.instance").value("/api/students"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value(DUPLICATE))
                .andExpect(content().string(not(containsString("ana.souza@email.com"))));
    }

    @Test
    void shouldReturn415ForNonJsonBody() throws Exception {
        mockMvc.perform(post("/api/students").contentType(MediaType.TEXT_PLAIN).content("Ana"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(PROBLEM_JSON));
    }

    // ---------- GET /api/students/{id} ----------

    @Test
    void shouldFindById() throws Exception {
        when(service.findById(1L)).thenReturn(ana(StudentStatus.ACTIVE));

        mockMvc.perform(get("/api/students/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ana Souza"));
    }

    @Test
    void shouldReturn404ForMissingStudent() throws Exception {
        when(service.findById(999L)).thenThrow(new ResourceNotFoundException("Aluno não encontrado."));

        mockMvc.perform(get("/api/students/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"))
                .andExpect(jsonPath("$.detail").value("Aluno não encontrado."))
                .andExpect(jsonPath("$.instance").value("/api/students/999"));
    }

    @Test
    void shouldReturn400ForNonNumericId() throws Exception {
        mockMvc.perform(get("/api/students/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON));
        verifyNoInteractions(service);
    }

    // ---------- GET /api/students ----------

    @Test
    void shouldListWithContractDefaultsAndPageFormat() throws Exception {
        when(service.search(isNull(), isNull(), any(Pageable.class))).thenAnswer(inv ->
                new PageImpl<>(List.of(ana(StudentStatus.ACTIVE)), inv.getArgument(2), 1));

        mockMvc.perform(get("/api/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].email").value("ana.souza@email.com"))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1));

        Pageable pageable = capturedPageable(null, null);
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "fullName"));
    }

    @Test
    void shouldPassSearchStatusAndSortToService() throws Exception {
        stubEmptySearch();

        mockMvc.perform(get("/api/students")
                        .param("search", "ana").param("status", "INACTIVE")
                        .param("page", "2").param("size", "5")
                        .param("sort", "email,desc").param("sort", "createdAt"))
                .andExpect(status().isOk());

        Pageable pageable = capturedPageable("ana", StudentStatus.INACTIVE);
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("email"), Sort.Order.asc("createdAt")));
    }

    @Test
    void shouldCapSizeAt100() throws Exception {
        stubEmptySearch();

        mockMvc.perform(get("/api/students").param("size", "500")).andExpect(status().isOk());

        assertThat(capturedPageable(null, null).getPageSize()).isEqualTo(100);
    }

    @Test
    void shouldApplySpringDefaultsForOutOfRangeOrInvalidPageAndSize() throws Exception {
        // Comportamento marcado como "a confirmar" no contrato (seção 2.6).
        stubEmptySearch();

        mockMvc.perform(get("/api/students").param("page", "-1").param("size", "0")).andExpect(status().isOk());
        Pageable negative = capturedPageable(null, null);
        assertThat(negative.getPageNumber()).isZero();
        assertThat(negative.getPageSize()).isEqualTo(20);
    }

    @Test
    void shouldApplySpringDefaultsForNonNumericPageAndSize() throws Exception {
        stubEmptySearch();

        mockMvc.perform(get("/api/students").param("page", "x").param("size", "y")).andExpect(status().isOk());
        Pageable nonNumeric = capturedPageable(null, null);
        assertThat(nonNumeric.getPageNumber()).isZero();
        assertThat(nonNumeric.getPageSize()).isEqualTo(20);
    }

    @Test
    void shouldRejectSortFieldOutsideAllowList() throws Exception {
        for (String sort : List.of("password", "id", "phone", "fullName,up", "fullName,asc,ignorecase")) {
            mockMvc.perform(get("/api/students").param("sort", sort))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(PROBLEM_JSON))
                    .andExpect(jsonPath("$.instance").value("/api/students"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectUnknownStatusFilter() throws Exception {
        mockMvc.perform(get("/api/students").param("status", "DELETED"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON));
        verifyNoInteractions(service);
    }

    @Test
    void shouldLimitSearchTo100Characters() throws Exception {
        mockMvc.perform(get("/api/students").param("search", "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON));
        verifyNoInteractions(service);

        stubEmptySearch();
        mockMvc.perform(get("/api/students").param("search", "a".repeat(100))).andExpect(status().isOk());
    }

    // ---------- PUT /api/students/{id} ----------

    @Test
    void shouldUpdate() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(ana(StudentStatus.ACTIVE));

        putJson("/api/students/1", VALID_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturn400OnInvalidUpdate() throws Exception {
        putJson("/api/students/1", """
                {"fullName": "Ana Souza", "email": "ana@email.com"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("birthDate"))
                .andExpect(jsonPath("$.errors[0].message").value("A data de nascimento é obrigatória"));
        verifyNoInteractions(service);
    }

    @Test
    void shouldReturn404OnUpdateOfMissingStudent() throws Exception {
        when(service.update(eq(999L), any())).thenThrow(new ResourceNotFoundException("Aluno não encontrado."));

        putJson("/api/students/999", VALID_BODY).andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn409OnUpdateWithEmailOfAnotherStudent() throws Exception {
        when(service.update(eq(1L), any())).thenThrow(new DuplicateResourceException("email", DUPLICATE));

        putJson("/api/students/1", VALID_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    // ---------- PATCH /api/students/{id}/status ----------

    @Test
    void shouldChangeStatus() throws Exception {
        when(service.changeStatus(1L, StudentStatus.INACTIVE)).thenReturn(ana(StudentStatus.INACTIVE));

        patchJson("/api/students/1/status", "{\"status\": \"INACTIVE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void shouldRequireStatus() throws Exception {
        patchJson("/api/students/1/status", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message").value("O status é obrigatório"));
        verifyNoInteractions(service);
    }

    @Test
    void shouldReturn400WithoutErrorsForUnknownStatusValue() throws Exception {
        patchJson("/api/students/1/status", "{\"status\": \"DELETED\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").doesNotExist());
        verifyNoInteractions(service);
    }

    @Test
    void shouldReturn404OnStatusChangeOfMissingStudent() throws Exception {
        when(service.changeStatus(999L, StudentStatus.ACTIVE))
                .thenThrow(new ResourceNotFoundException("Aluno não encontrado."));

        patchJson("/api/students/999/status", "{\"status\": \"ACTIVE\"}").andExpect(status().isNotFound());
    }

    // ---------- método não suportado ----------

    @Test
    void shouldReturn405ForDelete() throws Exception {
        mockMvc.perform(delete("/api/students/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(PROBLEM_JSON));
        verifyNoInteractions(service);
    }

    // ---------- CORS ----------

    @Test
    void shouldAllowPreflightFromFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/students")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PATCH")));
    }

    @Test
    void shouldRejectPreflightFromUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/students")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void shouldExposeLocationHeaderToFrontend() throws Exception {
        when(service.create(any())).thenReturn(ana(StudentStatus.ACTIVE));

        mockMvc.perform(post("/api/students")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Location")));
    }

    // ---------- auxiliares ----------

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions putJson(String url, String body) throws Exception {
        return mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions patchJson(String url, String body) throws Exception {
        return mockMvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void stubEmptySearch() {
        when(service.search(any(), any(), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(), (Pageable) inv.getArgument(2), 0));
    }

    private Pageable capturedPageable(String search, StudentStatus status) {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).search(eq(search), eq(status), captor.capture());
        return captor.getValue();
    }

    private static StudentResponse ana(StudentStatus status) {
        return new StudentResponse(1L, "Ana Souza", "ana.souza@email.com", "11987654321",
                LocalDate.of(1998, 4, 15), status, CREATED, CREATED);
    }
}
