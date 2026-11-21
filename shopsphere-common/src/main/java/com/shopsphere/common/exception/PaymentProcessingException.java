package com.shopsphere.common.exception;

import org.springframework.http.HttpStatus;

public class PaymentProcessingException extends BaseException {

    public PaymentProcessingException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }
}
