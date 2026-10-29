package com.example.congraduation.dto.sejong;

import com.example.congraduation.logging.LogMasking;

public class SejongLoginRequestDto {

    private String userId;
    private String password;

    public SejongLoginRequestDto() {
    }

    public SejongLoginRequestDto(String userId, String password) {
        this.userId = userId;
        this.password = password;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /** 실수로 로그에 찍혀도 비밀번호가 남지 않게 한다. */
    @Override
    public String toString() {
        return "SejongLoginRequestDto{userId=" + LogMasking.maskStudentId(userId) + ", password=[protected]}";
    }
}
