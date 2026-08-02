package com.example.congraduation.abeek.dto;

import lombok.Builder;
import lombok.Getter;
import com.example.congraduation.abeek.domain.enums.CourseCategory;
import com.example.congraduation.abeek.domain.enums.CourseRole;
import com.example.congraduation.abeek.domain.enums.DesignLevel;

import java.util.List;

@Getter
@Builder
public class AbeekEvaluationDetailResponse {
    private final String studentId;
    private final String studentNo;
    private final String studentName;
    private final int entranceYear;
    private final int graduationAbeekYear;
    private final int expectedGraduationYear;
    /** 표시용. 예: "2027년 졸업 예정 기준" */
    private final String graduationAbeekBasisLabel;
    private final AbeekEvaluationResponse evaluation;
    /** 카테고리 분류 안 된 것 포함, 이 학생의 ABEEK 매칭된 전체 이수 과목 */
    private final List<CourseDetailDto> allCompletedCourses;
    private final int allCompletedCourseCount;
    private final List<CategoryDetailDto> categories;

    @Getter
    @Builder
    public static class CategoryDetailDto {
        private final String categoryKey;
        private final String categoryLabel;
        private final CategoryProgressDto progress;
        private final int completedCourseCount;
        private final List<CourseDetailDto> completedCourses;
        /**
         * 미이수 과목 목록.
         * 인증선택(CERT_ELECTIVE)에서는 더보기용이며, 학수번호 대신 electiveArea를 쓴다.
         */
        private final List<CourseDetailDto> remainingCourses;
        /**
         * 인증선택: 아직 이수하지 않은 영역 요약(카드의 "남은 영역").
         * 다른 카테고리는 빈 목록.
         */
        private final List<RemainingAreaDto> remainingAreas;
    }

    /**
     * 인증선택 미이수 영역.
     * 카드에는 areaLabel을 보이고, 더보기에서 remainingCourses를 펼친다.
     */
    @Getter
    @Builder
    public static class RemainingAreaDto {
        /** Enum name. 예: ECONOMY_SOCIETY */
        private final String area;
        /** 표시명. 예: 경제와사회 */
        private final String areaLabel;
        private final boolean completed;
        private final int remainingCourseCount;
        private final List<CourseDetailDto> remainingCourses;
    }

    @Getter
    @Builder
    public static class CourseDetailDto {
        /** ABEEK 내부 과목 코드 (예: MAJ_OS) */
        private final String courseCode;
        /**
         * 세종 학수번호 (예: 004310).
         * 인증선택 남은 과목은 null — electiveArea를 사용.
         */
        private final String sejongCourseCode;
        /**
         * 인증선택 영역 코드 (예: ECONOMY_SOCIETY). NONE/해당없으면 null.
         */
        private final String electiveArea;
        /** 인증선택 영역 표시명 (예: 경제와사회) */
        private final String electiveAreaLabel;
        private final String courseName;
        private final CourseCategory category;
        private final String categoryLabel;
        private final CourseRole role;
        private final String roleLabel;
        private final int credits;
        private final double designCredits;
        private final DesignLevel designLevel;
        private final boolean completed;
        private final boolean waived;
        private final Integer takenYear;
        private final Integer takenSemester;
        private final String note;
    }
}
