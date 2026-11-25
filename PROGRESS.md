# PROGRESS.md — Andamento do FitManager

Última atualização: 2026-11-24

## Resumo
| Etapa | Status |
|---|---|
| 0 — Documentação inicial | ✅ Concluída |
| 1 — Ambiente e banco | ✅ Concluída (commit `03787bb`, no GitHub) |
| 2 — Base da API | ✅ Concluída — PR #1 integrado na `main` (merge `024d9e3`) |
| 3 — Alunos (backend) | ✅ Concluída — contrato no PR #2; implementação (Passos 2 a 6, 105 testes, validação manual no Swagger) commitada em `feat/students-backend`, **aguardando push e PR** |

## Repositório
- GitHub (público): https://github.com/sergio-stanley/fitmanager (`origin`).
- `main` = `origin/main`: `d44dbf8` — *Merge pull request #2 from sergio-stanley/docs/students-api-contract* (contém `a078044`, contrato v1.0.0; `024d9e3`, merge do PR #1 / Etapa 2; `03787bb`, Etapa 1).
- `feat/api-base` (`1d146e8`) e `docs/students-api-contract` (`a078044`): integradas; mantidas local e remotamente até decisão do desenvolvedor.
- `feat/students-backend`: atualizada por fast-forward para `d44dbf8`; branch do backend da Etapa 3 (worktree principal).
- `feat/frontend-base`: criada pelo desenvolvedor via Orca, em worktree separada (`C:\Users\sstan\orca\workspaces\fitmanager\feat-frontend-base`). Tem commit próprio de scaffold do frontend (`chore(frontend): scaffold React TypeScript and Vite...`, só em `frontend/`) a partir de `024d9e3` — o hash é gerenciado pelo agente frontend e pode mudar; ainda não contém o contrato (precisa de merge/rebase da `main`, a critério do desenvolvedor).

## Desenvolvimento em paralelo (dois agentes)
- **Agente backend:** worktree principal, branches `feat/students-backend` e de documentação. É o **único** que edita os arquivos da raiz: `PROGRESS.md`, `PROJECT.md`, `README.md`, `CLAUDE.md` e `API_CONTRACT.md`.
- **Agente frontend:** worktree do Orca, pasta `frontend/`. Documenta apenas dentro de `frontend/`. O agente backend não modifica a pasta `frontend/` nem a worktree do Orca.
- **Integração:** pelo [API_CONTRACT.md](API_CONTRACT.md). O contrato é publicado na `main` antes da implementação, para o frontend trabalhar com mocks. Mudanças no contrato só por PR, com nova versão.
- O `.env` não é versionado: cada worktree que precisar subir o backend deve ter sua própria cópia local.

## Ambiente
- JDK `openjdk 21.0.13.1 LTS`, Docker 29.9.3, Docker Compose v5.7.1.
- Container `fitmanager-db` (`postgres:16-alpine`, PostgreSQL 16.16) via `docker-compose.yml`.
- Instruções de execução e testes: ver [README.md](README.md).

## Etapa 1 — Ambiente e banco (concluída)
**Criado:** `docker-compose.yml` (porta só em `127.0.0.1`, volume `fitmanager-data`, healthcheck), `.env.example`, `.gitignore` da raiz (ignora `.env`), `application.properties` (datasource via `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; `ddl-auto=none`; `open-in-view=false`), `application-dev.properties` (perfil padrão `dev`, log de SQL).

**Resultados confirmados:**
- `fitmanager-db` com status `healthy`.
- `spring-boot:run` (executado pelo desenvolvedor): HikariPool conectou, PostgreSQL 16.16, Tomcat na porta 8080, `Started FitmanagerApplication in 3.416 seconds`.
- `mvnw test`: `Tests run: 1, Failures: 0` (na época, dependia do `.env`; resolvido na Etapa 2).

**Conceitos para entrevista:** externalização de configuração (12-factor); Docker Compose, volumes e healthcheck; `ddl-auto=none` + migrations; `open-in-view=false`; perfis do Spring; HikariCP.

## Etapa 2 — Base da API (concluída — PR #1, merge `024d9e3`)
**Passo 1 — Branch:** `feat/api-base` criada com `git switch -c`.

**Passo 2 — Swagger/OpenAPI**
- `springdoc-openapi-starter-webmvc-ui` 3.2.1 (fora do BOM; POM compilado contra Spring Boot 4.2.0) + `config/OpenApiConfig` (bean `OpenAPI`: título, versão, descrição).
- `dependency:tree`: `BUILD SUCCESS`, sem conflitos (swagger-core 3.0.0, swagger-ui 5.35.1).
- Validação manual (desenvolvedor): `/swagger-ui.html` e `/v3/api-docs` OK; "FitManager API", v1, descrição, OpenAPI 3.1.0.

**Passo 3 — Tratamento global de exceções (ProblemDetail, RFC 9457)**
- `exception/GlobalExceptionHandler` (`@RestControllerAdvice`, estende `ResponseEntityExceptionHandler`) e `exception/ResourceNotFoundException`.
- 404 (recurso inexistente), 400 com lista `errors` (validação), 500 genérico (stack trace só no log). Demais exceções do Spring MVC (400, 404 de rota, 405, 415...) mantêm o status da classe base.
- Campo `instance` preenchido com o caminho da requisição (sem query string) em **todas** as respostas, num ponto único: sobrescrita de `handleExceptionInternal`.
  - Causa investigada: no Spring Framework 7.1.0, nenhuma classe do Spring MVC chama `ProblemDetail.setInstance` (verificado no bytecode). Os handlers da classe base passam `body = null`, por isso o `ProblemDetail` é obtido da própria exceção (`ErrorResponse.getBody()`).
- Não foi usado `spring.mvc.problemdetails.enabled=true`: a propriedade só atua quando não existe handler próprio.

**Passo 4 — Testcontainers**
- Dependências de teste `spring-boot-testcontainers` e `testcontainers-postgresql` (2.0.5, via BOM).
- `TestcontainersConfiguration` (`@TestConfiguration`, bean `PostgreSQLContainer("postgres:16-alpine")` com `@ServiceConnection`).
- Testes **não usam** `.env` nem `fitmanager-db`: container próprio em porta aleatória. Contadores de escrita do `fitmanager-db` ficaram `0|0|0` antes e depois. Containers de teste e Ryuk são removidos ao final.

**Passo 5 — Testes automatizados**
- `exception/GlobalExceptionHandlerTest` (`@WebMvcTest`, controller aninhado só de teste — nenhum endpoint fictício em produção), 7 cenários: 404; `instance` sem query string; 400 de validação (2 erros, mensagens customizadas); 400 de JSON malformado; 405; 415; 500 sem vazamento (sem `trace`, sem `exception`, sem mensagem interna, sem nome da classe). Todos verificam `application/problem+json`, `status` e `instance`.
- `config/OpenApiDocsTest` (`@SpringBootTest` + `@AutoConfigureMockMvc` + Testcontainers): `/v3/api-docs` → 200, OpenAPI 3.1.x, título e versão.
- `FitmanagerApplicationTests.contextLoads` com Testcontainers.
- **Resultado real** (sessão sem nenhuma variável `DB_*`): `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- Histórico: a primeira execução falhou em `$.instance` (o campo não era preenchido), o que levou à investigação e à implementação descritas no Passo 3.

**Passo 6 — Documentação:** README, PROJECT, PROGRESS e CLAUDE revisados.

**Conceitos para entrevista:** OpenAPI × Swagger, code-first; starters e auto-configuração; BOM; `@Configuration`/`@Bean`; RFC 9457 e `@RestControllerAdvice`; exceções checked × unchecked; não vazar stack trace (OWASP); Testcontainers × H2; `@ServiceConnection`; `@WebMvcTest` × `@SpringBootTest`; context caching; pirâmide de testes.

## Etapa 3 — Alunos / backend (em andamento)
**Plano aprovado em 2026-11-24**, com cinco decisões:
1. Alunos são **desativados** (`status: INACTIVE`), nunca excluídos. Não há `DELETE`, para preservar fichas e histórico.
2. `birthDate` obrigatória; `phone` opcional.
3. CPF fora do escopo (dado pessoal; minimização de dados pela LGPD — não é necessário para nenhuma funcionalidade atual).
4. Contrato da API publicado **antes** da implementação, em PR separado, para o frontend trabalhar com mocks.
5. Prefixo `/api` e recursos em inglês (`/api/students`).

**Ajustes do desenvolvedor incorporados ao plano:**
- Mensagem de e-mail duplicado **não reproduz o e-mail**.
- `DataIntegrityViolationException` só vira 409 quando for a violação da constraint `uk_students_email`. Qualquer outra violação de integridade continua sendo erro interno (500).
- Normalização **antes** da validação (as regras valem para o valor normalizado). Telefone ausente, `null`, `""` ou só espaços → `null`.
- Ordenação com **lista explícita** de campos (`fullName`, `email`, `birthDate`, `createdAt`). Campo fora da lista → 400. Desempate estável por `id,asc`.
- Formato exato das respostas e tipos TypeScript documentados no contrato.

**Passos:**
- [x] **Passo 1 — Contrato da API:** [API_CONTRACT.md](API_CONTRACT.md) v1.0.0 (`a078044`), integrado na `main` pelo **PR #2** (merge `d44dbf8`).
- [x] **Passo 2 — Flyway e migração inicial** (branch `feat/students-backend`, sem commit):
  - Dependências `spring-boot-starter-flyway` e `flyway-database-postgresql`, sem versão no `pom.xml` (BOM: Spring Boot 4.2.0, Flyway 12.11.0).
  - `db/migration/V1__create_students_table.sql`: `pk_students`, `uk_students_email`, `ck_students_status`, `NOT NULL` em todos os campos exceto `phone`, id `GENERATED ALWAYS AS IDENTITY`. Duas constraints a mais que a modelagem original: `ck_students_email_lowercase` (garante no banco a unicidade sem diferenciar maiúsculas) e `ck_students_phone_digits` (10 ou 11 dígitos, como no contrato).
  - `spring.jpa.hibernate.ddl-auto` de `none` para `validate`.
  - `migration/FlywayMigrationTest` (10 testes, `@SpringBootTest` + Testcontainers + `JdbcTemplate`, transação revertida): histórico V1 com `success = true`; colunas, tipos, tamanhos e nulidade; id gerado e id manual rejeitado; `phone` nulo aceito; e-mail duplicado, e-mail com maiúsculas, status desconhecido, telefone não numérico e campo obrigatório nulo rejeitados, cada um pela constraint esperada.
  - Log real do Flyway (Testcontainers): `Current version of schema "public": << Empty Schema >>` → `Successfully applied 1 migration to schema "public", now at version v1`.
  - **Resultado real** (sem variáveis `DB_*`): `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`. A primeira execução teve 2 falhas, ambas erros do próprio teste (o valor de telefone com máscara excedia o `VARCHAR(11)` antes do `CHECK`; a mensagem do id manual fica na causa raiz da exceção). O schema não foi alterado para fazer os testes passarem.
  - Banco de desenvolvimento **não foi tocado**: `fitmanager-db` continua com 0 tabelas em `public`; volume `fitmanager_fitmanager-data` preservado. Nenhum `flyway clean`, `DROP TABLE` ou `baseline-on-migrate`. A V1 será aplicada no `fitmanager-db` no próximo `spring-boot:run` (schema vazio, sem necessidade de baseline).
  - Aprovado pelo desenvolvedor, incluindo as duas constraints extras.
- [x] **Passo 3 — Entidade e repository** (branch `feat/students-backend`, sem commit):
  - `entity/StudentStatus` (`ACTIVE`, `INACTIVE`) e `entity/Student`: `@Entity`/`@Table("students")`, id `IDENTITY`, `@Enumerated(STRING)`, nomes e tamanhos de coluna explícitos, `created_at` com `updatable = false`. Construtor `protected` para o JPA; construtor público que sempre cria como `ACTIVE`; métodos de domínio `updateData(...)` e `changeStatus(...)`; apenas getters públicos; sem Lombok.
  - Timestamps por `@PrePersist`/`@PreUpdate` (JPA padrão): um único instante na criação (`createdAt == updatedAt`), truncado em microssegundos (precisão do `TIMESTAMPTZ`), para o valor em memória ser igual ao lido do banco. `@PreUpdate` só dispara se a entidade mudou: repetir o status atual não altera `updatedAt`.
  - `repository/StudentRepository` (`JpaRepository<Student, Long>`): `existsByEmail`, `existsByEmailAndIdNot` (derivadas do nome) e `search(pattern, status, pageable)` em JPQL (trecho do nome **ou** e-mail sem diferenciar maiúsculas; `status` nulo = todos). `containsPattern(text)` normaliza o texto e **escapa os curingas do LIKE** (`%`, `_`, `\`), para que a busca seja literal, como diz o contrato.
  - `repository/StudentRepositoryTest` (`@DataJpaTest` + Testcontainers, 17 testes): `ddl-auto=validate` ativo; criação como `ACTIVE` com timestamps iguais; releitura do banco com valores idênticos; edição preserva `createdAt` e avança `updatedAt`; troca de status; status repetido não altera `updatedAt`; duplicidade expõe o nome da constraint `uk_students_email` (base do 409 no Passo 4); existência de e-mail (incluindo "outro aluno"); busca por nome sem diferenciar maiúsculas, por trecho de e-mail, vazia/em branco, por status, combinada; `_` e `%` tratados literalmente; paginação com totais; desempate estável por `id`; montagem do padrão escapado.
  - **Compatibilidade entidade × V1 comprovada de duas formas:** (1) todos os contextos sobem com `ddl-auto=validate`; (2) checagem negativa temporária — renomear a coluna `full_name` na entidade fez o contexto falhar com `SchemaManagementException: Schema validation: missing column [full_name_errado]`. O arquivo foi restaurado (checksum idêntico) e a suíte rodou novamente.
  - **Migration V1 preservada:** não foi alterada neste passo; nenhuma mudança estrutural foi necessária.
  - **Resultado real** (sem variáveis `DB_*`): `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`. Banco de desenvolvimento continua com 0 tabelas.
  - Aprovado pelo desenvolvedor.
- [x] **Passo 4 — DTOs e service** (branch `feat/students-backend`, sem commit):
  - `dto/StudentRequest` (record): **construtor compacto normaliza** (nome: `strip` + espaços internos únicos; e-mail: `strip` + minúsculas com `Locale.ROOT`; telefone: ausente/`""`/espaços → `null`, máscara não removida). O Jackson cria o record antes do Bean Validation, então as regras valem para o valor normalizado. Anotações com as **mensagens exatas do contrato**.
  - `dto/StudentStatusRequest` (record, `@NotNull` com `O status é obrigatório`) e `dto/StudentResponse` (record + `from(Student)`, mapeamento manual).
  - `exception/DuplicateResourceException`: mensagem genérica `Já existe um aluno cadastrado com este e-mail.` (nunca o e-mail) + campo `field = "email"` para o item `errors` do 409 no Passo 5.
  - `service/StudentService`: `@Transactional(readOnly = true)` na classe; escrita com `@Transactional`. Operações: `create`, `findById`, `search`, `update`, `changeStatus` (desativação e reativação; idempotente). Regras: criação sempre `ACTIVE`; e-mail único verificado antes (`existsByEmail` / `existsByEmailAndIdNot` — o aluno mantém o próprio e-mail); 404 com `Aluno não encontrado.` (sem o id); `search` acrescenta `id ASC` como desempate quando ausente.
  - **Flush antes da resposta:** toda escrita usa `saveAndFlush`. Assim (1) o `@PreUpdate` atualiza `updatedAt` antes de montar a resposta e (2) uma violação de unicidade surge dentro do método, onde é traduzida.
  - **Duplicidade concorrente:** `DataIntegrityViolationException` só vira `DuplicateResourceException` se a causa for um `ConstraintViolationException` do Hibernate com `getConstraintName() = uk_students_email`. Qualquer outra violação é relançada intacta.
  - Testes:
    - `dto/StudentRequestTest` (10, unitário com o Bean Validation real): normalização; validação do valor normalizado (`"  ab  "` rejeitado); todas as mensagens do contrato; nome em branco gera as duas mensagens; telefone com máscara/tamanho errado; data futura/hoje.
    - `service/StudentServiceTest` (18, Mockito): criação `ACTIVE`; duplicidade prévia sem expor o e-mail e sem salvar; duplicidade concorrente traduzida (com a causa preservada); outra constraint e violação sem nome **não** traduzidas; 404; busca com padrão escapado e desempate; desempate não duplicado; edição mantendo o próprio e-mail; telefone removido na edição; e-mail de outro aluno rejeitado sem alterar a entidade; concorrência na edição; 404 na edição sem consultar e-mail; desativar, reativar, repetir status; 404 no status.
    - `service/StudentServicePersistenceTest` (3, `@DataJpaTest` + Testcontainers + service real): timestamps na resposta de criação; edição devolve `updatedAt` novo, igual ao gravado, e preserva `createdAt`; status devolve `updatedAt` novo e repetir o status não o altera.
  - **Prova negativa do flush:** trocando temporariamente `saveAndFlush` por `save`, os 2 testes de `updatedAt` falharam (resposta com valor antigo). Arquivo restaurado (checksum idêntico) e suíte reexecutada.
  - Migration V1 inalterada (checksum `e03ad24cb9ff05cf`).
  - **Resultado real** (sem variáveis `DB_*`): `Tests run: 67, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
  - Aprovado pelo desenvolvedor.
- [x] **Passo 5 — Controller, paginação, CORS e erros** (branch `feat/students-backend`, sem commit):
  - `controller/StudentController` (`/api/students`): `POST` → **201** + `Location: /api/students/{id}`; `GET /{id}`; `GET` paginado; `PUT /{id}`; `PATCH /{id}/status`. `@Valid @RequestBody` nos corpos. Sem `DELETE` (→ 405).
  - Paginação: resposta `PagedModel` (`{ content, page }`, formato `VIA_DTO`) + `spring.data.web.pageable.serialization-mode=via-dto`, `default-page-size=20`, `max-page-size=100` (propriedades do Boot, e não `@EnableSpringDataWebSupport`, que desligaria a auto-configuração e os limites). `@PageableDefault(sort = "fullName", ASC)`.
  - Ordenação: lista explícita `fullName`, `email`, `birthDate`, `createdAt`; qualquer outro campo (incluindo `id`), direção inválida (ex.: `fullName,up`, que o Spring interpreta como campo `up`) ou `ignorecase` → **400**. Desempate por `id` continua no service.
  - `search` com `@Size(max = 100)` (validação de método nativa do Spring 7 → 400). `status` desconhecido → 400.
  - **Confirmado nos testes** (pendência "a confirmar" do contrato, seção 2.6): `page=-1` → 0; `size=0` → 20; `page`/`size` não numéricos → padrões (0 e 20); `size=500` → 100. Coincide com o texto atual do contrato (o Spring substitui pelo padrão); a nota "a confirmar" pode ser removida numa futura revisão do contrato (v1.0.1, via PR).
  - `config/CorsConfig`: `/api/**`, origem de `app.cors.allowed-origins` (`http://localhost:5173`), métodos `GET, POST, PUT, PATCH, OPTIONS`, header permitido `Content-Type`, header exposto `Location`.
  - `GlobalExceptionHandler`: `DuplicateResourceException` → **409** (`title: Conflito`, `detail` genérico, `errors: [{field: "email", ...}]`). Tratamentos existentes preservados (400, 404, 405, 415, 500).
  - **Correção encontrada pelos testes:** no Spring 7.1, `ProblemDetail.getType()` é `null` por padrão e o Jackson o omite (`NON_EMPTY`) — nenhuma resposta de erro trazia `type`, contrariando o contrato (seção 3: "sempre presente"). Corrigido no ponto central `handleExceptionInternal` (`type = about:blank` quando ausente), valendo para todos os erros. `GlobalExceptionHandlerTest` passou a verificar `type` nos 6 cenários.
  - Swagger: `@Tag`, `@Operation` e `@ApiResponse` por endpoint; `@ParameterObject` expande `page`, `size`, `sort`.
  - Testes:
    - `controller/StudentControllerTest` (30, `@WebMvcTest` + `@MockitoBean`): 201 com `Location` e corpo do contrato (datas, timestamps ISO-8601 UTC, `phone: null` presente); normalização chegando ao service; 400 com mensagens do contrato sem chamar o service; data em formato errado → 400 sem `errors`; 409 com `errors[0].field = email` sem expor o e-mail; 415; 404 com `instance`; id não numérico; listagem com padrões e formato `content`/`page`; repasse de `search`, `status`, `page`, `size`, `sort` múltiplo; limites de `page`/`size`; 5 ordenações inválidas; `status` inválido; `search` com 100 e 101 caracteres; `PUT` 200/400/404/409; `PATCH` 200/400 (nulo)/400 (desconhecido)/404; `DELETE` → 405; CORS: preflight permitido, origem desconhecida → 403, `Location` exposto.
    - `config/OpenApiDocsTest` (+1): os cinco endpoints documentados (201 e 409 no `POST`), nenhum `DELETE`, parâmetros `search`, `status`, `page`, `size`, `sort`.
  - Migration V1 e `API_CONTRACT.md` inalterados.
  - **Resultado real** (sem variáveis `DB_*`): `Tests run: 98, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`. A primeira execução teve 1 falha (`$.type` ausente), que levou à correção acima.
  - Aprovado pelo desenvolvedor.
- [x] **Passo 6 — Integração ponta a ponta e validação final** (branch `feat/students-backend`):
  - [x] `integration/StudentApiIntegrationTest` (7 testes, `@SpringBootTest` + `@AutoConfigureMockMvc` + Testcontainers, **sem mocks**, sem `@Transactional` no teste — cada requisição faz commit real). A tabela do **container de teste** é esvaziada antes de cada teste (nunca o `fitmanager-db`). Reaproveita o contexto do `OpenApiDocsTest` (mesma configuração).
    - Fluxo completo: cadastro (201, `Location`, normalização, `ACTIVE`, `createdAt == updatedAt`, valores iguais aos gravados) → consulta pelo `Location` → edição (mantém o próprio e-mail, telefone omitido vira `null`, `createdAt` preservado, `updatedAt` avança e é igual ao gravado) → desativação → repetição idempotente (`updatedAt` igual) → inativo consultável → reativação.
    - Unicidade: e-mail com maiúsculas diferentes → 409 sem expor o e-mail, continua 1 linha; edição com e-mail de outro aluno → 409 sem alterar a linha; próprio e-mail → 200.
    - Listagem: ordem padrão com desempate por id entre homônimos, metadados de página, segunda página, página além da última (vazia), busca por nome/e-mail, filtro por status, busca + status, `%` literal, `sort=email,desc`.
    - Erros: 400 com `errors` (validação do valor normalizado) sem gravar nada; 400 para ordenação, `status`, `search` > 100 e status desconhecido no PATCH; 404 em GET/PUT/PATCH com `type`, `title`, `detail`, `instance`.
    - Na primeira execução, 1 falha **do próprio teste**: `jsonPath(...).value(List.of(...))` no Spring 7 converte o JSON para o tipo exato do valor esperado (`ImmutableCollections`) e resulta em `null`. A resposta real foi impressa e estava correta; trocado por `contains(...)` do Hamcrest.
  - [x] Migration V1 (`e03ad24cb9ff05cf`) e `API_CONTRACT.md` (conteúdo idêntico ao commit; diferença de checksum era só CRLF) preservados.
  - [x] **Resultado real** (sem variáveis `DB_*`): `Tests run: 105, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
  - [x] **Validação manual no Swagger com o `fitmanager-db` — realizada e confirmada pelo desenvolvedor:** inicialização do Spring Boot, conexão com o PostgreSQL e migration V1 com sucesso; `POST` 201; `POST` com e-mail duplicado 409 com ProblemDetail correto; `GET` listagem 200; `GET` id 1 200 com nome e e-mail normalizados; `PUT` 200; `PATCH` desativação 200; `PATCH` reativação 200.
  - [x] **Conferência no `fitmanager-db` (sessão `READ ONLY`, nenhum dado alterado):** `flyway_schema_history` com a versão 1 (`create students table`, `success = true`) como única entrada; tabelas `students` e `flyway_schema_history`; aluno id 1 com os dados editados, e-mail normalizado (`teste.swagger@exemplo.com`), `status = ACTIVE` e `updated_at > created_at`; 1 aluno e 1 e-mail distinto, nenhum e-mail repetido. O aluno fictício permanece no banco de desenvolvimento.
  - [x] Revisão final: diff coerente; sem prints de depuração ou TODOs; nenhuma credencial (valores reais de `DB_*` presentes em 0 arquivos); nenhum arquivo sensível; `.env` ignorado.
  - [x] Suíte final com `mvnw clean test` (sem variáveis `DB_*`): `Tests run: 105, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
  - [x] README e PROJECT atualizados (funcionalidade de alunos, endpoints, Flyway, níveis de teste, status das etapas).
  - Commit autorizado: `feat: implement student management API with PostgreSQL persistence` (sem push).

**A confirmar na implementação (registrado no contrato):** comportamento para `page` negativo e `size` < 1 ou não numérico. O padrão do Spring é substituir pelo valor padrão.

## Estado atual do código
```
src/main/java/com/fitmanager/api
├── FitmanagerApplication.java
├── config/CorsConfig.java, OpenApiConfig.java
├── controller/StudentController.java
├── dto/StudentRequest.java, StudentResponse.java, StudentStatusRequest.java
├── entity/Student.java, StudentStatus.java
├── exception/DuplicateResourceException.java, GlobalExceptionHandler.java, ResourceNotFoundException.java
├── repository/StudentRepository.java
└── service/StudentService.java
src/main/resources
├── application.properties (ddl-auto=validate), application-dev.properties
└── db/migration/V1__create_students_table.sql
src/test/java/com/fitmanager/api
├── FitmanagerApplicationTests.java, TestcontainersConfiguration.java
├── config/OpenApiDocsTest.java
├── exception/GlobalExceptionHandlerTest.java
├── controller/StudentControllerTest.java
├── dto/StudentRequestTest.java
├── integration/StudentApiIntegrationTest.java
├── migration/FlywayMigrationTest.java
├── repository/StudentRepositoryTest.java
└── service/StudentServiceTest.java, StudentServicePersistenceTest.java
```
API de alunos completa (V1, entidade, repository, DTOs, service, controller, CORS) e coberta por testes ponta a ponta. Falta apenas a validação manual no Swagger.

## Pontos de atenção pendentes
1. **403 × 500 (Etapa 7):** o handler genérico de `Exception` vai capturar `AccessDeniedException`/`AuthenticationException` do Spring Security e devolver 500. Tratar ao adicionar a segurança.
2. **Mensagens de validação:** decidido — `message` explícita em português nos DTOs (texto exato definido no `API_CONTRACT.md`). Será aplicado no Passo 4 da Etapa 3.
3. **Swagger em produção:** desabilitar `springdoc.api-docs.enabled` e `springdoc.swagger-ui.enabled` no futuro perfil `prod`.
4. **Perfil `prod`:** ainda não existe (necessário no deploy).
5. **`pom.xml`:** `<name/>`, `<description/>`, `<licenses>`, `<developers>`, `<scm>` vazios (padrão do Initializr).
6. **Avisos do Mockito** (self-attach de agente Java) nos testes: não são falhas.
7. **Testes exigem Docker Desktop aberto** (Testcontainers).
8. **Fim de linha:** Git avisa conversão LF → CRLF (`core.autocrlf`). Opcional: `.gitattributes` na raiz.
9. **Dois contextos Spring nos testes (correção de registro anterior):** o log do Passo 2 mostrou dois containers PostgreSQL por execução. O `OpenApiDocsTest` (com `@AutoConfigureMockMvc`) e o `FitmanagerApplicationTests` têm configurações diferentes, então **não** compartilham contexto, ao contrário do que foi afirmado no Passo 5 da Etapa 2. O `FlywayMigrationTest` reaproveita o contexto do `FitmanagerApplicationTests`. Funciona corretamente; só custa ~15 s a mais. Otimização possível, não aplicada: unificar a configuração dos testes de integração.
10. **Aviso do Maven:** `Could not transfer metadata ... flyway-community-db-support ... 401`. O POM do Flyway declara um repositório do GitHub Packages que exige login; o Maven tenta consultá-lo e falha, mas resolve tudo pelo Maven Central. Não afeta o build.
11. ~~`updatedAt` na resposta~~ — **resolvido no Passo 4** (`saveAndFlush` em todas as escritas, comprovado por teste com banco real e prova negativa).
12. **Busca sem acentos:** a pesquisa diferencia acentos (`joao` não encontra `João`). Não está no contrato; possível melhoria futura com a extensão `unaccent` do PostgreSQL (exigiria nova migration).
13. **`equals`/`hashCode` da entidade:** não implementados (nenhum uso em `Set`/`Map` hoje). Se forem necessários, usar identidade por `id` com o cuidado de entidades ainda não persistidas.
14. ~~`DuplicateResourceException` como 500~~ — **resolvido no Passo 5** (409).
15. ~~Pendências da camada HTTP~~ — **resolvidas no Passo 5** (ordenação, `search`, `size`, `VIA_DTO`).
16. **CORS em produção:** definir `APP_CORS_ALLOWED_ORIGINS` com o domínio da Vercel no deploy (Etapa 10).
17. **Mensagens técnicas em inglês nos 400 genéricos** (ordenação inválida usa mensagem própria em português; `search` > 100, enum inválido, JSON malformado vêm do Spring). Previsto no contrato (seção 3.3: `detail` não exibível nesses casos).

## Decisões técnicas
| Data | Decisão | Motivo |
|---|---|---|
| 2026-11-24 | Docs na raiz do repositório (monorepo `fitmanager/` + futuro `frontend/`) | Um único repositório para backend e frontend |
| 2026-11-24 | Arquitetura em camadas, DTOs separados das entidades | Não acoplar a API ao banco |
| 2026-11-24 | Credenciais só em `.env` (ignorado) via variáveis de ambiente | Segredos fora do código e do Git |
| 2026-11-24 | PostgreSQL via Docker Compose em `127.0.0.1` | Ambiente reproduzível, banco não exposto na rede |
| 2026-11-24 | `ddl-auto=none` | Schema versionado por migrations |
| 2026-11-24 | springdoc-openapi 3.2.1 | Documentação gerada do código; versão alinhada ao Boot 4.2 |
| 2026-11-24 | ProblemDetail (RFC 9457) estendendo `ResponseEntityExceptionHandler` | Padrão IETF, nativo do Spring, status corretos sem reimplementar |
| 2026-11-24 | `instance` = `getRequestURI()` num ponto único | Consistência em todos os erros; query string fora (pode conter dados sensíveis) |
| 2026-11-24 | Testcontainers, sem H2 | Testar contra o mesmo PostgreSQL de produção |
| 2026-11-24 | Flyway na Etapa 3 | Instalar junto com a primeira migration (YAGNI) |
| 2026-11-24 | Mapeamento DTO ↔ entidade manual | Poucas entidades; código explícito; reavaliar MapStruct depois |
| 2026-11-24 | Spring Security + JWT (resource server) na Etapa 7 | Gerenciado pelo Boot, stateless, adequado a SPA |
| 2026-11-24 | Alunos desativados, sem `DELETE` | Preserva fichas e histórico (Etapas 5 e 6) |
| 2026-11-24 | Id `Long` (identity) | Simples e eficiente; a previsibilidade do id é tratada pela autenticação |
| 2026-11-24 | `status` como `VARCHAR` + `@Enumerated(STRING)` + `CHECK` | Evita o bug do `ORDINAL` ao reordenar o enum |
| 2026-11-24 | E-mail único, normalizado em minúsculas | Unicidade sem diferenciar maiúsculas; constraint no banco cobre condições de corrida |
| 2026-11-24 | Contract-first (`API_CONTRACT.md`) | Backend e frontend em paralelo sem bloqueio |
| 2026-11-24 | Paginação `VIA_DTO` (`content` + `page`), `size` máx. 100, desempate por `id` | Formato estável recomendado pelo Spring Data; proteção contra páginas enormes; ordem determinística |

## Compatibilidade verificada
- Gerenciadas pelo BOM do Spring Boot 4.2.0: Spring Framework 7.1.0, Flyway 12.11.0, Testcontainers 2.0.5, Hibernate 7.4.15, Hibernate Validator 9.1.6, Jackson 3.1.8, JUnit Jupiter 6.1.3, driver PostgreSQL 42.7.14.
- Fora do BOM: springdoc-openapi 3.2.1 (compilado contra Boot 4.2.0). MapStruct estável: 1.6.3 (não utilizado).
- Mudanças do Boot 4 a lembrar: `@WebMvcTest`/`@AutoConfigureMockMvc` em `org.springframework.boot.webmvc.test.autoconfigure`; Jackson 3 (`tools.jackson`); Testcontainers 2 (`org.testcontainers.postgresql.PostgreSQLContainer`).

## Próximos passos (aguardando autorização)
1. Push da `feat/students-backend` e PR para a `main` (cada um com autorização).
2. Opcional: contrato v1.0.1 removendo a nota "a confirmar" da seção 2.6 (comportamento já confirmado por testes).
3. Planejar o próximo módulo (Etapa 4 — Exercícios), somente com autorização.
