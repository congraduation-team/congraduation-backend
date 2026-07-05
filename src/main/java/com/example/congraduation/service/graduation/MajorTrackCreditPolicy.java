package com.example.congraduation.service.graduation;

public record MajorTrackCreditPolicy(
        int requiredCredits,
        int electiveCredits
) {
    public int totalCredits() {
        return requiredCredits + electiveCredits;
    }
}
