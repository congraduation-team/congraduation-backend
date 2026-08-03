package com.example.congraduation.service.graduation;

import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.graduation.EnglishCertificationProgressDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.service.transcript.TranscriptStandingMapper;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class EnglishCertificationService {

    private static final int OPTIONAL_POLICY_ADMISSION_YEAR = 2023;
    private static final Set<String> ENGLISH_MAJOR_NAMES = Set.of(
            "영어영문학과",
            "영어영문학전공",
            "영어데이터융합전공"
    );
    private static final Set<String> ARTS_COLLEGE_MAJORS = Set.of(
            "회화과",
            "패션디자인학과",
            "음악과",
            "체육학과",
            "무용과",
            "영화예술학과",
            "디자인이노베이션전공",
            "만화애니메이션텍전공",
            "영상디자인 융합전공",
            "뉴미디어퍼포먼스 융합전공",
            "럭셔리브랜드디자인 융합전공"
    );
    private static final Set<String> DEPARTMENT_EXEMPT_MAJORS = Set.of(
            "호텔외식비즈니스학과",
            "호텔외식관광프랜차이즈경영학과",
            "글로벌조리학과"
    );
    private static final String INTENSIVE_ENGLISH = "INTENSIVEENGLISH";

    public EnglishCertificationProgressDto evaluate(
            Student student,
            List<CompletedCourseUploadRowDto> courses
    ) {
        String major = normalizeMajor(student.getMajor());
        int admissionYear = student.getAdmissionYear() == null ? 0 : student.getAdmissionYear();
        boolean optionalPolicy = admissionYear >= OPTIONAL_POLICY_ADMISSION_YEAR;
        boolean englishMajor = isEnglishMajor(major);
        String requirement = buildRequirementSummary(optionalPolicy, englishMajor);

        if (isDepartmentExempt(major)) {
            return new EnglishCertificationProgressDto(
                    false,
                    true,
                    "EXEMPTED",
                    optionalPolicy ? "OPTIONAL" : "EXEMPT",
                    requirement,
                    "학과 기준 면제 대상이라 영어졸업인증이 면제됩니다."
            );
        }

        if (isArtsCollegeMajor(major)) {
            return new EnglishCertificationProgressDto(
                    false,
                    true,
                    "EXEMPTED",
                    optionalPolicy ? "OPTIONAL" : "EXEMPT",
                    requirement,
                    optionalPolicy
                            ? "예체능 계열은 2023학년도 이후 영어졸업인증 선택 대상입니다."
                            : "예체능 계열은 영어졸업인증 면제 대상입니다."
            );
        }

        if (hasCompletedIntensiveEnglish(courses)) {
            return new EnglishCertificationProgressDto(
                    true,
                    true,
                    "EXEMPTED",
                    optionalPolicy ? "OPTIONAL" : "REQUIRED",
                    requirement,
                    "Intensive English 이수로 영어졸업인증이 면제됩니다."
            );
        }

        // 입학 이후 실제 이수한 정규학기 수(휴학 연도 제외). 달력 (연도차×2+학기) 금지.
        int completedRegularSemesters = TranscriptStandingMapper.countDistinctRegularTerms(
                courses,
                student.getAdmissionYear()
        );
        int requiredSemestersForExemption = "건축학과".equals(major) ? 12 : 10;
        if (completedRegularSemesters >= requiredSemestersForExemption) {
            return new EnglishCertificationProgressDto(
                    true,
                    true,
                    "EXEMPTED",
                    optionalPolicy ? "OPTIONAL" : "REQUIRED",
                    requirement,
                    completedRegularSemesters + "학기 이수 기준으로 영어졸업인증이 면제됩니다."
            );
        }

        return new EnglishCertificationProgressDto(
                true,
                false,
                "IN_PROGRESS",
                optionalPolicy ? "OPTIONAL" : "REQUIRED",
                requirement,
                buildPendingDetail(
                        optionalPolicy,
                        englishMajor,
                        completedRegularSemesters,
                        requiredSemestersForExemption
                )
        );
    }

    private boolean hasCompletedIntensiveEnglish(List<CompletedCourseUploadRowDto> courses) {
        return courses.stream()
                .anyMatch(course -> normalizeCourseName(course.courseName()).equals(INTENSIVE_ENGLISH));
    }

    private boolean isArtsCollegeMajor(String major) {
        return ARTS_COLLEGE_MAJORS.contains(major);
    }

    private boolean isDepartmentExempt(String major) {
        return DEPARTMENT_EXEMPT_MAJORS.contains(major);
    }

    private boolean isEnglishMajor(String major) {
        return ENGLISH_MAJOR_NAMES.contains(major);
    }

    private String buildRequirementSummary(boolean optionalPolicy, boolean englishMajor) {
        if (englishMajor) {
            if (optionalPolicy) {
                return "영어영문 계열 기준: TOEIC 900점 이상, TOEFL iBT 91점 이상, "
                        + "TEPS 766점(뉴텝스 430점) 이상, OPIc Intermediate Mid 2 이상, "
                        + "TOEIC Speaking Intermediate Mid 2 이상, G-TELP Level 2(90점), "
                        + "G-TELP Speaking Level 3 또는 Intensive English 이수";
            }
            return "영어영문 계열 기준: TOEIC 800점 이상, TOEFL iBT 91점 이상, "
                    + "TEPS 637점(뉴텝스 348점) 이상, OPIc Intermediate Mid 1 이상, "
                    + "TOEIC Speaking Intermediate Mid 1 이상, G-TELP Level 2(77점) "
                    + "또는 Intensive English 이수(면제 가능)";
        }
        if (optionalPolicy) {
            return "일반 기준: TOEIC 800점 이상, TOEFL iBT 80점 이상, "
                    + "TEPS 637점(뉴텝스 348점) 이상, OPIc Intermediate Mid 1 이상, "
                    + "TOEIC Speaking Intermediate Mid 1 이상, G-TELP Level 2(77점), "
                    + "G-TELP Speaking Level 4 또는 Intensive English 이수";
        }
        return "일반 기준: TOEIC 700점 이상, TOEFL iBT 80점 이상, "
                + "TEPS 556점(뉴텝스 301점) 이상, OPIc Intermediate Low 이상, "
                + "TOEIC Speaking Intermediate Low 이상, G-TELP Level 2(65점) "
                + "또는 Intensive English 이수(면제 가능)";
    }

    private String buildPendingDetail(
            boolean optionalPolicy,
            boolean englishMajor,
            int completedRegularSemesters,
            int requiredSemestersForExemption
    ) {
        String scoreGuide = buildScoreGuide(optionalPolicy, englishMajor);
        String progress = "현재 기이수 정규학기 " + completedRegularSemesters + "학기"
                + " / 학기 면제 기준 " + requiredSemestersForExemption + "학기. ";
        if (optionalPolicy) {
            return progress + scoreGuide + " 현재는 공인영어 점수 데이터가 없어 시험 통과 여부를 확인하지 못했습니다. "
                    + "대체인정 기준으로는 Intensive English 이수 여부만 판정 중이며, 편입/외국인/재외국민 등 입학전형 기반 면제는 현재 확인할 수 없습니다.";
        }
        return progress + scoreGuide + " 현재는 공인영어 점수 데이터가 없어 시험 통과 여부를 확인하지 못했습니다. "
                + "대체인정 기준으로는 Intensive English 이수 여부만 판정 중이며, 편입/외국인/재외국민 등 입학전형 기반 면제는 현재 확인할 수 없습니다.";
    }

    private String buildScoreGuide(boolean optionalPolicy, boolean englishMajor) {
        if (englishMajor) {
            if (optionalPolicy) {
                return "영어영문 계열 2023학년도 이후 기준 점수는 TOEIC 900점 이상, TOEFL iBT 91점 이상, "
                        + "TEPS 766점(뉴텝스 430점) 이상, OPIc Intermediate Mid 2 이상, "
                        + "TOEIC Speaking Intermediate Mid 2 이상, G-TELP Level 2(90점), "
                        + "G-TELP Speaking Level 3입니다.";
            }
            return "영어영문 계열 2012~2022학년도 기준 점수는 TOEIC 800점 이상, TOEFL iBT 91점 이상, "
                    + "TEPS 637점(뉴텝스 348점) 이상, OPIc Intermediate Mid 1 이상, "
                    + "TOEIC Speaking Intermediate Mid 1 이상, G-TELP Level 2(77점)입니다.";
        }
        if (optionalPolicy) {
            return "2023학년도 이후 기준 점수는 TOEIC 800점 이상, TOEFL iBT 80점 이상, "
                    + "TEPS 637점(뉴텝스 348점) 이상, OPIc Intermediate Mid 1 이상, "
                    + "TOEIC Speaking Intermediate Mid 1 이상, G-TELP Level 2(77점), "
                    + "G-TELP Speaking Level 4입니다.";
        }
        return "2012~2022학년도 기준 점수는 TOEIC 700점 이상, TOEFL iBT 80점 이상, "
                + "TEPS 556점(뉴텝스 301점) 이상, OPIc Intermediate Low 이상, "
                + "TOEIC Speaking Intermediate Low 이상, G-TELP Level 2(65점)입니다.";
    }

    private String normalizeMajor(String major) {
        return major == null ? "" : major.trim();
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
}
