# API_CONTRACT.md — Contrato da API do FitManager

| | |
|---|---|
| **Versão do contrato** | 1.1.0 |
| **Status** | Alunos (seção 2): **implementado** no backend (Etapa 3). Exercícios (seção 6): aprovado para desenvolvimento com mocks, **backend ainda não implementado** (Etapa 4). |
| **Responsável** | Agente backend (único que edita este arquivo) |
| **Última atualização** | 2026-11-25 |

> **Regra de mudança:** este contrato só muda por PR, com aumento de versão e registro no [Histórico de alterações](#histórico-de-alterações). Quando o backend estiver implementado, a especificação gerada em `/v3/api-docs` também ficará disponível. Se os dois divergirem, é um bug: reporte.

---

## 1. Convenções gerais

| Item | Valor |
|---|---|
| URL base (desenvolvimento) | `http://localhost:8080` |
| Prefixo dos recursos | `/api` |
| Formato | JSON (UTF-8) |
| Requisições com corpo | Header `Content-Type: application/json` (outro tipo → **415**) |
| Respostas de sucesso | `Content-Type: application/json` |
| Respostas de erro | `Content-Type: application/problem+json` ([RFC 9457](#3-formato-de-erros-problemdetail)) |
| Nomes de campos | `camelCase` |
| Datas sem hora (`birthDate`) | `"yyyy-MM-dd"`, ex.: `"1998-04-15"` |
| Data e hora (`createdAt`, `updatedAt`) | ISO-8601 em **UTC**, terminando em `Z`, ex.: `"2026-11-24T20:15:30.123456Z"`. **O número de casas decimais pode variar**: use `new Date(valor)` e não compare strings. |
| Campos opcionais sem valor | Sempre presentes na resposta, com valor `null` (nunca omitidos) |
| Autenticação | Nenhuma por enquanto (entrará na Etapa 7, e este contrato será atualizado) |

### CORS (desenvolvimento)
- Origem permitida: `http://localhost:5173` (Vite).
- Métodos: `GET`, `POST`, `PUT`, `PATCH`, `OPTIONS`.
- Header de requisição permitido: `Content-Type` (necessário para o *preflight* de `POST`, `PUT` e `PATCH` com JSON).
- Header exposto ao navegador: **`Location`** (necessário para ler o id do recurso criado).

---

## 2. Alunos (`/api/students`)

### 2.1 Modelo `Student` (resposta)

```json
{
  "id": 1,
  "fullName": "Ana Souza",
  "email": "ana.souza@email.com",
  "phone": "11987654321",
  "birthDate": "1998-04-15",
  "status": "ACTIVE",
  "createdAt": "2026-11-24T20:15:30.123456Z",
  "updatedAt": "2026-11-24T20:15:30.123456Z"
}
```

| Campo | Tipo | Nulo? | Descrição |
|---|---|---|---|
| `id` | inteiro | não | Gerado pelo servidor |
| `fullName` | string | não | Já normalizado (sem espaços nas pontas e sem espaços repetidos) |
| `email` | string | não | Já normalizado (minúsculas, sem espaços) |
| `phone` | string | **sim** | Apenas dígitos (10 ou 11), ou `null` |
| `birthDate` | string (`yyyy-MM-dd`) | não | |
| `status` | `"ACTIVE"` \| `"INACTIVE"` | não | |
| `createdAt` | string (ISO-8601 UTC) | não | Gerado pelo servidor |
| `updatedAt` | string (ISO-8601 UTC) | não | Atualizado pelo servidor a cada alteração |

### 2.2 Corpo de criação e edição `StudentRequest`

```json
{
  "fullName": "Ana Souza",
  "email": "Ana.Souza@Email.com",
  "phone": "11987654321",
  "birthDate": "1998-04-15"
}
```

**Normalização (feita pelo servidor ANTES da validação; as regras abaixo valem para o valor já normalizado):**

| Campo | Normalização |
|---|---|
| `fullName` | Remove espaços nas pontas e reduz espaços internos repetidos a um só (`"  Ana   Souza "` → `"Ana Souza"`) |
| `email` | Remove espaços nas pontas e converte para minúsculas |
| `phone` | Remove espaços nas pontas. **Ausente, `null`, `""` ou só espaços → `null` (sem telefone).** Não remove máscara: `"(11) 98765-4321"` é **inválido**. O frontend deve enviar apenas dígitos. |

**Validação:**

| Campo | Obrigatório | Regra | Mensagem (`errors[].message`) |
|---|---|---|---|
| `fullName` | sim | não vazio | `O nome é obrigatório` |
| | | 3 a 120 caracteres | `O nome deve ter entre 3 e 120 caracteres` |
| `email` | sim | não vazio | `O e-mail é obrigatório` |
| | | formato de e-mail | `O e-mail deve ser válido` |
| | | até 160 caracteres | `O e-mail deve ter no máximo 160 caracteres` |
| | | único entre todos os alunos (ativos e inativos) | → **409** (ver 2.9) |
| `phone` | não | 10 ou 11 dígitos, só números | `O telefone deve conter 10 ou 11 dígitos, apenas números` |
| `birthDate` | sim | presente | `A data de nascimento é obrigatória` |
| | | anterior à data atual do servidor | `A data de nascimento deve estar no passado` |

Observações:
- Um mesmo campo pode aparecer **mais de uma vez** em `errors` (ex.: `fullName` vazio pode gerar as duas mensagens de nome).
- A ordem dos itens em `errors` **não é garantida**.
- `birthDate` em formato diferente de `yyyy-MM-dd` (ex.: `"15/04/1998"`) torna o JSON ilegível → **400 sem `errors`** (ver 3.3).
- O backend é a fonte da verdade. A validação no frontend é recomendada para UX, mas não substitui a do servidor.

### 2.3 Endpoints

| Método | Rota | Corpo | Sucesso | Erros possíveis |
|---|---|---|---|---|
| `POST` | `/api/students` | `StudentRequest` | **201** + `Student` + header `Location` | 400, 409, 415 |
| `GET` | `/api/students/{id}` | — | **200** + `Student` | 400 (id não numérico), 404 |
| `GET` | `/api/students` | — | **200** + `Page<Student>` | 400 |
| `PUT` | `/api/students/{id}` | `StudentRequest` | **200** + `Student` | 400, 404, 409, 415 |
| `PATCH` | `/api/students/{id}/status` | `StudentStatusRequest` | **200** + `Student` | 400, 404, 415 |

Não existe `DELETE`: alunos são **desativados** (`status: "INACTIVE"`) para preservar o histórico. Um aluno inativo continua acessível por `GET`.

### 2.4 Criar aluno — `POST /api/students`

Requisição: corpo `StudentRequest` (2.2). Novo aluno sempre nasce com `status: "ACTIVE"`.

Resposta **201 Created**:
```
Location: /api/students/1
```
```json
{
  "id": 1,
  "fullName": "Ana Souza",
  "email": "ana.souza@email.com",
  "phone": "11987654321",
  "birthDate": "1998-04-15",
  "status": "ACTIVE",
  "createdAt": "2026-11-24T20:15:30.123456Z",
  "updatedAt": "2026-11-24T20:15:30.123456Z"
}
```

### 2.5 Buscar por id — `GET /api/students/{id}`

Resposta **200**: `Student`. Id inexistente → **404** (ver 3.2).

### 2.6 Listar / pesquisar — `GET /api/students`

**Parâmetros de consulta (todos opcionais):**

| Parâmetro | Tipo | Padrão | Regra |
|---|---|---|---|
| `search` | string | — | Espaços nas pontas são removidos; vazio = sem filtro. Procura o trecho em `fullName` **ou** `email`, ignorando maiúsculas/minúsculas. Máximo de 100 caracteres (acima → 400). |
| `status` | `ACTIVE` \| `INACTIVE` | — (todos) | Valor diferente → 400 |
| `page` | inteiro ≥ 0 | `0` | Página começa em **0** |
| `size` | inteiro 1–100 | `20` | Acima de 100 é reduzido para 100 |
| `sort` | `campo,direção` | `fullName,asc` | Campos permitidos: **`fullName`, `email`, `birthDate`, `createdAt`**. Direção: `asc` ou `desc` (padrão `asc`). Pode repetir o parâmetro para ordenar por mais de um campo. Campo fora da lista → **400**. |

**Desempate estável:** o servidor sempre acrescenta `id,asc` como último critério de ordenação. Dois alunos com o mesmo nome aparecem sempre na mesma ordem, e nenhum registro se repete ou some ao trocar de página (desde que os dados não mudem entre as requisições).

Página além da última → **200** com `content: []`.

**Valores fora da faixa ou não numéricos** (comportamento confirmado por testes automatizados): `page` negativo ou não numérico → `0`; `size` menor que 1 ou não numérico → `20`; `size` acima de 100 → `100`. Nenhum desses casos gera erro.

Exemplo: `GET /api/students?search=ana&status=ACTIVE&page=0&size=20&sort=fullName,asc`

Resposta **200**:
```json
{
  "content": [
    {
      "id": 1,
      "fullName": "Ana Souza",
      "email": "ana.souza@email.com",
      "phone": "11987654321",
      "birthDate": "1998-04-15",
      "status": "ACTIVE",
      "createdAt": "2026-11-24T20:15:30.123456Z",
      "updatedAt": "2026-11-24T20:15:30.123456Z"
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 2.7 Editar aluno — `PUT /api/students/{id}`

- Corpo `StudentRequest` completo (substituição): todos os campos editáveis são enviados. **Omitir `phone` ou enviá-lo vazio remove o telefone.**
- Não altera `status` (use 2.8). `createdAt` não muda; `updatedAt` é atualizado.
- O aluno pode manter o próprio e-mail. E-mail de **outro** aluno → 409.

Resposta **200**: `Student` atualizado.

### 2.8 Alterar status — `PATCH /api/students/{id}/status`

```json
{ "status": "INACTIVE" }
```
| Campo | Obrigatório | Valores | Mensagem |
|---|---|---|---|
| `status` | sim | `ACTIVE`, `INACTIVE` | `O status é obrigatório` (ausente/nulo). Valor desconhecido → 400 sem `errors` (JSON ilegível). |

Resposta **200**: `Student` atualizado. A operação é **idempotente**: enviar o mesmo status atual também retorna 200.

### 2.9 E-mail duplicado — **409 Conflict**

Ocorre em `POST` e `PUT` quando o e-mail (já normalizado) pertence a outro aluno, ativo ou inativo. **O e-mail não é repetido na mensagem.** O item em `errors` permite exibir o erro junto ao campo do formulário.

```json
{
  "type": "about:blank",
  "title": "Conflito",
  "status": 409,
  "detail": "Já existe um aluno cadastrado com este e-mail.",
  "instance": "/api/students",
  "errors": [
    { "field": "email", "message": "Já existe um aluno cadastrado com este e-mail." }
  ]
}
```

---

## 3. Formato de erros (ProblemDetail)

Todas as respostas de erro seguem a RFC 9457, com `Content-Type: application/problem+json`.

| Campo | Tipo | Sempre presente? | Descrição |
|---|---|---|---|
| `type` | string | sim | Hoje sempre `"about:blank"` |
| `title` | string | sim | Resumo do tipo de erro |
| `status` | inteiro | sim | Igual ao status HTTP |
| `detail` | string | ver 3.3 | Explicação |
| `instance` | string | sim | Caminho da requisição, **sem query string** |
| `errors` | `FieldViolation[]` | **não** | Presente apenas em 400 de validação e nos 409 de duplicidade (e-mail de aluno, nome de exercício) |

### 3.1 Validação — 400
```json
{
  "type": "about:blank",
  "title": "Dados inválidos",
  "status": 400,
  "detail": "Um ou mais campos possuem valores inválidos.",
  "instance": "/api/students",
  "errors": [
    { "field": "fullName", "message": "O nome deve ter entre 3 e 120 caracteres" },
    { "field": "birthDate", "message": "A data de nascimento deve estar no passado" }
  ]
}
```

### 3.2 Não encontrado — 404
```json
{
  "type": "about:blank",
  "title": "Recurso não encontrado",
  "status": 404,
  "detail": "Aluno não encontrado.",
  "instance": "/api/students/999"
}
```

### 3.3 Tabela de erros e como exibi-los

| Status | Quando | `title` | `detail` exibível ao usuário? | `errors` |
|---|---|---|---|---|
| 400 | Validação de campos | `Dados inválidos` | sim | **sim** |
| 400 | JSON malformado, data em formato errado, enum desconhecido, id não numérico, parâmetro inválido, campo de ordenação não permitido | variável | **não** — pode vir em inglês e técnico; exiba mensagem genérica | não |
| 404 | Aluno ou exercício inexistente | `Recurso não encontrado` | sim | não |
| 404 | Rota inexistente | variável | não | não |
| 405 | Método HTTP não suportado | variável | não | não |
| 409 | E-mail de aluno duplicado | `Conflito` | sim | **sim** (`email`) |
| 409 | Nome de exercício duplicado | `Conflito` | sim | **sim** (`name`) |
| 415 | `Content-Type` diferente de JSON | variável | não | não |
| 500 | Erro inesperado | `Erro interno` | sim (mensagem genérica) | não |

**Regra prática para o frontend:** se existir `errors`, mostre cada mensagem junto ao campo correspondente. Senão, use `detail` apenas nos casos marcados como "sim"; nos demais, mostre uma mensagem genérica (ex.: "Não foi possível concluir a operação.").

---

## 4. Tipos TypeScript

Tipos comuns e de alunos abaixo. Os tipos de exercícios estão na [seção 6.9](#69-tipos-typescript-de-exercícios) e reutilizam `Page<T>`, `PageMetadata`, `SortDirection`, `FieldViolation` e `ProblemDetail` daqui.

```ts
// Tipos do contrato v1.0.0 — manter sincronizado com API_CONTRACT.md

export type StudentStatus = 'ACTIVE' | 'INACTIVE';

export interface Student {
  id: number;
  fullName: string;
  email: string;
  phone: string | null;
  birthDate: string; // 'yyyy-MM-dd'
  status: StudentStatus;
  createdAt: string; // ISO-8601 UTC, ex.: '2026-11-24T20:15:30.123456Z'
  updatedAt: string; // ISO-8601 UTC
}

/** Corpo de POST /api/students e PUT /api/students/{id}. */
export interface StudentRequest {
  fullName: string;
  email: string;
  phone?: string | null; // só dígitos (10 ou 11); ausente/null/'' = sem telefone
  birthDate: string; // 'yyyy-MM-dd'
}

/** Corpo de PATCH /api/students/{id}/status. */
export interface StudentStatusRequest {
  status: StudentStatus;
}

export interface PageMetadata {
  size: number;
  number: number; // começa em 0
  totalElements: number;
  totalPages: number;
}

export interface Page<T> {
  content: T[];
  page: PageMetadata;
}

export type StudentSortField = 'fullName' | 'email' | 'birthDate' | 'createdAt';
export type SortDirection = 'asc' | 'desc';
export type StudentSort = `${StudentSortField},${SortDirection}`;

/** Query string de GET /api/students. */
export interface StudentListParams {
  search?: string; // até 100 caracteres
  status?: StudentStatus;
  page?: number; // >= 0, padrão 0
  size?: number; // 1..100, padrão 20
  sort?: StudentSort | StudentSort[]; // padrão 'fullName,asc'; o servidor desempata por id
}

export interface FieldViolation {
  field: string;
  message: string;
}

export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance: string;
  errors?: FieldViolation[];
}
```

---

## 5. Dados para mocks

```json
[
  {
    "id": 1,
    "fullName": "Ana Souza",
    "email": "ana.souza@email.com",
    "phone": "11987654321",
    "birthDate": "1998-04-15",
    "status": "ACTIVE",
    "createdAt": "2026-11-20T13:05:12.481Z",
    "updatedAt": "2026-11-20T13:05:12.481Z"
  },
  {
    "id": 2,
    "fullName": "Bruno Lima",
    "email": "bruno.lima@email.com",
    "phone": null,
    "birthDate": "1985-09-02",
    "status": "ACTIVE",
    "createdAt": "2026-11-21T09:40:03.9Z",
    "updatedAt": "2026-11-23T18:22:47.105233Z"
  },
  {
    "id": 3,
    "fullName": "Carla Mendes",
    "email": "carla.mendes@email.com",
    "phone": "2133334444",
    "birthDate": "2001-12-30",
    "status": "INACTIVE",
    "createdAt": "2026-11-22T16:11:00Z",
    "updatedAt": "2026-11-24T10:00:59.3Z"
  }
]
```
Os timestamps têm precisões diferentes de propósito, para o mock refletir a observação da seção 1.

---

## 6. Exercícios (`/api/exercises`)

Catálogo de exercícios da academia. É a base para as fichas e os treinos das próximas etapas: qualquer exercício ativo do catálogo poderá ser usado em qualquer modalidade de treino, sem restrição por grupo muscular ou divisão de treino. As convenções gerais (seção 1), o formato de erros (seção 3) e o formato de página (seção 2.6) valem igualmente aqui.

### 6.1 Grupos musculares (`MuscleGroup`)

Cada exercício tem **um** grupo muscular **principal**, usado para organizar e filtrar o catálogo. Ele não restringe como o exercício será usado nos treinos.

| Valor (API) | Rótulo sugerido (pt-BR) |
|---|---|
| `CHEST` | Peito |
| `BACK` | Costas |
| `SHOULDERS` | Ombros |
| `BICEPS` | Bíceps |
| `TRICEPS` | Tríceps |
| `FOREARMS` | Antebraços |
| `ABS` | Abdômen |
| `QUADRICEPS` | Quadríceps |
| `HAMSTRINGS` | Posteriores de coxa |
| `GLUTES` | Glúteos |
| `CALVES` | Panturrilhas |
| `FULL_BODY` | Corpo inteiro |
| `CARDIO` | Cardio |
| `OTHER` | Outro (mobilidade, alongamento etc.) |

- A API usa sempre o **valor** em maiúsculas. Os rótulos são responsabilidade do frontend; a tabela acima é a sugestão oficial.

**Evolução da lista e compatibilidade com o TypeScript:**
- Incluir um novo valor é uma mudança **aditiva** no JSON (nova versão *minor* do contrato, com aviso prévio por PR). **Remover ou renomear** um valor é uma mudança incompatível (nova versão *major*) e não está prevista.
- O tipo `MuscleGroup` (seção 6.9) é uma **lista fechada** verificada apenas em tempo de compilação. Se o backend passar a devolver um valor novo antes de o frontend atualizar seus tipos, o código continuará compilando, mas receberá em tempo de execução um valor fora do tipo. Por isso, ao **ler respostas**, o frontend deve:
  1. obter rótulos sempre por `muscleGroupLabel(valor)` (seção 6.9), que devolve o próprio valor quando ele é desconhecido;
  2. não usar `switch`/mapeamentos exaustivos sem um caso padrão;
  3. não descartar nem rejeitar itens com valor desconhecido (exibi-los normalmente, com o valor bruto como rótulo).
- Ao **enviar requisições**, o frontend só usa os valores que conhece; o backend rejeita valores fora da lista com 400.
- Ao receber uma nova versão *minor* que inclua valores, o frontend atualiza `MuscleGroup` e `MUSCLE_GROUP_LABELS`.

### 6.2 Modelo `Exercise` (resposta)

```json
{
  "id": 1,
  "name": "Supino reto com barra",
  "muscleGroup": "CHEST",
  "description": "Deitado no banco, descer a barra até o peito e empurrar até estender os braços.",
  "status": "ACTIVE",
  "createdAt": "2026-11-25T14:02:11.482913Z",
  "updatedAt": "2026-11-25T14:02:11.482913Z"
}
```

| Campo | Tipo | Nulo? | Descrição |
|---|---|---|---|
| `id` | inteiro | não | Gerado pelo servidor |
| `name` | string | não | Já normalizado (sem espaços nas pontas e sem espaços repetidos). **A grafia original é preservada** (maiúsculas e acentos). |
| `muscleGroup` | `MuscleGroup` | não | Grupo muscular principal |
| `description` | string | **sim** | Instruções de execução, ou `null` |
| `status` | `"ACTIVE"` \| `"INACTIVE"` | não | |
| `createdAt` | string (ISO-8601 UTC) | não | Gerado pelo servidor |
| `updatedAt` | string (ISO-8601 UTC) | não | Atualizado pelo servidor a cada alteração |

### 6.3 Corpo de criação e edição `ExerciseRequest`

```json
{
  "name": "  Supino   reto com barra ",
  "muscleGroup": "CHEST",
  "description": "Deitado no banco, descer a barra até o peito e empurrar até estender os braços."
}
```

**Normalização (feita pelo servidor ANTES da validação):**

| Campo | Normalização |
|---|---|
| `name` | Remove espaços nas pontas e reduz espaços internos repetidos a um só (`"  Supino   reto "` → `"Supino reto"`). Maiúsculas e acentos são mantidos. |
| `description` | Remove espaços nas pontas. **Ausente, `null`, `""` ou só espaços → `null`.** Quebras de linha internas são preservadas. |

**Validação:**

| Campo | Obrigatório | Regra | Mensagem (`errors[].message`) |
|---|---|---|---|
| `name` | sim | não vazio | `O nome é obrigatório` |
| | | 3 a 100 caracteres | `O nome deve ter entre 3 e 100 caracteres` |
| | | único no catálogo (ativos e inativos), **sem diferenciar maiúsculas** | → **409** (ver 6.8) |
| `muscleGroup` | sim | presente | `O grupo muscular é obrigatório` |
| | | um dos valores da seção 6.1 | valor desconhecido → **400 sem `errors`** (JSON ilegível) |
| `description` | não | até 500 caracteres | `A descrição deve ter no máximo 500 caracteres` |

Observações:
- A unicidade compara o nome **normalizado e sem diferenciar maiúsculas**: `"Supino Reto"`, `"supino reto"` e `"  SUPINO   RETO "` são o mesmo exercício. **Acentos diferenciam**: `"Rosca alternada"` e `"Rosca alternáda"` são nomes distintos.
- Um mesmo campo pode aparecer mais de uma vez em `errors`; a ordem dos itens não é garantida (como na seção 2.2).

### 6.4 Endpoints

| Método | Rota | Corpo | Sucesso | Erros possíveis |
|---|---|---|---|---|
| `POST` | `/api/exercises` | `ExerciseRequest` | **201** + `Exercise` + header `Location` | 400, 409, 415 |
| `GET` | `/api/exercises/{id}` | — | **200** + `Exercise` | 400 (id não numérico), 404 |
| `GET` | `/api/exercises` | — | **200** + `Page<Exercise>` | 400 |
| `PUT` | `/api/exercises/{id}` | `ExerciseRequest` | **200** + `Exercise` | 400, 404, 409, 415 |
| `PATCH` | `/api/exercises/{id}/status` | `ExerciseStatusRequest` | **200** + `Exercise` | 400, 404, 415 |

Não existe `DELETE`: exercícios são **desativados** (`status: "INACTIVE"`), para que fichas e históricos que os usam continuem íntegros. Um exercício inativo continua acessível por `GET` e aparece na listagem quando o filtro de status não é informado.

### 6.5 Criar exercício — `POST /api/exercises`

Corpo `ExerciseRequest` (6.3). Novo exercício sempre nasce com `status: "ACTIVE"`.

Resposta **201 Created**, com header `Location: /api/exercises/1` e o `Exercise` criado no corpo (exemplo em 6.2).

### 6.6 Listar / pesquisar — `GET /api/exercises`

**Parâmetros de consulta (todos opcionais):**

| Parâmetro | Tipo | Padrão | Regra |
|---|---|---|---|
| `search` | string | — | Espaços nas pontas são removidos; vazio = sem filtro. Procura o trecho em `name`, sem diferenciar maiúsculas. Busca literal (`%` e `_` não são curingas). Máximo de 100 caracteres (acima → 400). |
| `muscleGroup` | `MuscleGroup` | — (todos) | Valor diferente da seção 6.1 → 400 |
| `status` | `ACTIVE` \| `INACTIVE` | — (todos) | Valor diferente → 400 |
| `page` | inteiro ≥ 0 | `0` | Mesmas regras da seção 2.6 |
| `size` | inteiro 1–100 | `20` | Mesmas regras da seção 2.6 |
| `sort` | `campo,direção` | `name,asc` | Campos permitidos: **`name`, `muscleGroup`, `createdAt`**. Direção `asc` ou `desc`. Pode repetir. Campo fora da lista → **400**. |

- **Desempate estável:** o servidor sempre acrescenta `id,asc` como último critério.
- Ordenar por `muscleGroup` usa o **valor** do enum (ordem alfabética de `ABS`, `BACK`, `BICEPS`...), não o rótulo em português. Para exibir agrupado por rótulo, ordene no frontend.
- Página além da última → **200** com `content: []`.

Exemplo: `GET /api/exercises?search=supino&muscleGroup=CHEST&status=ACTIVE&page=0&size=20&sort=name,asc`

Resposta **200**:
```json
{
  "content": [
    {
      "id": 1,
      "name": "Supino reto com barra",
      "muscleGroup": "CHEST",
      "description": "Deitado no banco, descer a barra até o peito e empurrar até estender os braços.",
      "status": "ACTIVE",
      "createdAt": "2026-11-25T14:02:11.482913Z",
      "updatedAt": "2026-11-25T14:02:11.482913Z"
    }
  ],
  "page": { "size": 20, "number": 0, "totalElements": 1, "totalPages": 1 }
}
```

### 6.7 Editar e alterar status

**`PUT /api/exercises/{id}`** — corpo `ExerciseRequest` completo (substituição). Omitir `description` ou enviá-la vazia **remove a descrição**. Não altera `status`; `createdAt` não muda; `updatedAt` é atualizado. O exercício pode manter o próprio nome (inclusive mudando só maiúsculas, ex.: `"supino reto"` → `"Supino Reto"`). Nome de **outro** exercício → 409. Resposta **200** com o `Exercise` atualizado.

**`PATCH /api/exercises/{id}/status`**
```json
{ "status": "INACTIVE" }
```
| Campo | Obrigatório | Valores | Mensagem |
|---|---|---|---|
| `status` | sim | `ACTIVE`, `INACTIVE` | `O status é obrigatório` (ausente/nulo). Valor desconhecido → 400 sem `errors`. |

Resposta **200** com o `Exercise` atualizado. **Idempotente**: repetir o status atual retorna 200 sem alterar `updatedAt`.

### 6.8 Nome duplicado — **409 Conflict**

Em `POST` e `PUT`, quando o nome normalizado (sem diferenciar maiúsculas) pertence a outro exercício, ativo ou inativo. O nome não é repetido na mensagem.

```json
{
  "type": "about:blank",
  "title": "Conflito",
  "status": 409,
  "detail": "Já existe um exercício cadastrado com este nome.",
  "instance": "/api/exercises",
  "errors": [
    { "field": "name", "message": "Já existe um exercício cadastrado com este nome." }
  ]
}
```

Exercício inexistente → **404** com `"detail": "Exercício não encontrado."` (formato da seção 3.2).

### 6.9 Tipos TypeScript de exercícios

```ts
// Tipos do contrato v1.1.0 — reutilizam Page<T>, SortDirection, FieldViolation e ProblemDetail (seção 4).

export type MuscleGroup =
  | 'CHEST' | 'BACK' | 'SHOULDERS' | 'BICEPS' | 'TRICEPS' | 'FOREARMS' | 'ABS'
  | 'QUADRICEPS' | 'HAMSTRINGS' | 'GLUTES' | 'CALVES' | 'FULL_BODY' | 'CARDIO' | 'OTHER';

/** Rótulos sugeridos (seção 6.1). */
export const MUSCLE_GROUP_LABELS: Record<MuscleGroup, string> = {
  CHEST: 'Peito',
  BACK: 'Costas',
  SHOULDERS: 'Ombros',
  BICEPS: 'Bíceps',
  TRICEPS: 'Tríceps',
  FOREARMS: 'Antebraços',
  ABS: 'Abdômen',
  QUADRICEPS: 'Quadríceps',
  HAMSTRINGS: 'Posteriores de coxa',
  GLUTES: 'Glúteos',
  CALVES: 'Panturrilhas',
  FULL_BODY: 'Corpo inteiro',
  CARDIO: 'Cardio',
  OTHER: 'Outro',
};

/**
 * Rótulo seguro para valores vindos da API: se o valor não estiver na lista conhecida
 * (ex.: incluído numa versão futura do contrato), devolve o próprio valor (seção 6.1).
 */
export function muscleGroupLabel(value: string): string {
  // hasOwnProperty (e não o operador "in"): ignora propriedades herdadas como "toString".
  return Object.prototype.hasOwnProperty.call(MUSCLE_GROUP_LABELS, value)
    ? MUSCLE_GROUP_LABELS[value as MuscleGroup]
    : value;
}

export type ExerciseStatus = 'ACTIVE' | 'INACTIVE';

export interface Exercise {
  id: number;
  name: string;
  muscleGroup: MuscleGroup;
  description: string | null;
  status: ExerciseStatus;
  createdAt: string; // ISO-8601 UTC
  updatedAt: string; // ISO-8601 UTC
}

/** Corpo de POST /api/exercises e PUT /api/exercises/{id}. */
export interface ExerciseRequest {
  name: string;
  muscleGroup: MuscleGroup;
  description?: string | null; // ausente/null/'' = sem descrição
}

/** Corpo de PATCH /api/exercises/{id}/status. */
export interface ExerciseStatusRequest {
  status: ExerciseStatus;
}

export type ExerciseSortField = 'name' | 'muscleGroup' | 'createdAt';
export type ExerciseSort = `${ExerciseSortField},${SortDirection}`;

/** Query string de GET /api/exercises. */
export interface ExerciseListParams {
  search?: string; // até 100 caracteres
  muscleGroup?: MuscleGroup;
  status?: ExerciseStatus;
  page?: number; // >= 0, padrão 0
  size?: number; // 1..100, padrão 20
  sort?: ExerciseSort | ExerciseSort[]; // padrão 'name,asc'; o servidor desempata por id
}
```

### 6.10 Dados para mocks

```json
[
  {
    "id": 1,
    "name": "Supino reto com barra",
    "muscleGroup": "CHEST",
    "description": "Deitado no banco, descer a barra até o peito e empurrar até estender os braços.",
    "status": "ACTIVE",
    "createdAt": "2026-11-25T14:02:11.482913Z",
    "updatedAt": "2026-11-25T14:02:11.482913Z"
  },
  {
    "id": 2,
    "name": "Agachamento livre",
    "muscleGroup": "QUADRICEPS",
    "description": null,
    "status": "ACTIVE",
    "createdAt": "2026-11-25T14:05:40.2Z",
    "updatedAt": "2026-11-25T14:05:40.2Z"
  },
  {
    "id": 3,
    "name": "Puxada frontal",
    "muscleGroup": "BACK",
    "description": "Puxar a barra até a altura do queixo, mantendo o tronco estável.",
    "status": "ACTIVE",
    "createdAt": "2026-11-25T14:07:03Z",
    "updatedAt": "2026-11-25T15:30:18.77Z"
  },
  {
    "id": 4,
    "name": "Esteira",
    "muscleGroup": "CARDIO",
    "description": null,
    "status": "INACTIVE",
    "createdAt": "2026-11-25T14:09:55.123Z",
    "updatedAt": "2026-11-25T16:44:02.9123Z"
  }
]
```

---

## Histórico de alterações
| Versão | Data | Alteração |
|---|---|---|
| 1.0.0 | 2026-11-24 | Versão inicial: recurso `students` (criar, buscar, listar/pesquisar, editar, alterar status), formato de erros e tipos TypeScript. |
| 1.1.0 | 2026-11-25 | Novo recurso `exercises` (seção 6): catálogo com um grupo muscular principal por exercício, 14 valores de `MuscleGroup`, regras de compatibilidade do enum com o TypeScript e `muscleGroupLabel` com fallback. Tabela de erros (3.3) com o 409 de nome e o 404 de exercício. Seção 2.6: nota "a confirmar" substituída pelo comportamento confirmado em testes (sem mudança de comportamento). Nenhuma alteração incompatível na seção de alunos. |
