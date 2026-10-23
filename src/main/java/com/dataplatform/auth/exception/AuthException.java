package com.dataplatform.auth.exception;

/**
 * 认证异常
 */
public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
