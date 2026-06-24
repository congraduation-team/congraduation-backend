package com.example.hackathon.service.graduation;

import com.example.hackathon.domain.Student;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DepartmentCurriculumPolicyService {

    public DepartmentCurriculumPolicy resolve(Student student) {
        String departmentKey = normalizeMajor(student.getMajor());
        int admissionYear = student.getAdmissionYear() == null ? 0 : student.getAdmissionYear();

        if (admissionYear == 2021 && "컴퓨터공학과".equals(departmentKey)) {
            return new DepartmentCurriculumPolicy(
                    departmentKey,
                    2021,
                    14,
                    0,
                    9,
                    null,
                    130,
                    72,
                    33,
                    39,
                    Map.of(
                            "전필", 33,
                            "전선", 39,
                            "공필", 14,
                            "기필", 9
                    )
            );
        }

        if (admissionYear == 2022 && "컴퓨터공학과".equals(departmentKey)) {
            return new DepartmentCurriculumPolicy(
                    departmentKey,
                    2022,
                    13,
                    6,
                    15,
                    null,
                    130,
                    72,
                    33,
                    39,
                    Map.of(
                            "전필", 33,
                            "전선", 39,
                            "공필", 13,
                            "균필", 6,
                            "기필", 15
                    )
            );
        }

        if (admissionYear == 2023 && "컴퓨터공학과".equals(departmentKey)) {
            return new DepartmentCurriculumPolicy(
                    departmentKey,
                    2023,
                    13,
                    6,
                    15,
                    null,
                    130,
                    72,
                    33,
                    39,
                    Map.of(
                            "전필", 33,
                            "전선", 39,
                            "공필", 13,
                            "균필", 6,
                            "기필", 15
                    )
            );
        }

        if (admissionYear == 2024 && "컴퓨터공학과".equals(departmentKey)) {
            return new DepartmentCurriculumPolicy(
                    departmentKey,
                    2024,
                    13,
                    9,
                    9,
                    15,
                    130,
                    60,
                    21,
                    39,
                    Map.of(
                            "전필", 21,
                            "전선", 39,
                            "공필", 13,
                            "균필", 9,
                            "기필", 9,
                            "전기", 15
                    )
            );
        }

        if (admissionYear == 2025 && "컴퓨터공학과".equals(departmentKey)) {
            return new DepartmentCurriculumPolicy(
                    departmentKey,
                    2025,
                    13,
                    9,
                    9,
                    15,
                    130,
                    60,
                    21,
                    39,
                    Map.of(
                            "전필", 21,
                            "전선", 39,
                            "공필", 13,
                            "균필", 9,
                            "기필", 9,
                            "전기", 15
                    )
            );
        }

        if (admissionYear == 2026 && "컴퓨터공학과".equals(departmentKey)) {
            return new DepartmentCurriculumPolicy(
                    departmentKey,
                    2026,
                    12,
                    9,
                    9,
                    15,
                    130,
                    60,
                    21,
                    39,
                    Map.of(
                            "전필", 21,
                            "전선", 39,
                            "공필", 12,
                            "균필", 9,
                            "기필", 9,
                            "전기", 15
                    )
            );
        }

        return new DepartmentCurriculumPolicy(
                departmentKey,
                admissionYear,
                0,
                0,
                0,
                null,
                130,
                0,
                0,
                0,
                Map.of()
        );
    }

    private String normalizeMajor(String major) {
        if (major == null) {
            return "";
        }

        String normalized = major.trim();
        if ("컴퓨터공학".equals(normalized)) {
            return "컴퓨터공학과";
        }
        return normalized;
    }
}
