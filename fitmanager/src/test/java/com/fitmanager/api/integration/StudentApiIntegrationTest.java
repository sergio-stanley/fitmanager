package com.fitmanager.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

import com.fitmanager.api.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Ponta a ponta: HTTP → controller → service → repository → PostgreSQL real (Testcontainers).
 * Nenhum mock. Sem @Transactional no teste: cada requisição faz commit de verdade, como em produção.
 * O banco é o container descartável do teste (nunca o fitmanager-db); ele é esvaziado antes de cada teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StudentApiIntegrationTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String DUPLICATE = "Já existe um aluno cadastrado com este e-mail.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTestDatabase() {
        jdbc.update("DELETE FROM students");
    }

    // ---------- fluxo completo ----------

    @Test
    void shouldRunFullStudentLifecycle() throws Exception {
        // 1. Cadastro: 201, Location, dados normalizados, ACTIVE, createdAt == updatedAt
        MvcResult created = postJson("/api/students", """
                {"fullName": "  Ana   Souza ", "email": " Ana.Souza@Email.com ", "phone": "11987654321", "birthDate": "1998-04-15"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value("ana.souza@email.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        long id = idOf(created);
        String location = created.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isEqualTo("/api/students/" + id);
        Instant createdAt = instant(created, "$.createdAt");
        assertThat(instant(created, "$.updatedAt")).isEqualTo(createdAt);

        // 2. Persistência real: o que a API respondeu é o que está gravado
        Map<String, Object> row = row(id);
        assertThat(row.get("full_name")).isEqualTo("Ana Souza");
        assertThat(row.get("email")).isEqualTo("ana.souza@email.com");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(dbInstant(id, "created_at")).isEqualTo(createdAt);
        assertThat(dbInstant(id, "updated_at")).isEqualTo(createdAt);

        // 3. Consulta pelo Location
        MvcResult fetched = mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andReturn();
        // Compara instantes, não strings: o número de casas decimais pode variar (contrato, seção 1).
        assertThat(instant(fetched, "$.createdAt")).isEqualTo(createdAt);

        // 4. Edição: mantém o próprio e-mail, remove o telefone (omitido), preserva createdAt
        Thread.sleep(5);
        MvcResult updated = putJson(location, """
                {"fullName": "Ana Souza Lima", "email": "ana.souza@email.com", "birthDate": "1998-04-15"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ana Souza Lima"))
                .andExpect(content().string(containsString("\"phone\":null"))) // presente, com valor null
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        Instant updatedAt = instant(updated, "$.updatedAt");
        assertThat(instant(updated, "$.createdAt")).isEqualTo(createdAt);
        assertThat(updatedAt).isAfter(createdAt);
        assertThat(dbInstant(id, "updated_at")).isEqualTo(updatedAt);
        assertThat(dbInstant(id, "created_at")).isEqualTo(createdAt);
        assertThat(row(id).get("phone")).isNull();

        // 5. Desativação
        Thread.sleep(5);
        MvcResult deactivated = patchStatus(id, "INACTIVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andReturn();
        Instant deactivatedAt = instant(deactivated, "$.updatedAt");
        assertThat(deactivatedAt).isAfter(updatedAt);
        assertThat(row(id).get("status")).isEqualTo("INACTIVE");

        // 6. Idempotência: repetir INACTIVE não altera updatedAt
        Thread.sleep(5);
        MvcResult repeated = patchStatus(id, "INACTIVE").andExpect(status().isOk()).andReturn();
        assertThat(instant(repeated, "$.updatedAt")).isEqualTo(deactivatedAt);
        assertThat(dbInstant(id, "updated_at")).isEqualTo(deactivatedAt);

        // 7. Aluno inativo continua consultável
        mockMvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));

        // 8. Reativação
        patchStatus(id, "ACTIVE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(row(id).get("status")).isEqualTo("ACTIVE");
        assertThat(dbInstant(id, "created_at")).isEqualTo(createdAt);
    }

    // ---------- unicidade do e-mail ----------

    @Test
    void shouldRejectDuplicateEmailIgnoringCaseAndKeepSingleRow() throws Exception {
        create("Ana Souza", "ana.souza@email.com");

        postJson("/api/students", body("Outra Ana", "ANA.SOUZA@EMAIL.COM"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Conflito"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(DUPLICATE))
                .andExpect(jsonPath("$.instance").value("/api/students"))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(content().string(not(containsString("ana.souza@email.com"))));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM students", Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldRejectEmailOfAnotherStudentOnUpdateButAllowOwn() throws Exception {
        long ana = create("Ana Souza", "ana@email.com");
        create("Bruno Lima", "bruno@email.com");

        putJson("/api/students/" + ana, body("Ana Souza", "bruno@email.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
        assertThat(row(ana).get("email")).isEqualTo("ana@email.com"); // nada foi alterado

        putJson("/api/students/" + ana, body("Ana S.", "ana@email.com")).andExpect(status().isOk());
    }

    // ---------- listagem: filtros e paginação ----------

    @Test
    void shouldFilterSearchAndPaginateWithStableOrder() throws Exception {
        create("Carla Mendes", "carla@email.com");
        long anaA = create("Ana Souza", "ana.a@email.com");
        long anaB = create("Ana Souza", "ana.b@email.com"); // homônima: desempate por id
        long bruno = create("Bruno Lima", "bruno@fit.com");
        patchStatus(bruno, "INACTIVE").andExpect(status().isOk());

        // Padrão: fullName ASC + desempate por id; página com metadados
        mockMvc.perform(get("/api/students").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains((int) anaA, (int) anaB)))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.page.totalPages").value(2));

        mockMvc.perform(get("/api/students").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.content[*].fullName", contains("Bruno Lima", "Carla Mendes")));

        // Página além da última: 200 com content vazio
        mockMvc.perform(get("/api/students").param("size", "2").param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        // Busca por nome (sem diferenciar maiúsculas) e por trecho de e-mail
        mockMvc.perform(get("/api/students").param("search", "  ANA "))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mockMvc.perform(get("/api/students").param("search", "fit.com"))
                .andExpect(jsonPath("$.content[0].fullName").value("Bruno Lima"));

        // Filtro por status e combinação com busca
        mockMvc.perform(get("/api/students").param("status", "INACTIVE"))
                .andExpect(jsonPath("$.content[*].fullName", contains("Bruno Lima")));
        mockMvc.perform(get("/api/students").param("search", "email.com").param("status", "ACTIVE"))
                .andExpect(jsonPath("$.page.totalElements").value(3));

        // Curingas do LIKE são literais
        mockMvc.perform(get("/api/students").param("search", "%"))
                .andExpect(jsonPath("$.page.totalElements").value(0));

        // Ordenação permitida, decrescente
        mockMvc.perform(get("/api/students").param("sort", "email,desc"))
                .andExpect(jsonPath("$.content[0].email").value("carla@email.com"));
    }

    // ---------- erros do contrato ----------

    @Test
    void shouldReturn400WithFieldErrorsAndPersistNothing() throws Exception {
        postJson("/api/students", """
                {"fullName": "  ab ", "email": "ana@email.com", "phone": "   ", "birthDate": "2999-01-01"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.errors[*].message").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "O nome deve ter entre 3 e 120 caracteres",
                        "A data de nascimento deve estar no passado")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM students", Integer.class)).isZero();
    }

    @Test
    void shouldReturn400ForInvalidSortAndParameters() throws Exception {
        mockMvc.perform(get("/api/students").param("sort", "phone"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.instance").value("/api/students"));
        mockMvc.perform(get("/api/students").param("status", "DELETED")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/students").param("search", "a".repeat(101))).andExpect(status().isBadRequest());
        patchStatus(1, "DELETED").andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404ForMissingStudentOnEveryOperation() throws Exception {
        mockMvc.perform(get("/api/students/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"))
                .andExpect(jsonPath("$.detail").value("Aluno não encontrado."))
                .andExpect(jsonPath("$.instance").value("/api/students/999999"));
        putJson("/api/students/999999", body("Ana Souza", "ana@email.com")).andExpect(status().isNotFound());
        patchStatus(999999, "INACTIVE").andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM students", Integer.class)).isZero();
    }

    // ---------- auxiliares ----------

    private long create(String name, String email) throws Exception {
        return idOf(postJson("/api/students", body(name, email)).andExpect(status().isCreated()).andReturn());
    }

    private static String body(String name, String email) {
        return """
                {"fullName": "%s", "email": "%s", "birthDate": "1998-04-15"}
                """.formatted(name, email);
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions putJson(String url, String json) throws Exception {
        return mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions patchStatus(long id, String status) throws Exception {
        return mockMvc.perform(patch("/api/students/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"" + status + "\"}"));
    }

    private static long idOf(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static Instant instant(MvcResult result, String path) throws Exception {
        return Instant.parse(JsonPath.read(result.getResponse().getContentAsString(), path));
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT full_name, email, phone, status FROM students WHERE id = ?", id);
    }

    private Instant dbInstant(long id, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM students WHERE id = ?", OffsetDateTime.class, id)
                .toInstant();
    }
}
