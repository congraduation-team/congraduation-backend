package com.example.hackathon.exception;

public class TranscriptNotFoundException extends RuntimeException {

    public TranscriptNotFoundException(String message) {
        super(message);
    }
}
