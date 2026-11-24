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
| Testes | JUnit 5 (+ Spring Boot Test) |
| Documentação da API | Swagger/OpenAPI (a adicionar) |
| Frontend | React, TypeScript, Tailwind CSS (a criar) |
| Infra | Docker, Git/GitHub |
| Deploy | Render (backend) e Vercel (frontend) |

Dependências hoje no `pom.xml`: `spring-boot-starter-webmvc`, `-data-jpa`, `-validation`, `spring-boot-devtools`, driver `postgresql` e starters de teste (`data-jpa-test`, `validation-test`, `webmvc-test`).
Pendentes: Swagger/OpenAPI, Spring Security, migrations (Flyway), Dockerfile da aplicação. (O PostgreSQL já roda via `docker-compose.yml`.)

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

## Planejamento por etapas (proposta, sujeita à aprovação)
| # | Etapa | Resultado esperado |
|---|---|---|
| 0 | Documentação inicial | Os 4 arquivos de docs |
| 1 | Ambiente e banco | PostgreSQL (Docker Compose), perfis `dev`, app subindo conectada ao banco |
| 2 | Base da API | Estrutura de pacotes, tratamento global de erros, Swagger/OpenAPI |
| 3 | Alunos | CRUD completo com validação e testes |
| 4 | Exercícios | CRUD completo com testes |
| 5 | Fichas de treino | Relacionamento aluno–exercícios, regras e testes |
| 6 | Evolução | Registro e consulta do histórico do aluno |
| 7 | Autenticação | Spring Security + JWT, perfis e autorização |
| 8 | Dashboard (API) | Endpoints de indicadores |
| 9 | Frontend | React + TS + Tailwind consumindo a API |
| 10 | Docker e deploy | Dockerfile, CI, deploy em Render e Vercel |

## Decisões em aberto (a discutir)
- Migrations: Flyway vs. `ddl-auto` no início.
- Autenticação: JWT stateless (provável) vs. sessão.
- Testes de repositório: Testcontainers (fiel ao PostgreSQL) vs. H2.
- Mapeamento DTO ↔ entidade: manual vs. MapStruct.
- Estrutura do repositório: monorepo (`fitmanager/` + `frontend/`) é a proposta atual.
