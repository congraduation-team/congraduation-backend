package com.example.congraduation.service.graduation;

import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.graduation.ClassicReadingCertificationProgressDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ClassicReadingCertificationService {

    private static final int REQUIRED_POLICY_START_YEAR = 2015;
    private static final int OPTIONAL_POLICY_START_YEAR = 2023;
    private static final Set<String> DEPARTMENT_EXEMPT_MAJORS = Set.of(
            "호텔외식비즈니스학과",
            "호텔외식관광프랜차이즈경영학과",
            "글로벌조리학과"
    );
    private static final String CLASSIC_SPECIAL_LECTURE = "고전특강";

    public ClassicReadingCertificationProgressDto evaluate(
            Student student,
            List<CompletedCourseUploadRowDto> courses
    ) {
        int admissionYear = student.getAdmissionYear() == null ? 0 : student.getAdmissionYear();
        boolean optionalPolicy = admissionYear >= OPTIONAL_POLICY_START_YEAR;
        String major = normalizeMajor(student.getMajor());
        String requirement = buildRequirementSummary(admissionYear, optionalPolicy);

        if (admissionYear > 0 && admissionYear < REQUIRED_POLICY_START_YEAR) {
            return new ClassicReadingCertificationProgressDto(
                    false,
                    true,
                    "NOT_APPLICABLE",
                    "NONE",
                    requirement,
                    CLASSIC_SPECIAL_LECTURE + " 이수",
                    "현재 기준으로는 고전독서인증 정책 적용 대상이 아닙니다."
            );
        }

        if (isDepartmentExempt(major)) {
            return new ClassicReadingCertificationProgressDto(
                    false,
                    true,
                    "EXEMPTED",
                    optionalPolicy ? "OPTIONAL" : "EXEMPT",
                    requirement,
                    CLASSIC_SPECIAL_LECTURE + " 이수",
                    "학과 기준 면제 대상이라 고전독서인증이 면제됩니다."
            );
        }

        if (hasCompletedClassicSpecialLecture(courses)) {
            return new ClassicReadingCertificationProgressDto(
                    true,
                    true,
                    "COMPLETED",
                    optionalPolicy ? "OPTIONAL" : "REQUIRED",
                    requirement,
                    CLASSIC_SPECIAL_LECTURE + " 이수",
                    CLASSIC_SPECIAL_LECTURE + " 이수로 고전독서인증 대체요건을 충족했습니다."
            );
        }

        if (student.isClassicReadingCertified()) {
            return new ClassicReadingCertificationProgressDto(
                    true,
                    true,
                    "CERTIFIED",
                    optionalPolicy ? "OPTIONAL" : "REQUIRED",
                    requirement,
                    CLASSIC_SPECIAL_LECTURE + " 이수",
                    buildCertifiedDetail(student)
            );
        }

        return new ClassicReadingCertificationProgressDto(
                true,
                false,
                "IN_PROGRESS",
                optionalPolicy ? "OPTIONAL" : "REQUIRED",
                requirement,
                CLASSIC_SPECIAL_LECTURE + " 이수",
                buildPendingDetail(optionalPolicy, student)
        );
    }

    private boolean hasCompletedClassicSpecialLecture(List<CompletedCourseUploadRowDto> courses) {
        return courses.stream()
                .anyMatch(course -> matchesCourseName(course.courseName(), CLASSIC_SPECIAL_LECTURE) && isPassed(course.grade()));
    }

    private boolean isDepartmentExempt(String major) {
        return DEPARTMENT_EXEMPT_MAJORS.contains(major);
    }

    private String buildRequirementSummary(int admissionYear, boolean optionalPolicy) {
        if (optionalPolicy) {
            return "2023학년도 이후 기준: 영어/고전독서/SW코딩인증 중 졸업정책에 맞는 개수를 충족해야 하며, 고전특강 이수는 고전독서인증 대체로 인정됩니다.";
        }
        if (admissionYear >= REQUIRED_POLICY_START_YEAR) {
            return "2015~2022학년도 기준: 고전독서인증 필수이며, 고전특강 이수는 고전독서인증 대체로 인정됩니다.";
        }
        return "고전독서인증 또는 고전특강 이수 여부를 확인합니다.";
    }

    private String buildCertifiedDetail(Student student) {
        StringBuilder detail = new StringBuilder("세종 고전독서인증 사이트에서 완료로 확인되었습니다.");
        if (student.getClassicReadingCertifiedCount() != null && student.getClassicReadingRequiredCount() != null) {
            detail.append(" 인증 ").append(student.getClassicReadingCertifiedCount())
                    .append("권 / 필요 ").append(student.getClassicReadingRequiredCount()).append("권.");
        }
        if (student.getClassicReadingCompletedCount() != null) {
            detail.append(" 독후감 제출 ").append(student.getClassicReadingCompletedCount()).append("권.");
        }
        return detail.toString();
    }

    private String buildPendingDetail(boolean optionalPolicy, Student student) {
        StringBuilder detail = new StringBuilder();
        if (student.getClassicReadingCrawledAt() != null) {
            detail.append("세종 고전독서인증 사이트 기준 아직 완료되지 않았습니다. ");
            if (student.getClassicReadingCertifiedCount() != null && student.getClassicReadingRequiredCount() != null) {
                detail.append("현재 인증 ").append(student.getClassicReadingCertifiedCount())
                        .append("권 / 필요 ").append(student.getClassicReadingRequiredCount()).append("권. ");
            }
        } else {
            detail.append("세종 고전독서인증 크롤링 결과가 아직 저장되지 않았습니다. ");
        }
        if (optionalPolicy) {
            detail.append("대체인정 기준으로는 고전특강 이수 여부를 함께 판정합니다.");
        } else {
            detail.append("대체인정 기준으로는 고전특강 이수 여부를 함께 판정합니다.");
        }
        return detail.toString();
    }

    private boolean matchesCourseName(String actual, String expected) {
        return normalizeCourseName(actual).equals(normalizeCourseName(expected));
    }

    private boolean isPassed(String grade) {
        String normalized = grade == null ? "" : grade.trim().toUpperCase(Locale.ROOT);
        return !normalized.isBlank() && !"F".equals(normalized) && !"FA".equals(normalized) && !"NP".equals(normalized);
    }

    private String normalizeCourseName(String courseName) {
        if (courseName == null) {
            return "";
        }
        return courseName
                .replace(" ", "")
                .replace("-", "")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeMajor(String major) {
        return major == null ? "" : major.trim();
    }
}
