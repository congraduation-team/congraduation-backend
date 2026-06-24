package com.example.hackathon.dto.sejong;

public class SejongProfileResponseDto {

    private String major;
    private String studentId;
    private String name;
    private Integer gradeLevel;

    public SejongProfileResponseDto() {
    }

    public SejongProfileResponseDto(String major, String studentId, String name, String gradeLevel) {
        this.major = major;
        this.studentId = studentId;
        this.name = name;
        this.gradeLevel = extractNumber(gradeLevel);
    }

    private Integer extractNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String numberOnly = value.replaceAll("[^0-9]", "");
        if (numberOnly.isBlank()) {
            return null;
        }

        return Integer.parseInt(numberOnly);
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(Integer gradeLevel) {
        this.gradeLevel = gradeLevel;
    }
}
