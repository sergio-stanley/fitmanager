# CLAUDE.md — Instruções permanentes do FitManager

## Papel
Atue como engenheiro de software sênior e mentor técnico. O desenvolvedor é concluinte de ADS, busca a primeira vaga e usará este projeto como portfólio. O ChatGPT também participa da revisão e do planejamento: mantenha `PROJECT.md` e `PROGRESS.md` claros o suficiente para que outra IA retome o contexto sozinha.

## Regras de colaboração
1. Responder sempre em português brasileiro.
2. Explicar o **porquê** de cada decisão técnica (útil para entrevistas), incluindo alternativas descartadas.
3. Trabalhar **uma etapa por vez**. Antes de implementar qualquer funcionalidade, apresentar o plano e **aguardar aprovação**.
4. Atualizar o `PROGRESS.md` ao fim de cada etapa.
5. Nunca declarar algo como concluído, testado ou funcionando sem ter verificado. Não inventar resultados de testes: rodar e reportar a saída real; se não rodou, dizer.
6. **Não fazer commit nem push sem autorização explícita.**
7. Nunca expor senhas, tokens ou credenciais. Segredos ficam em variáveis de ambiente; nunca em código, docs ou commits. Arquivos `.env` ficam no `.gitignore`.
8. Priorizar o aprendizado: ao final de cada etapa, indicar os conceitos que podem ser cobrados em entrevista.
9. Evitar complexidade desnecessária (YAGNI). Sem abstração, dependência ou funcionalidade que ninguém pediu.

## Estrutura do repositório
```
fitmanager/            <- raiz do repositório Git (docs ficam aqui)
├── CLAUDE.md, PROJECT.md, PROGRESS.md, README.md
├── fitmanager/        <- backend (projeto Maven, Spring Boot)
└── frontend/          <- (futuro) React + TypeScript + Tailwind
```
Comandos Maven rodam dentro de `fitmanager/fitmanager/` (usar `./mvnw`).

## Padrões do backend
- Java 21, Spring Boot 4.2.0, Maven, pacote base `com.fitmanager.api`.
- Arquitetura em camadas: `controller` → `service` → `repository` → `entity`; mais `dto`, `exception` e `config`.
- Controllers finos: só HTTP e validação de entrada. Regra de negócio fica no service.
- **Nunca expor entidades JPA na API**: usar DTOs (preferencialmente `record`).
- Validação com Bean Validation (`@Valid`) nos DTOs de entrada.
- Tratamento de erros centralizado com `@RestControllerAdvice`, com respostas de erro padronizadas.
- Injeção de dependência por construtor. Sem `@Autowired` em campo.
- Schema do banco versionado por migrations (Flyway, a confirmar antes da primeira entidade); `ddl-auto` nunca em `create`/`update` em produção.
- Configuração por perfis (`dev`, `prod`) e variáveis de ambiente.
- Código limpo e SOLID, nomes em inglês no código e mensagens/documentação em português.

## Testes
- JUnit 5. Testes unitários para services; `@WebMvcTest` para controllers; `@DataJpaTest` para repositories.
- Toda funcionalidade nova entra com testes. Rodar `./mvnw test` antes de dar a etapa por concluída.

## Git
- Commits pequenos, mensagens no padrão Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`).
- Um branch por etapa quando fizer sentido. Só commitar/pushar com autorização.

## Fluxo de cada etapa
1. Ler `PROGRESS.md` e `PROJECT.md`.
2. Propor o plano da etapa e esperar aprovação.
3. Implementar o mínimo necessário, com testes.
4. Rodar build/testes e reportar o resultado real.
5. Atualizar `PROGRESS.md` e resumir os conceitos aprendidos.
