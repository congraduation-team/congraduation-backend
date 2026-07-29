package com.example.congraduation.abeek.timetable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TimetableTermData(
        int termYear,
        int semester,
        List<TimetableOffering> offerings
) {
}
