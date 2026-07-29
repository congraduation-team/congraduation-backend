package com.example.congraduation.abeek.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 세종 성적표 개설학과코드 / 강의시간표 개설학과명 ↔ ABEEK departmentCode 매핑.
 */
@Component
public class AbeekDepartmentCatalog {

    public record DepartmentInfo(String sejongCode, String abeekCode, String name) {
    }

    private final Map<String, DepartmentInfo> bySejongCode = new LinkedHashMap<>();
    private final Map<String, DepartmentInfo> byAbeekCode = new LinkedHashMap<>();
    private final Map<String, DepartmentInfo> byNormalizedName = new LinkedHashMap<>();
    private final Map<String, List<String>> openingNameAliases = new LinkedHashMap<>();

    public AbeekDepartmentCatalog() {
        register("3210", "CSE", "컴퓨터공학과", List.of("컴퓨터공학과"));
        register("3215", "SW", "소프트웨어학과", List.of("소프트웨어학과", "콘텐츠소프트웨어학과"));
        register("3220", "AI", "인공지능학과", List.of("인공지능학과"));
        register("3230", "DS", "데이터사이언스학과", List.of("데이터사이언스학과", "인공지능데이터사이언스학과"));
        register("3240", "SEC", "정보보호학과", List.of("정보보호학과"));
        register("3250", "AIROBOT", "지능형로봇학과", List.of("AI로봇학과", "지능형로봇학과", "국방AI로봇융합공학과"));
        register("3110", "ARCH", "건축공학과", List.of("건축공학과"));
        register("3120", "CIVIL", "건설환경공학과", List.of("건설환경공학과", "환경융합공학과"));
        register("3130", "ENV", "환경에너지공간융합학과", List.of("환경에너지공간융합학과"));
        register("3140", "ENERGY", "지구자원시스템공학과", List.of("지구자원시스템공학과", "에너지자원공학과"));
        register("3150", "MECH", "기계공학과", List.of("기계공학과"));
        register("3160", "AERO", "항공우주공학과", List.of(
                "항공우주공학과",
                "우주항공시스템공학부 우주항공공학전공",
                "우주항공시스템공학부 항공시스템공학전공",
                "우주항공시스템공학부 지능형드론융합전공",
                "우주항공시스템공학부"
        ));
        register("3170", "NANO", "나노신소재공학과", List.of("나노신소재공학과"));
        register("3180", "NUCLEAR", "양자원자력공학과", List.of("양자원자력공학과"));
        register("3190", "EICE", "전자정보통신공학과", List.of("전자정보통신공학과", "AI융합전자공학과"));
        register("3200", "EE", "전기공학과", List.of("전기공학과", "양자지능정보학과"));
    }

    private void register(String sejongCode, String abeekCode, String name, List<String> aliases) {
        DepartmentInfo info = new DepartmentInfo(sejongCode, abeekCode, name);
        bySejongCode.put(sejongCode, info);
        byAbeekCode.put(abeekCode, info);
        byNormalizedName.put(normalize(name), info);
        List<String> names = new ArrayList<>();
        names.add(name);
        for (String alias : aliases) {
            names.add(alias);
            byNormalizedName.putIfAbsent(normalize(alias), info);
        }
        openingNameAliases.put(abeekCode, List.copyOf(names));
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
        return Optional.ofNullable(byAbeekCode.get(abeekCode.trim().toUpperCase(Locale.ROOT)));
    }

    public Optional<DepartmentInfo> findByDepartmentName(String departmentName) {
        if (departmentName == null || departmentName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byNormalizedName.get(normalize(departmentName)));
    }

    public Collection<DepartmentInfo> allDepartments() {
        return List.copyOf(byAbeekCode.values());
    }

    public List<String> openingDepartmentNames(String abeekCode) {
        if (abeekCode == null || abeekCode.isBlank()) {
            return List.of();
        }
        return openingNameAliases.getOrDefault(abeekCode.trim().toUpperCase(Locale.ROOT), List.of());
    }

    /** 교양/공통 개설 코드는 학과 추론에서 제외 */
    public boolean isSharedOpeningCode(String sejongCode) {
        if (sejongCode == null || sejongCode.isBlank()) {
            return true;
        }
        String code = sejongCode.trim();
        return code.startsWith("9") || "0000".equals(code);
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
