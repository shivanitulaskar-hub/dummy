package com.consoleadda.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends BusinessException {

    public NotFoundException(String entity, Object id) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", entity + " " + id + " was not found");
    }
}
