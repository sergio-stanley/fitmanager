package com.fitmanager.api.exception;

/**
 * Lançada quando um recurso solicitado não existe (ex.: aluno com id inexistente).
 * A mensagem é enviada ao cliente: deve ser descritiva e não conter dados internos.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
