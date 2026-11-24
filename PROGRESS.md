# PROGRESS.md — Andamento do FitManager

Última atualização: 2026-11-24

## Concluído
- [x] JDK 21 instalado e verificado (`openjdk 21.0.13.1 LTS`).
- [x] VS Code configurado com extensões Java e Spring Boot.
- [x] Projeto gerado pelo Spring Initializr (Maven, Spring Boot 4.2.0, `com.fitmanager:fitmanager`, pacote `com.fitmanager.api`).
- [x] Repositório Git inicializado (branch `main`) com o primeiro commit `chore: initialize project structure and PostgreSQL environment` (local, sem push).
- [x] Documentação inicial: `CLAUDE.md`, `PROJECT.md`, `PROGRESS.md`, `README.md`.
- [x] **Etapa 1 — Ambiente e banco** (detalhes abaixo).

## Etapa 1 — Ambiente e banco (concluída em 2026-11-24)
**Criado:** `docker-compose.yml` (PostgreSQL 16, porta exposta só em `127.0.0.1`, volume `fitmanager-data`, healthcheck), `.env.example`, `.gitignore` da raiz (ignora `.env`), `application.properties` (datasource via `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; `ddl-auto=none`; `open-in-view=false`) e `application-dev.properties` (perfil padrão `dev`, log de SQL).

**Resultados confirmados:**
- Docker 29.9.3 e Compose v5.7.1 funcionando.
- Container `fitmanager-db` (`postgres:16-alpine`) com status `healthy`.
- `./mvnw spring-boot:run`: HikariPool conectou, PostgreSQL 16.16, Hibernate/JPA inicializados, Tomcat na porta 8080, `Started FitmanagerApplication in 3.416 seconds` (resultado informado pelo desenvolvedor, executado na máquina dele).
- `.\mvnw.cmd test`: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` (1 teste: `FitmanagerApplicationTests.contextLoads`, que sobe o contexto completo conectado ao PostgreSQL).

**Como rodar localmente:**
1. `Copy-Item .env.example .env` e preencher usuário/senha.
2. `docker compose up -d` (raiz do repositório).
3. Carregar o `.env` na sessão (o Spring **não** lê `.env` sozinho):
   `Get-Content .env | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object { $k,$v = $_ -split '=',2; Set-Item "env:$($k.Trim())" $v.Trim() }`
4. Dentro de `fitmanager/`: `.\mvnw.cmd spring-boot:run` ou `.\mvnw.cmd test`.

**Limitação conhecida:** o teste `contextLoads` depende do banco no ar e das variáveis `DB_*` definidas. Sem eles, falha. Isso será resolvido quando houver testes de repositório com banco isolado (ver decisão pendente sobre Testcontainers/H2).

**Conceitos para entrevista:** variáveis de ambiente e externalização de configuração (12-factor); Docker Compose, volumes e healthcheck; por que `ddl-auto=none` + migrations; `open-in-view=false`; perfis do Spring; pool de conexões (HikariCP).

## Estado verificado do código
- Apenas a classe `FitmanagerApplication` e o teste `FitmanagerApplicationTests` (gerados pelo Initializr).
- Nenhuma entidade, controller, service ou repository.

## Pendências e observações
- `pom.xml` tem `<name/>`, `<description/>`, `<licenses>`, `<developers>`, `<scm>` vazios (padrão do Initializr). Preencher/limpar depois.
- Ainda não há dependências de Swagger/OpenAPI, Security nem Flyway.
- Perfil `prod` ainda não existe (será necessário no deploy).
- O Mockito emite avisos de self-attach de agente Java nos testes. Não é falha. Pode ser tratado depois.

## Decisões técnicas
| Data | Decisão | Motivo |
|---|---|---|
| 2026-11-24 | Docs na raiz do repositório Git (pasta externa), não dentro do módulo Maven | A raiz comporta o backend e o futuro `frontend/` (monorepo) |
| 2026-11-24 | Arquitetura em camadas com DTOs separados das entidades | Evita acoplar API ao banco; padrão cobrado em entrevistas |
| 2026-11-24 | Credenciais do banco só em `.env` (ignorado pelo Git) e lidas por variáveis de ambiente | Segredos nunca no código nem no repositório |
| 2026-11-24 | PostgreSQL via Docker Compose, porta ligada só a `127.0.0.1` | Ambiente reproduzível, sem expor o banco na rede |
| 2026-11-24 | `ddl-auto=none` | O schema será versionado por migrations, não pelo Hibernate |

## Próximos passos (aguardando autorização)
1. Criar o repositório remoto no GitHub e fazer o push (somente com autorização).
2. Decidir os itens em aberto do `PROJECT.md` (Flyway, JWT, Testcontainers/H2, MapStruct).
3. **Etapa 2 — Base da API:** estrutura de pacotes, tratamento global de erros e Swagger/OpenAPI. Só após aprovação do plano.
