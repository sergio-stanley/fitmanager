# FitManager

Sistema Full Stack de gerenciamento de academias. Projeto de portfólio, em desenvolvimento inicial.

> **Status:** ambiente configurado (PostgreSQL via Docker, aplicação conectando ao banco); nenhuma funcionalidade implementada ainda. Veja o andamento em [PROGRESS.md](PROGRESS.md).

## Funcionalidades planejadas
- Dashboard administrativo
- Cadastro e gerenciamento de alunos
- Cadastro de exercícios
- Fichas de treino
- Histórico de evolução dos alunos
- Autenticação e controle de acesso

## Tecnologias
**Backend:** Java 21, Spring Boot 4.2.0, Spring Data JPA, Bean Validation, PostgreSQL, Maven, JUnit
**Frontend (planejado):** React, TypeScript, Tailwind CSS
**Infra (planejado):** Docker, Swagger/OpenAPI, Render e Vercel

## Estrutura
```
fitmanager/        backend Spring Boot (Maven)
frontend/          (futuro) aplicação React
PROJECT.md         escopo, arquitetura e planejamento
PROGRESS.md        andamento e decisões
CLAUDE.md          instruções de desenvolvimento e colaboração
```

## Pré-requisitos
- JDK 21
- Docker Desktop

## Como executar (Windows/PowerShell)
Na raiz do repositório:
```powershell
Copy-Item .env.example .env        # depois preencha DB_USERNAME e DB_PASSWORD
docker compose up -d               # sobe o PostgreSQL

# carrega o .env na sessão atual (o Spring não lê .env sozinho)
Get-Content .env | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
  $k,$v = $_ -split '=',2; Set-Item "env:$($k.Trim())" $v.Trim()
}

cd fitmanager
.\mvnw.cmd spring-boot:run         # ou .\mvnw.cmd test
```
A aplicação sobe na porta 8080.

## Segurança
Credenciais nunca são versionadas. Use variáveis de ambiente.

## Licença
A definir.
