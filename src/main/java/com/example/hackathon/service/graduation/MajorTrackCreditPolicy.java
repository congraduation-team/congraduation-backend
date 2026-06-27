package com.example.hackathon.service.graduation;

public record MajorTrackCreditPolicy(
        int requiredCredits,
        int electiveCredits
) {
    public int totalCredits() {
        return requiredCredits + electiveCredits;
    }
}
