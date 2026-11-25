package com.fitmanager.api.exception;

/**
 * Lançada quando um valor que deve ser único já está em uso (ex.: e-mail de aluno).
 * A mensagem vai para o cliente e NÃO deve conter o valor duplicado.
 * {@code field} indica o campo do formulário afetado (usado no item "errors" do 409).
 */
public class DuplicateResourceException extends RuntimeException {

    private final String field;

    public DuplicateResourceException(String field, String message) {
        super(message);
        this.field = field;
    }

    public DuplicateResourceException(String field, String message, Throwable cause) {
        super(message, cause);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
