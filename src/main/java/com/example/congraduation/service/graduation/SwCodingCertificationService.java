package com.example.congraduation.service.graduation;

import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.graduation.SwCodingCertificationProgressDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SwCodingCertificationService {

    private static final int APPLICABLE_ADMISSION_YEAR = 2023;
    private static final Set<String> MAJOR_STUDENT_MAJORS = Set.of(
            "컴퓨터공학과",
            "소프트웨어학과",
            "인공지능데이터사이언스학과",
            "AI로봇학과",
            "콘텐츠소프트웨어학과",
            "정보보호학과",
            "AI융합전자공학과",
            "국방AI로봇융합공학과",
            "국방AI융합시스템공학과",
            "국방시스템공학과"
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
    private static final String ADVANCED_C_PROGRAMMING = "고급C프로그래밍및실습";
    private static final String CODING_AND_STORYTELLING = "K-MOOC:코딩과스토리텔링";
    private static final Map<String, Integer> GRADE_ORDER = Map.of(
            "A+", 8,
            "A0", 7,
            "B+", 6,
            "B0", 5,
            "C+", 4,
            "C0", 3,
            "D+", 2,
            "D0", 1,
            "F", 0,
            "FA", 0
    );

    public SwCodingCertificationProgressDto evaluate(
            Student student,
            List<CompletedCourseUploadRowDto> courses
    ) {
        int admissionYear = student.getAdmissionYear() == null ? 0 : student.getAdmissionYear();
        if (admissionYear < APPLICABLE_ADMISSION_YEAR) {
            return new SwCodingCertificationProgressDto(
                    false,
                    false,
                    "NOT_APPLICABLE",
                    "NONE",
                    "2023학년도 이후 입학자에게만 적용됩니다.",
                    null,
                    null,
                    "입학년도 기준으로 SW코딩졸업인증 정책 적용 대상이 아닙니다."
            );
        }

        String major = normalizeMajor(student.getMajor());
        boolean majorStudent = MAJOR_STUDENT_MAJORS.contains(major);
        boolean artsCollegeStudent = ARTS_COLLEGE_MAJORS.contains(major);
        String studentGroup = majorStudent ? "MAJOR" : "NON_MAJOR";
        String graduationRule = artsCollegeStudent
                ? "고전독서인증/SW코딩인증 중 1개 이상 통과"
                : "영어/고전독서/SW코딩인증 중 2개 이상 통과";
        String primaryRequirement = majorStudent ? "TOSC 3급 이상" : "TOSC 5급 이상";
        String substituteRequirement = majorStudent
                ? "고급C프로그래밍및실습 B0 이상"
                : "K-MOOC:코딩과스토리텔링 이수";

        Optional<CompletedCourseUploadRowDto> satisfiedBySubstitute = majorStudent
                ? courses.stream().filter(this::isAdvancedCSubstituteSatisfied).findFirst()
                : courses.stream().filter(this::isCodingStorytellingSatisfied).findFirst();

        if (satisfiedBySubstitute.isPresent()) {
            CompletedCourseUploadRowDto course = satisfiedBySubstitute.get();
            return new SwCodingCertificationProgressDto(
                    true,
                    true,
                    "COMPLETED",
                    studentGroup,
                    graduationRule,
                    primaryRequirement,
                    substituteRequirement,
                    buildSatisfiedDetail(majorStudent, course)
            );
        }

        return new SwCodingCertificationProgressDto(
                true,
                false,
                "IN_PROGRESS",
                studentGroup,
                graduationRule,
                primaryRequirement,
                substituteRequirement,
                buildPendingDetail(majorStudent)
        );
    }

    private boolean isAdvancedCSubstituteSatisfied(CompletedCourseUploadRowDto course) {
        if (!matchesCourseName(course.courseName(), ADVANCED_C_PROGRAMMING)) {
            return false;
        }
        String grade = normalizeGrade(course.grade());
        Integer order = GRADE_ORDER.get(grade);
        return order != null && order >= GRADE_ORDER.get("B0");
    }

    private boolean isCodingStorytellingSatisfied(CompletedCourseUploadRowDto course) {
        if (!matchesCourseName(course.courseName(), CODING_AND_STORYTELLING)) {
            return false;
        }
        String grade = normalizeGrade(course.grade());
        return grade != null && !"NP".equals(grade) && !"F".equals(grade) && !"FA".equals(grade);
    }

    private String buildSatisfiedDetail(boolean majorStudent, CompletedCourseUploadRowDto course) {
        String grade = normalizeGrade(course.grade());
        if (majorStudent) {
            return course.courseName() + " 과목을 " + grade + "로 이수해 SW코딩졸업인증 대체요건을 충족했습니다.";
        }
        return course.courseName() + " 과목을 이수해 SW코딩졸업인증 대체요건을 충족했습니다.";
    }

    private String buildPendingDetail(boolean majorStudent) {
        if (majorStudent) {
            return "현재는 TOSC 점수 데이터가 없어 시험 통과 여부를 확인하지 못했습니다. "
                    + "대체이수 기준으로는 고급C프로그래밍및실습 B0 이상 이수 내역이 필요합니다.";
        }
        return "현재는 TOSC 점수 데이터가 없어 시험 통과 여부를 확인하지 못했습니다. "
                + "대체이수 기준으로는 K-MOOC:코딩과스토리텔링 이수 내역이 필요합니다.";
    }

    private boolean matchesCourseName(String actual, String expected) {
        return normalizeCourseName(actual).equals(normalizeCourseName(expected));
    }

    private String normalizeCourseName(String courseName) {
        if (courseName == null) {
            return "";
        }
        return courseName
                .replace("：", ":")
                .replace("·", "")
                .replace("-", "")
                .replace(" ", "")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeGrade(String grade) {
        return grade == null ? null : grade.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeMajor(String major) {
        return major == null ? "" : major.trim();
    }
}
