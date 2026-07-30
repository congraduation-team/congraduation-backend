package com.example.congraduation.service.graduation;

import com.example.congraduation.domain.Student;
import com.example.congraduation.domain.StudentMajorTrack;
import com.example.congraduation.dto.graduation.DoubleMajorGraduationRequirementProgressDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DoubleMajorGraduationRequirementService {

    private static final Set<String> ARTS_DOUBLE_MAJOR_MAJORS = Set.of(
            "회화과",
            "패션디자인학과",
            "음악과",
            "체육학과",
            "무용과",
            "영화예술학과",
            "만화애니메이션텍전공"
    );
    private static final String DESIGN_INNOVATION_MAJOR = "디자인이노베이션전공";
    private static final Set<String> DOUBLE_MAJOR_REQUIRED_CATEGORIES = Set.of("복필", "복수전필", "복전필");
    private static final Set<String> DOUBLE_MAJOR_ELECTIVE_CATEGORIES = Set.of("복선", "복수전선", "복전선");
    private static final List<String> GRADUATION_WORK_KEYWORDS = List.of(
            "졸업작품", "졸업시험", "졸업연주", "졸업전시", "졸업논문", "캡스톤디자인"
    );

    public DoubleMajorGraduationRequirementProgressDto evaluate(
            Student student,
            StudentMajorTrack track,
            List<CompletedCourseUploadRowDto> courses
    ) {
        String primary = normalizeMajor(student.getMajor());
        String secondary = normalizeMajor(track.getDepartmentCode());

        if (DESIGN_INNOVATION_MAJOR.equals(secondary)) {
            return new DoubleMajorGraduationRequirementProgressDto(
                    false,
                    true,
                    "NOT_REQUIRED",
                    "디자인이노베이션전공 복수전공은 졸업작품 추가 이수 없이 판정합니다."
            );
        }

        if (!ARTS_DOUBLE_MAJOR_MAJORS.contains(secondary)) {
            return new DoubleMajorGraduationRequirementProgressDto(
                    false,
                    true,
                    "NOT_REQUIRED",
                    "해당 복수전공 학과는 졸업작품 추가 요건이 없는 것으로 판정합니다."
            );
        }

        boolean completedByCourse = courses.stream()
                .filter(course -> isDoubleMajorCategory(course.category()))
                .map(CompletedCourseUploadRowDto::courseName)
                .filter(courseName -> courseName != null && !courseName.isBlank())
                .anyMatch(this::containsGraduationWorkKeyword);

        if (completedByCourse) {
            return new DoubleMajorGraduationRequirementProgressDto(
                    true,
                    true,
                    "COMPLETED",
                    "복수전공 이수 과목 중 졸업작품(시험) 과목이 확인되어 추가 요건을 충족했습니다."
            );
        }

        if (ARTS_DOUBLE_MAJOR_MAJORS.contains(primary) && ARTS_DOUBLE_MAJOR_MAJORS.contains(secondary)) {
            return new DoubleMajorGraduationRequirementProgressDto(
                    true,
                    false,
                    "MANUAL_CHECK_REQUIRED",
                    "예체능대학 전공자가 예체능대학 복수전공을 이수하는 경우 지정 전공선택 3학점 대체가 가능해 자동 판정이 어렵습니다."
            );
        }

        if ("만화애니메이션텍전공".equals(secondary)) {
            return new DoubleMajorGraduationRequirementProgressDto(
                    true,
                    false,
                    "MANUAL_CHECK_REQUIRED",
                    "만화애니메이션텍전공 복수전공은 졸업작품 또는 지정 전공선택 3학점 대체가 가능해 자동 판정이 어렵습니다."
            );
        }

        return new DoubleMajorGraduationRequirementProgressDto(
                true,
                false,
                "IN_PROGRESS",
                secondary + " 복수전공은 졸업작품(시험) 추가 이수가 필요합니다."
        );
    }

    private boolean isDoubleMajorCategory(String category) {
        if (category == null) {
            return false;
        }
        String normalized = category.trim();
        return DOUBLE_MAJOR_REQUIRED_CATEGORIES.contains(normalized)
                || DOUBLE_MAJOR_ELECTIVE_CATEGORIES.contains(normalized);
    }

    private boolean containsGraduationWorkKeyword(String courseName) {
        return GRADUATION_WORK_KEYWORDS.stream().anyMatch(courseName::contains);
    }

    private String normalizeMajor(String major) {
        if (major == null) {
            return "";
        }
        return switch (major.trim()) {
            case "건축학전공" -> "건축학과";
            case "법학과", "법학" -> "법학전공";
            default -> major.trim();
        };
    }
}
