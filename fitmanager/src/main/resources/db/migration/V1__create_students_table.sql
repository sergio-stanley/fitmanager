-- V1: tabela de alunos (Etapa 3). Modelagem aprovada e API_CONTRACT.md v1.0.0.
-- Migration aplicada nunca é editada: qualquer mudança futura entra como V2, V3...

CREATE TABLE students (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY,
    full_name   VARCHAR(120) NOT NULL,
    email       VARCHAR(160) NOT NULL,
    phone       VARCHAR(11),
    birth_date  DATE         NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_students PRIMARY KEY (id),
    -- O nome é usado pelo backend para identificar a duplicidade de e-mail (409).
    CONSTRAINT uk_students_email UNIQUE (email),
    -- A aplicação grava o e-mail em minúsculas; garantir isso no banco torna a unicidade
    -- efetivamente case-insensitive mesmo se outro cliente escrever direto na tabela.
    CONSTRAINT ck_students_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_students_phone_digits CHECK (phone ~ '^[0-9]{10,11}$'),
    CONSTRAINT ck_students_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
