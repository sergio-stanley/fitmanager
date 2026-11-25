# PROJECT.md — FitManager

## Visão
Sistema Full Stack de gerenciamento de academias, com qualidade profissional, para compor o portfólio de um desenvolvedor em busca da primeira oportunidade.

## Stack
| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 LTS |
| Framework | Spring Boot 4.2.0 (Maven) |
| Banco | PostgreSQL |
| Persistência | Spring Data JPA |
| Validação | Bean Validation |
| Testes | JUnit 6 (Jupiter), Spring Boot Test, MockMvc, Testcontainers |
| Documentação da API | springdoc-openapi (Swagger UI, OpenAPI 3.1) |
| Migrations | Flyway |
| Frontend | React, TypeScript, Tailwind CSS (em desenvolvimento pelo agente frontend) |
| Infra | Docker, Git/GitHub |
| Deploy | Render (backend) e Vercel (frontend) |

Dependências hoje no `pom.xml`: `spring-boot-starter-webmvc`, `-data-jpa`, `-validation`, `-flyway`, `flyway-database-postgresql`, `spring-boot-devtools`, driver `postgresql`, `springdoc-openapi-starter-webmvc-ui` 3.2.1 e, para testes, `data-jpa-test`, `validation-test`, `webmvc-test`, `spring-boot-testcontainers` e `testcontainers-postgresql`.
Pendentes: Spring Security (Etapa 7), Dockerfile da aplicação (Etapa 10). O PostgreSQL de desenvolvimento roda via `docker-compose.yml`.

## Escopo (funcionalidades planejadas)
1. Autenticação e controle de acesso (perfis, ex.: ADMIN e INSTRUTOR).
2. Cadastro e gerenciamento de alunos.
3. Cadastro de exercícios.
4. Gerenciamento de fichas de treino (ficha de um aluno composta por exercícios, séries e repetições).
5. Histórico de evolução dos alunos (medidas, peso, cargas).
6. Dashboard administrativo.
7. Validação de dados e tratamento de erros padronizado.
8. Testes automatizados.

Fora do escopo por enquanto: pagamentos, controle de catraca, app mobile. Qualquer item novo exige aprovação.

## Arquitetura
Monólito em camadas, API REST consumida por um SPA React.

```
com.fitmanager.api
├── controller   (HTTP, DTOs de entrada/saída, @Valid)
├── service      (regras de negócio, transações)
├── repository   (Spring Data JPA)
├── entity       (modelo de domínio/JPA)
├── dto          (records de request/response)
├── exception    (exceções de domínio + @RestControllerAdvice)
└── config       (segurança, CORS, OpenAPI)
```

Princípios: SOLID, código limpo, DTOs separados das entidades, injeção por construtor, sem complexidade desnecessária.

## Planejamento por etapas
| # | Etapa | Resultado esperado | Status |
|---|---|---|---|
| 0 | Documentação inicial | Os 4 arquivos de docs | ✅ |
| 1 | Ambiente e banco | PostgreSQL (Docker Compose), perfil `dev`, app subindo conectada ao banco | ✅ |
| 2 | Base da API | Swagger/OpenAPI, tratamento global de erros (ProblemDetail), Testcontainers e testes | ✅ (PR #1) |
| 3 | Alunos | Contrato da API, Flyway + V1, entidade, repository, DTOs, service, controller, CORS e testes | ✅ backend (contrato no PR #2; implementação commitada em `feat/students-backend`, aguardando push/PR) |
| 4 | Exercícios | CRUD completo com testes |
| 5 | Fichas de treino | Relacionamento aluno–exercícios, regras e testes |
| 6 | Evolução | Registro e consulta do histórico do aluno |
| 7 | Autenticação | Spring Security + JWT, perfis e autorização |
| 8 | Dashboard (API) | Endpoints de indicadores |
| 9 | Frontend | React + TS + Tailwind consumindo a API | 🔄 em paralelo (agente frontend, branch `feat/frontend-base`) |
| 10 | Docker e deploy | Dockerfile, CI, deploy em Render e Vercel |

## Decisões tomadas (detalhes e justificativas em `PROGRESS.md`)
- Migrations: **Flyway** (V1 = tabela `students`); `ddl-auto=validate`.
- Integração backend/frontend: **contract-first** via `API_CONTRACT.md`.
- Alunos: **desativação** em vez de exclusão; e-mail único normalizado; CPF fora do escopo.
- Banco nos testes: **Testcontainers com PostgreSQL**; H2 não será usado.
- Erros: **ProblemDetail (RFC 9457)** via `@RestControllerAdvice`.
- Mapeamento DTO ↔ entidade: **manual** por enquanto (reavaliar MapStruct se ficar repetitivo).
- Autenticação: **Spring Security + JWT** (resource server) na Etapa 7.
- Repositório: **monorepo** (`fitmanager/` + futuro `frontend/`).
