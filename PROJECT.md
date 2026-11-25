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
1. Autenticação e controle de acesso (perfis: professor/instrutor e aluno; administração a definir).
2. Cadastro e gerenciamento de alunos.
3. Catálogo de exercícios (um grupo muscular principal por exercício).
4. Fichas de treino nas três modalidades (ver abaixo), compostas por exercícios, séries, repetições e cargas.
5. Registro de treinos realizados e histórico de evolução dos alunos (desempenho por exercício, medidas, peso).
6. Dashboard administrativo.
7. Validação de dados e tratamento de erros padronizado.
8. Testes automatizados.

Fora do escopo por enquanto: pagamentos, controle de catraca, app mobile. Qualquer item novo exige aprovação.

## Modalidades de treino (definição do produto — 2026-11-25)

O FitManager oferece **três modalidades de treino**:

| Modalidade | Quem cria | O que é |
|---|---|---|
| **Treino do professor** | Professor/instrutor | Ficha personalizada pelo instrutor para um aluno específico. |
| **Treino personalizado** | Aluno | O aluno cria as próprias fichas: escolhe nomes, exercícios, séries, repetições e cargas. |
| **Treino livre** | Aluno | O aluno escolhe exercícios na hora, de qualquer grupo muscular, registra o desempenho e, opcionalmente, salva a combinação como uma ficha (que passa a ser um treino personalizado). |

**Princípio:** nenhum aluno fica limitado a uma divisão predefinida de treino (ex.: "A/B/C", "peito e tríceps") nem a um subconjunto de exercícios. Qualquer exercício **ativo** do catálogo pode ser usado em qualquer modalidade. O grupo muscular serve para organizar e filtrar o catálogo, nunca para restringir.

### Consequências para as próximas etapas (proposta técnica, a detalhar e aprovar em cada etapa)
- **Ficha (Etapa 5):** pertence a um aluno e registra **quem a criou** (professor ou o próprio aluno) e a **origem** (professor, personalizado, salva a partir de treino livre). Contém itens ordenados, cada um com exercício, séries, repetições e carga planejadas. Um aluno pode ter várias fichas ativas ao mesmo tempo.
- **Treino realizado / sessão (Etapa 6):** registro do que o aluno **efetivamente fez** em uma data: exercícios, séries, repetições e cargas executadas. Pode ser feito a partir de uma ficha (qualquer modalidade) ou **sem ficha** (treino livre). Uma sessão livre pode ser convertida em ficha.
- **Histórico de evolução (Etapa 6):** construído a partir das sessões registradas (ex.: evolução da carga de um exercício ao longo do tempo), somando medidas corporais e peso. Por isso, o histórico deve guardar os valores **executados**, e não depender da ficha atual (fichas podem ser editadas ou desativadas sem apagar o passado).
- **Catálogo de exercícios (Etapa 4):** exercícios são desativados, não excluídos, para não quebrar fichas e sessões antigas. Exercício inativo não pode ser adicionado a fichas/sessões novas, mas continua visível no histórico.
- **Permissões (Etapa 7)** — proposta inicial:

| Ação | Professor | Aluno |
|---|---|---|
| Gerenciar alunos | sim | apenas o próprio perfil (a definir) |
| Gerenciar catálogo de exercícios | sim | não (consulta) |
| Criar/editar ficha de professor | sim, para qualquer aluno | não (consulta das próprias) |
| Criar/editar ficha personalizada | a definir | sim, apenas as próprias |
| Registrar treino (com ficha ou livre) | a definir | sim, apenas os próprios |
| Ver histórico de evolução | sim, de qualquer aluno | apenas o próprio |

- **Dependência de ordem:** as Etapas 5 e 6 precisam saber "quem é o aluno" e "quem criou a ficha". **Decisão provisória do desenvolvedor (2026-11-25): antecipar a autenticação para antes da implementação das fichas de treino**, para que autoria, propriedade e permissões já nasçam do usuário autenticado. A confirmar após a conclusão da Etapa 4; se confirmada, a numeração das etapas será revista nesse momento. A Etapa 4 (exercícios) segue sem alteração.

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
| 3 | Alunos | Contrato da API, Flyway + V1, entidade, repository, DTOs, service, controller, CORS e testes | ✅ backend (contrato no PR #2; implementação no PR #3) |
| 4 | Exercícios | Catálogo com um grupo muscular principal, migration V2, CRUD com desativação, contrato v1.1.0 e testes | 🔄 Passo 1 (contrato v1.1.0) aprovado; Passos 2 a 6 pendentes |
| 5 | Fichas de treino | Fichas das três modalidades (professor, personalizada, salva a partir de treino livre), com autoria, origem e itens ordenados (exercício, séries, repetições, carga) | |
| 6 | Treinos realizados e evolução | Registro de sessões (com ficha ou livre), conversão de sessão livre em ficha, histórico de desempenho por exercício, medidas e peso | |
| 7 | Autenticação | Spring Security + JWT, perfis professor e aluno, permissões da seção "Modalidades de treino" | ⚠️ decisão provisória: antecipar para antes da Etapa 5 (confirmar após a Etapa 4) |
| 8 | Dashboard (API) | Endpoints de indicadores |
| 9 | Frontend | React + TS + Tailwind consumindo a API | 🔄 em paralelo (agente frontend, branch `feat/frontend-base`) |
| 10 | Docker e deploy | Dockerfile, CI, deploy em Render e Vercel |

## Decisões tomadas (detalhes e justificativas em `PROGRESS.md`)
- Migrations: **Flyway** (V1 = tabela `students`); `ddl-auto=validate`.
- Integração backend/frontend: **contract-first** via `API_CONTRACT.md`.
- Alunos: **desativação** em vez de exclusão; e-mail único normalizado; CPF fora do escopo.
- Exercícios: **um grupo muscular principal** por exercício, de uma lista fixa (enum); desativação em vez de exclusão; nome único sem diferenciar maiúsculas.
- Treinos: **três modalidades** (professor, personalizado, livre) e **nenhuma restrição** de divisão de treino ou de exercícios para o aluno. Ficha planejada separada da sessão realizada; histórico de evolução preservado a partir das sessões; várias fichas ativas por aluno.
- Autenticação: **decisão provisória** de antecipá-la para antes das fichas (confirmar após a Etapa 4).
- Banco nos testes: **Testcontainers com PostgreSQL**; H2 não será usado.
- Erros: **ProblemDetail (RFC 9457)** via `@RestControllerAdvice`.
- Mapeamento DTO ↔ entidade: **manual** por enquanto (reavaliar MapStruct se ficar repetitivo).
- Autenticação: **Spring Security + JWT** (resource server) na Etapa 7.
- Repositório: **monorepo** (`fitmanager/` + futuro `frontend/`).
