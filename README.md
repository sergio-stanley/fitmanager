# FitManager

Sistema Full Stack de gerenciamento de academias. Projeto de portfólio, em desenvolvimento.

> **Status:** API de **gerenciamento de alunos** implementada, com persistência em PostgreSQL, migrations Flyway e 105 testes automatizados. Os demais módulos estão planejados. Veja o andamento em [PROGRESS.md](PROGRESS.md).

## Funcionalidades
**Implementado (backend):**
- Cadastro e gerenciamento de alunos: cadastrar, consultar, listar com busca/filtro/paginação, editar, desativar e reativar (sem exclusão física, para preservar o histórico).

**Planejado:**
- Cadastro de exercícios
- Fichas de treino
- Histórico de evolução dos alunos
- Autenticação e controle de acesso
- Dashboard administrativo

## Tecnologias
**Backend:** Java 21, Spring Boot 4.2.0, Spring Web MVC, Spring Data JPA (Hibernate 7), Bean Validation, PostgreSQL 16, Flyway, Maven
**Documentação da API:** springdoc-openapi 3.2.1 (Swagger UI, OpenAPI 3.1) e [API_CONTRACT.md](API_CONTRACT.md)
**Testes:** JUnit 6 (Jupiter), Mockito, Spring Boot Test, MockMvc, Testcontainers 2
**Infra:** Docker e Docker Compose
**Planejado:** Spring Security + JWT, React, TypeScript, Tailwind CSS, deploy em Render e Vercel

## Estrutura
```
fitmanager/        backend Spring Boot (Maven)
frontend/          aplicação React (em desenvolvimento, em branch própria)
docker-compose.yml PostgreSQL para desenvolvimento
API_CONTRACT.md    contrato da API compartilhado com o frontend
PROJECT.md         escopo, arquitetura e planejamento
PROGRESS.md        andamento, decisões e pendências
CLAUDE.md          instruções de desenvolvimento e colaboração
```

## Pré-requisitos
- JDK 21
- Docker Desktop (para o banco de desenvolvimento e para os testes)

## Como executar (Windows/PowerShell)
Na raiz do repositório:
```powershell
Copy-Item .env.example .env        # depois preencha DB_USERNAME e DB_PASSWORD
docker compose up -d               # sobe o PostgreSQL (container fitmanager-db)

# carrega o .env na sessão atual (o Spring não lê .env sozinho)
Get-Content .env | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
  $k,$v = $_ -split '=',2; Set-Item "env:$($k.Trim())" $v.Trim()
}

cd fitmanager
.\mvnw.cmd spring-boot:run
```
A aplicação sobe na porta 8080. Na primeira execução, o Flyway cria as tabelas automaticamente (`db/migration`).

> Para parar o banco sem perder dados: `docker compose stop`. **Não** use `docker compose down -v`: o `-v` apaga o volume com os dados.

## API de alunos
Contrato completo, com exemplos e tipos TypeScript: [API_CONTRACT.md](API_CONTRACT.md).

| Método | Rota | Sucesso |
|---|---|---|
| `POST` | `/api/students` | 201 + header `Location` |
| `GET` | `/api/students/{id}` | 200 |
| `GET` | `/api/students?search=&status=&page=&size=&sort=` | 200 (paginado: `content` + `page`) |
| `PUT` | `/api/students/{id}` | 200 |
| `PATCH` | `/api/students/{id}/status` | 200 (ativar/desativar) |

Regras principais: e-mail único (sem diferenciar maiúsculas), dados normalizados antes da validação, novo aluno sempre `ACTIVE`, ordenação apenas por `fullName`, `email`, `birthDate` ou `createdAt` (com desempate estável por `id`), página de no máximo 100 itens.

## Documentação da API
Com a aplicação rodando:
- Swagger UI: http://localhost:8080/swagger-ui.html
- Especificação OpenAPI (JSON): http://localhost:8080/v3/api-docs

## Testes
Dentro de `fitmanager/`:
```powershell
.\mvnw.cmd test
```
Os testes **não** usam o `.env` nem o banco de desenvolvimento: o Testcontainers sobe um PostgreSQL descartável, que é removido ao final. Basta o Docker Desktop estar aberto.

| Nível | O que cobre |
|---|---|
| Unitário (Mockito, Bean Validation) | Regras do service, normalização e mensagens de validação |
| Web (`@WebMvcTest`) | Contrato HTTP: status, JSON, erros, paginação, CORS |
| Persistência (`@DataJpaTest`) | Migration, constraints, consultas, timestamps |
| Integração ponta a ponta (`@SpringBootTest`) | HTTP → banco real, sem mocks |

## Padrão de erros
Todas as respostas de erro seguem a **RFC 9457** (`application/problem+json`):
```json
{
  "type": "about:blank",
  "title": "Dados inválidos",
  "status": 400,
  "detail": "Um ou mais campos possuem valores inválidos.",
  "instance": "/caminho/da/requisicao",
  "errors": [{ "field": "email", "message": "O e-mail deve ser válido" }]
}
```
Erros inesperados retornam 500 com mensagem genérica; detalhes internos ficam apenas no log do servidor.

## Segurança
Credenciais nunca são versionadas: ficam no `.env` (ignorado pelo Git) e são lidas por variáveis de ambiente.

## Licença
A definir.
