# PROGRESS.md — Andamento do FitManager

Última atualização: 2026-11-24

## Resumo
| Etapa | Status |
|---|---|
| 0 — Documentação inicial | ✅ Concluída |
| 1 — Ambiente e banco | ✅ Concluída (commit `03787bb`, no GitHub) |
| 2 — Base da API | ✅ Concluída — PR #1 integrado na `main` (merge `024d9e3`) |
| 3 — Alunos (backend) | 🔄 Em andamento — plano aprovado; Passo 1 (contrato da API) commitado em `docs/students-api-contract`, aguardando push/PR |

## Repositório
- GitHub (público): https://github.com/sergio-stanley/fitmanager (`origin`).
- `main` = `origin/main`: `024d9e3` — *Merge pull request #1 from sergio-stanley/feat/api-base* (contém `1d146e8`, Etapa 2, e `03787bb`, Etapa 1).
- `feat/api-base` (`1d146e8`): integrada; mantida local e remotamente até decisão do desenvolvedor.
- `docs/students-api-contract`: criada a partir de `024d9e3` só para o PR do contrato (`API_CONTRACT.md` + este arquivo).
- `feat/students-backend`: criada a partir de `024d9e3`; branch do backend da Etapa 3 (worktree principal).
- `feat/frontend-base`: criada pelo desenvolvedor via Orca, em worktree separada (`C:\Users\sstan\orca\workspaces\fitmanager\feat-frontend-base`); atualizada por fast-forward para `024d9e3`.

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
- [x] **Passo 1 — Contrato da API:** [API_CONTRACT.md](API_CONTRACT.md) v1.0.0 aprovado pelo desenvolvedor e commitado na branch `docs/students-api-contract` (`docs: add students API contract v1.0.0`). Aguardando push, PR e merge (cada um com autorização).
- [ ] Passo 2 — Flyway, `V1__create_students_table.sql`, `ddl-auto=validate`.
- [ ] Passo 3 — Entidade `Student`/`StudentStatus`, `StudentRepository` e `StudentRepositoryTest`.
- [ ] Passo 4 — DTOs, `StudentService`, `DuplicateResourceException` e `StudentServiceTest`.
- [ ] Passo 5 — `StudentController`, paginação, `CorsConfig`, novos tratamentos no handler e `StudentControllerTest`.
- [ ] Passo 6 — `StudentApiIntegrationTest`, validação manual no Swagger e documentação final.

**A confirmar na implementação (registrado no contrato):** comportamento para `page` negativo e `size` < 1 ou não numérico. O padrão do Spring é substituir pelo valor padrão.

## Estado atual do código
```
src/main/java/com/fitmanager/api
├── FitmanagerApplication.java
├── config/OpenApiConfig.java
└── exception/GlobalExceptionHandler.java, ResourceNotFoundException.java
src/test/java/com/fitmanager/api
├── FitmanagerApplicationTests.java, TestcontainersConfiguration.java
├── config/OpenApiDocsTest.java
└── exception/GlobalExceptionHandlerTest.java
```
Ainda não há entidades, controllers de produção, services nem repositories.

## Pontos de atenção pendentes
1. **403 × 500 (Etapa 7):** o handler genérico de `Exception` vai capturar `AccessDeniedException`/`AuthenticationException` do Spring Security e devolver 500. Tratar ao adicionar a segurança.
2. **Mensagens de validação:** decidido — `message` explícita em português nos DTOs (texto exato definido no `API_CONTRACT.md`). Será aplicado no Passo 4 da Etapa 3.
3. **Swagger em produção:** desabilitar `springdoc.api-docs.enabled` e `springdoc.swagger-ui.enabled` no futuro perfil `prod`.
4. **Perfil `prod`:** ainda não existe (necessário no deploy).
5. **`pom.xml`:** `<name/>`, `<description/>`, `<licenses>`, `<developers>`, `<scm>` vazios (padrão do Initializr).
6. **Avisos do Mockito** (self-attach de agente Java) nos testes: não são falhas.
7. **Testes exigem Docker Desktop aberto** (Testcontainers).
8. **Fim de linha:** Git avisa conversão LF → CRLF (`core.autocrlf`). Opcional: `.gitattributes` na raiz.

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
1. Push e PR da branch `docs/students-api-contract` para a `main` (cada um com autorização).
2. Após o merge: fast-forward da `feat/students-backend` (e, a critério do desenvolvedor, da `feat/frontend-base`) a partir da `main`.
3. Passo 2 da Etapa 3 (Flyway), somente com autorização.
