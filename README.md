# FitManager

Sistema Full Stack de gerenciamento de academias. Projeto de portfólio, em desenvolvimento.

> **Status:** base da API pronta (documentação OpenAPI, tratamento padronizado de erros e testes automatizados). As funcionalidades de negócio ainda não foram implementadas. Veja o andamento em [PROGRESS.md](PROGRESS.md).

## Funcionalidades planejadas
- Dashboard administrativo
- Cadastro e gerenciamento de alunos
- Cadastro de exercícios
- Fichas de treino
- Histórico de evolução dos alunos
- Autenticação e controle de acesso

## Tecnologias
**Backend:** Java 21, Spring Boot 4.2.0, Spring Web MVC, Spring Data JPA, Bean Validation, PostgreSQL 16, Maven
**Documentação da API:** springdoc-openapi 3.2.1 (Swagger UI, OpenAPI 3.1)
**Testes:** JUnit 6 (Jupiter), Spring Boot Test, MockMvc, Testcontainers 2
**Infra:** Docker e Docker Compose
**Planejado:** Flyway, Spring Security + JWT, React, TypeScript, Tailwind CSS, deploy em Render e Vercel

## Estrutura
```
fitmanager/        backend Spring Boot (Maven)
frontend/          (futuro) aplicação React
docker-compose.yml PostgreSQL para desenvolvimento
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
A aplicação sobe na porta 8080.

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
