package com.example.congraduation.service.graduation;

import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.transcript.CategoryCourseDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class BalancedLiberalCoursePolicyService {

    private static final String AREA_HISTORY = "역사와사상";
    private static final String AREA_NATURE = "자연과과학";
    private static final String AREA_SOCIETY = "경제와사회";
    private static final String AREA_CULTURE = "문화와예술";
    private static final String AREA_FUSION = "융합과창의";

    private static final List<CategoryCourseDto> COMMON_LIBERAL_REQUIRED_2022_2023 = List.of(
            new CategoryCourseDto("GEN_SEMINAR_A", "신입생세미나A", "1"),
            new CategoryCourseDto("GEN_SEMINAR_B", "신입생세미나B", "1"),
            new CategoryCourseDto("GEN_WRITE", "문제해결을위한글쓰기와발표", "3"),
            new CategoryCourseDto("GEN_UNIVERSE", "우주자연인간", "1"),
            new CategoryCourseDto("GEN_CAREER_JOB", "취창업과진로설계", "1"),
            new CategoryCourseDto("GEN_PHILOSOPHY", "서양철학:쟁점과토론", "3"),
            new CategoryCourseDto("GEN_UNI_ENG", "대학영어", "2"),
            new CategoryCourseDto("GEN_STARTUP1", "창업과기업가정신1", "1")
    );

    private static final List<CategoryCourseDto> COMMON_LIBERAL_REQUIRED_2024_2025 = List.of(
            new CategoryCourseDto("GEN_CAREER_DESIGN", "세종인을위한진로설계", "1"),
            new CategoryCourseDto("GEN_MAJOR_EXPLORATION", "세종인을위한전공탐색", "1"),
            new CategoryCourseDto("GEN_WRITE", "문제해결을위한글쓰기와발표", "3"),
            new CategoryCourseDto("GEN_UNIVERSE", "우주자연인간", "1"),
            new CategoryCourseDto("GEN_CAREER_JOB", "취창업과진로설계", "1"),
            new CategoryCourseDto("GEN_PHILOSOPHY", "서양철학:쟁점과토론", "3"),
            new CategoryCourseDto("GEN_UNI_ENG", "대학영어", "2"),
            new CategoryCourseDto("GEN_STARTUP1", "창업과기업가정신1", "1")
        );

    private static final List<CategoryCourseDto> COMMON_LIBERAL_REQUIRED_2026 = List.of(
            new CategoryCourseDto("GEN_WRITE_2026", "비판적사고와창의적글쓰기", "3"),
            new CategoryCourseDto("GEN_PHILOSOPHY", "서양철학:쟁점과토론", "3"),
            new CategoryCourseDto("GEN_UNI_ENG", "대학영어", "2"),
            new CategoryCourseDto("GEN_STARTUP1", "창업과기업가정신", "1"),
            new CategoryCourseDto("GEN_CAREER_JOB", "취업과진로역량개발", "1"),
            new CategoryCourseDto("GEN_MAJOR_EXPLORATION", "세종인을위한전공탐색", "1")
    );

    private static final Map<String, List<CategoryCourseDto>> BALANCED_LIBERAL_AREA_CATALOG_2022_2025 = Map.of(
            AREA_HISTORY,
            List.of(
                    new CategoryCourseDto("GEN_WORLD_HIST", "세계사", "3"),
                    new CategoryCourseDto("GEN_EAST_WEST", "동서양의사상과윤리", "3")
            ),
            AREA_SOCIETY,
            List.of(
                    new CategoryCourseDto("GEN_ECON", "경제학", "3"),
                    new CategoryCourseDto("GEN_MGMT", "경영학", "3")
            ),
            AREA_CULTURE,
            List.of(
                    new CategoryCourseDto("GEN_METAVERSE", "컴퓨터게임과메타버스", "3"),
                    new CategoryCourseDto("GEN_FUSION_ART", "융합예술의이해", "3")
            )
    );

    private static final Map<String, List<CategoryCourseDto>> BALANCED_LIBERAL_AREA_CATALOG_2026 = Map.of(
            AREA_HISTORY,
            List.of(
                    new CategoryCourseDto("BAL_HISTORY_EAST_WEST", "동서양의사상과윤리", "3"),
                    new CategoryCourseDto("BAL_HISTORY_BIBLE", "성서와기독교", "3"),
                    new CategoryCourseDto("BAL_HISTORY_WORLD", "세계사", "3"),
                    new CategoryCourseDto("BAL_HISTORY_KOREA", "한국현대사", "3")
            ),
            AREA_NATURE,
            List.of(
                    new CategoryCourseDto("BAL_NATURE_BIO", "생명과학의이해", "3"),
                    new CategoryCourseDto("BAL_NATURE_MATH", "수의세계", "3"),
                    new CategoryCourseDto("BAL_NATURE_ENV", "지구환경과기후변화", "3"),
                    new CategoryCourseDto("BAL_NATURE_SCIENCE", "현대과학으로의초대", "3")
            ),
            AREA_SOCIETY,
            List.of(
                    new CategoryCourseDto("BAL_SOCIETY_MGMT", "경영학", "3"),
                    new CategoryCourseDto("BAL_SOCIETY_ECON", "경제학", "3"),
                    new CategoryCourseDto("BAL_SOCIETY_MEDIA", "미디어빅뱅과방송", "3"),
                    new CategoryCourseDto("BAL_SOCIETY_LAW", "현대사회와법", "3"),
                    new CategoryCourseDto("BAL_SOCIETY_WELFARE", "복지국가의이해", "3")
            ),
            AREA_CULTURE,
            List.of(
                    new CategoryCourseDto("BAL_CULTURE_ART", "융합예술의이해", "3"),
                    new CategoryCourseDto("BAL_CULTURE_GAME", "컴퓨터게임과메타버스", "3"),
                    new CategoryCourseDto("BAL_CULTURE_KOREA", "한국의문화와한류", "3"),
                    new CategoryCourseDto("BAL_CULTURE_MODERN", "현대예술의이해", "3")
            ),
            AREA_FUSION,
            List.of(
                    new CategoryCourseDto("BAL_FUSION_SPACE", "공간과인간:인문,예술,공학의융합", "3"),
                    new CategoryCourseDto("BAL_FUSION_MACHINE", "기계,인간,지능", "3"),
                    new CategoryCourseDto("BAL_FUSION_PSY_AI", "심리학과인공지능", "3"),
                    new CategoryCourseDto("BAL_FUSION_SPACE_CIV", "우주와문명", "3"),
                    new CategoryCourseDto("BAL_FUSION_SCIENCE", "융합과학탐구", "3"),
                    new CategoryCourseDto("BAL_FUSION_THINKING", "융합적사고", "3"),
                    new CategoryCourseDto("BAL_FUSION_WRITE_STEM", "융합적사고에기반한이공계글쓰기", "3"),
                    new CategoryCourseDto("BAL_FUSION_WRITE_HUMAN", "융합적사고에기반한인문사회계글쓰기", "3"),
                    new CategoryCourseDto("BAL_FUSION_COGNITIVE", "인지과학:마음,언어,기계", "3")
            )
    );

    private static final Map<String, String> BALANCED_LIBERAL_AREA_BY_ALIAS = Map.ofEntries(
            Map.entry("세계사", AREA_HISTORY),
            Map.entry("세계사:인간과문명", AREA_HISTORY),
            Map.entry("동서양의사상과윤리", AREA_HISTORY),
            Map.entry("성서와기독교", AREA_HISTORY),
            Map.entry("한국현대사", AREA_HISTORY),
            Map.entry("생명과학의이해", AREA_NATURE),
            Map.entry("수의세계", AREA_NATURE),
            Map.entry("지구환경과기후변화", AREA_NATURE),
            Map.entry("현대과학으로의초대", AREA_NATURE),
            Map.entry("경제학", AREA_SOCIETY),
            Map.entry("경영학", AREA_SOCIETY),
            Map.entry("미디어빅뱅과방송", AREA_SOCIETY),
            Map.entry("현대사회와법", AREA_SOCIETY),
            Map.entry("복지국가의이해", AREA_SOCIETY),
            Map.entry("컴퓨터게임과메타버스", AREA_CULTURE),
            Map.entry("융합예술의이해", AREA_CULTURE),
            Map.entry("한국의문화와한류", AREA_CULTURE),
            Map.entry("현대예술의이해", AREA_CULTURE),
            Map.entry("공간과인간:인문,예술,공학의융합", AREA_FUSION),
            Map.entry("기계,인간,지능", AREA_FUSION),
            Map.entry("심리학과인공지능", AREA_FUSION),
            Map.entry("우주와문명", AREA_FUSION),
            Map.entry("융합과학탐구", AREA_FUSION),
            Map.entry("융합적사고", AREA_FUSION),
            Map.entry("융합적사고에기반한이공계글쓰기", AREA_FUSION),
            Map.entry("융합적사고에기반한인문사회계글쓰기", AREA_FUSION),
            Map.entry("인지과학:마음,언어,기계", AREA_FUSION)
    );

    private static final Set<String> HISTORY_EXCLUDED_MAJORS = Set.of(
            "국어국문학과",
            "영어영문학전공",
            "일어일문학전공",
            "중국통상학전공",
            "역사학과",
            "한국언어문화전공"
    );

    private static final Set<String> NATURE_EXCLUDED_MAJORS = Set.of(
            "수학통계학과",
            "물리천문학과",
            "화학과",
            "식품생명공학전공",
            "바이오융합공학전공",
            "바이오산업자원공학전공",
            "스마트생명산업융합학과",
            "AI로봇학과",
            "인공지능데이터사이언스학과",
            "지능형드론융합전공",
            "건설환경공학과",
            "건축공학과",
            "건축학과",
            "기계공학과",
            "나노신소재공학과",
            "양자원자력공학과",
            "전자정보통신공학과",
            "정보보호학과",
            "컴퓨터공학과",
            "소프트웨어학과",
            "국방시스템공학과",
            "항공시스템공학전공",
            "우주항공공학전공",
            "지구자원시스템공학과",
            "환경에너지공간융합학과"
    );

    private static final Set<String> SOCIETY_EXCLUDED_MAJORS = Set.of(
            "교육학과",
            "미디어커뮤니케이션학과",
            "행정학과",
            "법학전공",
            "경영학부",
            "경제학과",
            "국제통상전공",
            "국제협력전공",
            "글로벌조리학과",
            "외식경영학전공",
            "호텔관광경영학전공",
            "호텔외식관광프랜차이즈경영학과",
            "호텔외식비즈니스학과"
    );

    private static final Set<String> CULTURE_EXCLUDED_MAJORS = Set.of(
            "무용과",
            "음악과",
            "영화예술학과",
            "체육학과",
            "패션디자인학과",
            "회화과",
            "디자인이노베이션전공",
            "만화애니메이션텍전공",
            "영상디자인 융합전공",
            "뉴미디어퍼포먼스 융합전공",
            "럭셔리브랜드디자인 융합전공"
    );

    private static final Map<String, Set<String>> COMMON_LIBERAL_EQUIVALENTS = Map.of(
            "GEN_WRITE", Set.of("쓰기와말하기", "문제해결을위한글쓰기와발표", "비판적사고와창의적글쓰기"),
            "GEN_PHILOSOPHY", Set.of("사회와가치", "서양철학의이해", "서양철학:쟁점과토론"),
            "GEN_UNI_ENG", Set.of(
                    "대학영어",
                    "English Listening Practice 1",
                    "English Reading Practice 1",
                    "English Writing 1",
                    "English Writing 2",
                    "English Composition 3",
                    "English Composition 4",
                    "English for Professional Purposes 1",
                    "English for Professional Purposes 2"
            ),
            "GEN_STARTUP1", Set.of("창업과기업가정신1", "창업과기업가정신"),
            "GEN_MAJOR_EXPLORATION", Set.of(
                    "세종인을위한전공탐색",
                    "신입생세미나B",
                    "대학생활과진로탐색",
                    "대학생활과진로설계1"
            ),
            "GEN_CAREER_DESIGN", Set.of(
                    "세종인을위한진로설계",
                    "신입생세미나",
                    "신입생세미나1",
                    "신입생세미나A",
                    "대학생활과진로설계"
            ),
            "GEN_CAREER_JOB", Set.of("취창업과진로설계", "취업과진로역량개발"),
            "GEN_UNIVERSE", Set.of("우주자연인간"),
            "GEN_SEMINAR_A", Set.of("신입생세미나", "신입생세미나1", "신입생세미나A", "세종인을위한진로설계"),
            "GEN_SEMINAR_B", Set.of("신입생세미나B", "세종인을위한전공탐색")
    );

    public BalancedLiberalRequirement resolveRequirement(Integer admissionYear) {
        int year = admissionYear == null ? 0 : admissionYear;

        if (year >= 2024) {
            return new BalancedLiberalRequirement(9, 3, 2, 4);
        }
        if (year >= 2022) {
            return new BalancedLiberalRequirement(6, 2, 2, 4);
        }

        return new BalancedLiberalRequirement(0, 0, 1, 4);
    }

    public String resolveArea(Student student, CompletedCourseUploadRowDto course) {
        Integer admissionYear = student == null ? null : student.getAdmissionYear();
        if (!isEligibleAcademicYear(admissionYear, course)) {
            return null;
        }
        String area = BALANCED_LIBERAL_AREA_BY_ALIAS.get(normalizeCourseName(course.courseName()));
        if (area == null) {
            return null;
        }

        Set<String> availableAreas = new LinkedHashSet<>(availableAreas(student));
        return availableAreas.contains(area) ? area : null;
    }

    public boolean isEligibleAcademicYear(Integer admissionYear, CompletedCourseUploadRowDto course) {
        BalancedLiberalRequirement requirement = resolveRequirement(admissionYear);
        Integer takenYear = parseTakenYear(course.year());
        if (takenYear == null || admissionYear == null) {
            return false;
        }

        int academicYear = takenYear - admissionYear + 1;
        return academicYear >= requirement.minAcademicYear() && academicYear <= requirement.maxAcademicYear();
    }

    public List<String> availableAreas(Student student) {
        Integer admissionYear = student == null ? null : student.getAdmissionYear();
        int year = admissionYear == null ? 0 : admissionYear;
        if (year >= 2026) {
            List<String> areas = new ArrayList<>(List.of(
                    AREA_HISTORY,
                    AREA_NATURE,
                    AREA_SOCIETY,
                    AREA_CULTURE,
                    AREA_FUSION
            ));
            String excludedArea = resolveExcludedArea(student);
            if (excludedArea != null) {
                areas.remove(excludedArea);
            }
            return List.copyOf(areas);
        }
        if (year >= 2022) {
            return List.of(AREA_HISTORY, AREA_SOCIETY, AREA_CULTURE);
        }
        return List.of();
    }

    public List<CategoryCourseDto> requiredCommonLiberalCourses(Integer admissionYear) {
        int year = admissionYear == null ? 0 : admissionYear;
        if (year >= 2026) {
            return COMMON_LIBERAL_REQUIRED_2026;
        }
        if (year >= 2024) {
            return COMMON_LIBERAL_REQUIRED_2024_2025;
        }
        if (year >= 2022) {
            return COMMON_LIBERAL_REQUIRED_2022_2023;
        }
        return List.of();
    }

    public Map<String, List<CategoryCourseDto>> balancedLiberalAreaCatalog(Student student) {
        Integer admissionYear = student == null ? null : student.getAdmissionYear();
        List<String> availableAreas = availableAreas(student);
        Map<String, List<CategoryCourseDto>> sourceCatalog =
                admissionYear != null && admissionYear >= 2026
                        ? BALANCED_LIBERAL_AREA_CATALOG_2026
                        : BALANCED_LIBERAL_AREA_CATALOG_2022_2025;
        Map<String, List<CategoryCourseDto>> catalog = new LinkedHashMap<>();
        for (String area : availableAreas) {
            catalog.put(area, sourceCatalog.getOrDefault(area, List.of()));
        }
        return catalog;
    }

    public Set<String> commonLiberalEquivalentNames(String courseCode) {
        return COMMON_LIBERAL_EQUIVALENTS.getOrDefault(courseCode, Set.of());
    }

    public String normalizeCourseName(String courseName) {
        return courseName == null ? "" : courseName.trim();
    }

    private String resolveExcludedArea(Student student) {
        if (student == null || student.getAdmissionYear() == null || student.getAdmissionYear() < 2026) {
            return null;
        }

        String major = normalizeCourseName(student.getMajor());
        if (major.isBlank()) {
            return null;
        }

        if (HISTORY_EXCLUDED_MAJORS.contains(major)) {
            return AREA_HISTORY;
        }
        if (NATURE_EXCLUDED_MAJORS.contains(major)) {
            return AREA_NATURE;
        }
        if (SOCIETY_EXCLUDED_MAJORS.contains(major)) {
            return AREA_SOCIETY;
        }
        if (CULTURE_EXCLUDED_MAJORS.contains(major)) {
            return AREA_CULTURE;
        }
        return null;
    }

    private Integer parseTakenYear(String year) {
        if (year == null || year.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(year.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record BalancedLiberalRequirement(
            int requiredCredits,
            int requiredAreaCount,
            int minAcademicYear,
            int maxAcademicYear
    ) {
    }
}
