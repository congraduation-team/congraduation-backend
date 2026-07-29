package com.example.congraduation.abeek.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 세종 성적표 개설학과코드 ↔ ABEEK departmentCode 매핑.
 */
@Component
public class AbeekDepartmentCatalog {

    public record DepartmentInfo(String sejongCode, String abeekCode, String name) {
    }

    private final Map<String, DepartmentInfo> bySejongCode = new LinkedHashMap<>();

    public AbeekDepartmentCatalog() {
        register("3210", "CSE", "컴퓨터공학과");
        register("3215", "SW", "소프트웨어학과");
        register("3220", "AI", "인공지능학과");
        register("3230", "DS", "데이터사이언스학과");
        register("3240", "SEC", "정보보호학과");
        register("3250", "AIROBOT", "지능형로봇학과");
        register("3110", "ARCH", "건축공학과");
        register("3120", "CIVIL", "건설환경공학과");
        register("3130", "ENV", "환경에너지공간융합학과");
        register("3140", "ENERGY", "지구자원시스템공학과");
        register("3150", "MECH", "기계공학과");
        register("3160", "AERO", "항공우주공학과");
        register("3170", "NANO", "나노신소재공학과");
        register("3180", "NUCLEAR", "양자원자력공학과");
        register("3190", "EICE", "전자정보통신공학과");
        register("3200", "EE", "전기공학과");
    }

    private void register(String sejongCode, String abeekCode, String name) {
        bySejongCode.put(sejongCode, new DepartmentInfo(sejongCode, abeekCode, name));
    }

    public Optional<DepartmentInfo> findBySejongCode(String sejongCode) {
        if (sejongCode == null || sejongCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(bySejongCode.get(sejongCode.trim()));
    }

    public Optional<DepartmentInfo> findByAbeekCode(String abeekCode) {
        if (abeekCode == null || abeekCode.isBlank()) {
            return Optional.empty();
        }
        String normalized = abeekCode.trim().toUpperCase(Locale.ROOT);
        return bySejongCode.values().stream()
                .filter(info -> info.abeekCode().equals(normalized))
                .findFirst();
    }

    /** 교양/공통 개설 코드는 학과 추론에서 제외 */
    public boolean isSharedOpeningCode(String sejongCode) {
        if (sejongCode == null || sejongCode.isBlank()) {
            return true;
        }
        String code = sejongCode.trim();
        return code.startsWith("9") || "0000".equals(code);
    }
}
