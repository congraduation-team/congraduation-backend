package com.example.congraduation.service.sejong;

/**
 * 세종 포털이 학번/비밀번호 불일치로 로그인을 거절함. 로그인 시도 제한에서 실패로 센다.
 * 기존과 같은 400 응답을 유지하도록 IllegalArgumentException을 상속한다.
 */
public class SejongInvalidCredentialsException extends IllegalArgumentException {

    public SejongInvalidCredentialsException() {
        super("학번 또는 비밀번호가 틀렸습니다.");
    }
}
