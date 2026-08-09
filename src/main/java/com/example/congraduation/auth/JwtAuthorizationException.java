package com.example.congraduation.auth;

public class JwtAuthorizationException extends RuntimeException {

    public JwtAuthorizationException(String message) {
        super(message);
    }
}
