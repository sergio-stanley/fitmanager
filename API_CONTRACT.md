# API_CONTRACT.md — Contrato da API do FitManager

| | |
|---|---|
| **Versão do contrato** | 1.0.0 |
| **Status** | Aprovado para desenvolvimento com mocks. **Backend ainda não implementado** (Etapa 3). |
| **Responsável** | Agente backend (único que edita este arquivo) |
| **Última atualização** | 2026-11-24 |

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

> A confirmar nos testes da implementação (Passo 5): o comportamento exato para `page` negativo e `size` menor que 1 ou não numérico. O padrão do Spring é substituir pelo valor padrão. Se a implementação for diferente, este contrato será atualizado.

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
| `errors` | `FieldViolation[]` | **não** | Presente apenas em 400 de validação e no 409 de e-mail |

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
| 404 | Aluno inexistente | `Recurso não encontrado` | sim | não |
| 404 | Rota inexistente | variável | não | não |
| 405 | Método HTTP não suportado | variável | não | não |
| 409 | E-mail duplicado | `Conflito` | sim | **sim** (`email`) |
| 415 | `Content-Type` diferente de JSON | variável | não | não |
| 500 | Erro inesperado | `Erro interno` | sim (mensagem genérica) | não |

**Regra prática para o frontend:** se existir `errors`, mostre cada mensagem junto ao campo correspondente. Senão, use `detail` apenas nos casos marcados como "sim"; nos demais, mostre uma mensagem genérica (ex.: "Não foi possível concluir a operação.").

---

## 4. Tipos TypeScript

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

## Histórico de alterações
| Versão | Data | Alteração |
|---|---|---|
| 1.0.0 | 2026-11-24 | Versão inicial: recurso `students` (criar, buscar, listar/pesquisar, editar, alterar status), formato de erros e tipos TypeScript. |
