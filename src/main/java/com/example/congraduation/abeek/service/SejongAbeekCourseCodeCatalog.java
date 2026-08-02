package com.example.congraduation.abeek.service;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 세종 기이수/시간표 학수번호 → ABEEK CourseMaster.courseCode 매핑.
 * 과목명이 바뀌어도(예: 고급프로그래밍이해-P ↔ 입문-P) 학수번호로 동일 과목을 판정한다.
 */
@Component
public class SejongAbeekCourseCodeCatalog {

    private final Map<String, String> sejongToAbeek;

    public SejongAbeekCourseCodeCatalog() {
        Map<String, String> map = new LinkedHashMap<>();

        // —— 전문교양 ——
        map.put("009067", "GEN_WRITE");
        map.put("011182", "GEN_CAREER_EXP");
        map.put("009045", "GEN_STARTUP1");
        map.put("010352", "GEN_ENG_LISTEN");
        map.put("010354", "GEN_ENG_READ");
        map.put("009068", "GEN_PHILOSOPHY");
        map.put("011110", "GEN_SEMINAR");
        map.put("009489", "GEN_WORLD_HIST");
        map.put("009936", "GEN_TECH_WRITE");
        map.put("010112", "GEN_GRAD2");
        // 고급프로그래밍입문-P / 이해-P (표기만 다름, 동일 학수번호)
        map.put("009787", "GEN_ADV_PROG_P");

        // —— BSM ——
        map.put("006098", "BSM_CALC");
        map.put("002647", "BSM_PHYS_LAB");
        map.put("000304", "BSM_EMATH1");
        map.put("009955", "BSM_DISC");
        map.put("009959", "BSM_PROB_PROG"); // 확률통계및프로그래밍(구)
        map.put("007330", "BSM_PROB");     // 확률및통계(신)
        map.put("009961", "BSM_LINEAR_PROG"); // 선형대수및프로그래밍(구)
        map.put("001725", "BSM_LINEAR");      // 선형대수(신)

        // —— 전공 ——
        map.put("009912", "MAJ_C");
        map.put("009913", "MAJ_ADV_C");
        map.put("009914", "MAJ_BASIC_DESIGN");
        map.put("004118", "MAJ_DIGITAL");
        map.put("009952", "MAJ_DS");
        map.put("009954", "MAJ_ALGO");
        map.put("003278", "MAJ_ARCH");
        map.put("004310", "MAJ_OS");
        map.put("003284", "MAJ_NETWORK");
        map.put("009960", "MAJ_CAPSTONE");
        map.put("006135", "MAJ_SEC");
        map.put("007313", "MAJ_PL");

        this.sejongToAbeek = Collections.unmodifiableMap(map);
    }

    public Optional<String> findAbeekCourseCode(String sejongCourseCode) {
        String normalized = normalize(sejongCourseCode);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sejongToAbeek.get(normalized));
    }

    public boolean isKnownSejongCode(String sejongCourseCode) {
        return findAbeekCourseCode(sejongCourseCode).isPresent();
    }

    private static String normalize(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
