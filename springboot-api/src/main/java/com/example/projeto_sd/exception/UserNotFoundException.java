package com.example.projeto_sd.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exceção lançada quando um usuário não é encontrado no sistema.
 * Mapeada automaticamente para o status HTTP 404 (Not Found).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class UserNotFoundException extends RuntimeException {
    
    /**
     * Constrói uma nova exceção com a mensagem especificada.
     *
     * @param message a mensagem detalhando o motivo da exceção
     */
    public UserNotFoundException(String message) {
        super(message);
    }

    /**
     * Constrói uma nova exceção com a mensagem e causa especificadas.
     *
     * @param message a mensagem detalhando o motivo da exceção
     * @param cause a causa original da exceção
     */
    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
} 