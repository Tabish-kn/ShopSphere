package com.shopsphere.common.exception;

import org.springframework.http.HttpStatus;

public class InventoryInsufficientException extends BaseException {

    public InventoryInsufficientException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
