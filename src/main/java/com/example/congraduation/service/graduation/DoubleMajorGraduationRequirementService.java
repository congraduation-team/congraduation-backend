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
    private static final Set<String> IOT_MAJORS = Set.of("지능IoT학과", "지능IOT학과");
    private static final Set<String> DOUBLE_MAJOR_REQUIRED_CATEGORIES = Set.of("복필", "복수전필", "복전필");
    private static final Set<String> DOUBLE_MAJOR_ELECTIVE_CATEGORIES = Set.of("복선", "복수전선", "복전선");
    private static final List<String> GRADUATION_WORK_KEYWORDS = List.of(
            "졸업작품", "졸업시험", "졸업연주", "졸업전시", "졸업논문", "캡스톤디자인"
    );
    private static final String SUBSTITUTE_GUIDANCE =
            "이미 주전공 등에서 졸업작품을 이수하여 중복 이수가 어려운 경우, "
                    + "학과 상담 후 지정 전공선택 3학점으로 대체할 수 있습니다.";

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
                    "GUIDANCE",
                    "디자인이노베이션전공 복수전공은 졸업작품 추가 이수가 없습니다. "
                            + "전공필수 이수조건은 주임교수 상담 후 전공선택 대체과목으로 충족할 수 있으니 학과 안내를 확인하세요."
            );
        }

        if (IOT_MAJORS.contains(secondary)) {
            return new DoubleMajorGraduationRequirementProgressDto(
                    true,
                    true,
                    "GUIDANCE",
                    "지능IoT학과 복수전공은 (1) 교류형 MD(IoT 인공지능: 지정필수로 자동 검사)와 "
                            + "(2) 공유형 MD(초급/중급/고급 중 1개, 9~12학점) 이수가 필요합니다. "
                            + "공유형은 세종 개설 과목과 과목명에 '온디바이스'가 포함된 과목을 자동 합산하며, "
                            + "그 외 타대학 공유형 과목은 사물인터넷 혁신융합대학사업단/학과 기준으로 확인하세요."
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

        boolean artsToArts = ARTS_DOUBLE_MAJOR_MAJORS.contains(primary) && ARTS_DOUBLE_MAJOR_MAJORS.contains(secondary);
        boolean manga = "만화애니메이션텍전공".equals(secondary);

        if (artsToArts || manga) {
            String target = manga ? "만화애니메이션텍전공" : secondary;
            return new DoubleMajorGraduationRequirementProgressDto(
                    true,
                    false,
                    "IN_PROGRESS",
                    target + " 복수전공은 졸업작품(시험) 이수가 필요합니다. " + SUBSTITUTE_GUIDANCE
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
            case "지능IOT학과", "지능IoT학과" -> "지능IoT학과";
            default -> major.trim();
        };
    }
}
