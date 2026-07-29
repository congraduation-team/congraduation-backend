package com.example.congraduation.abeek.timetable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TimetableOffering(
        String college,
        String openingDepartment,
        String courseCode,
        String section,
        String courseName,
        String category,
        String gradeYear,
        Double credits,
        String classType,
        String schedule,
        String room,
        String professor,
        String hostDepartment
) {
}
