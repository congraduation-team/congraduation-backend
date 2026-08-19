package com.example.congraduation.service.graduation;

import com.example.congraduation.domain.Student;
import com.example.congraduation.dto.graduation.RequirementCourseDto;
import com.example.congraduation.dto.transcript.CategoryCourseDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AcademicFoundationCoursePolicyService {

    private static final Map<Integer, Map<String, AcademicFoundationRequirement>> REQUIREMENTS_BY_YEAR = loadRequirements();

    public AcademicFoundationEvaluation evaluate(Student student, List<CompletedCourseUploadRowDto> completedCourses) {
        AcademicFoundationRequirement requirement = resolveRequirement(student);
        if (requirement == null) {
            return AcademicFoundationEvaluation.empty();
        }

        Set<String> consumedCourseKeys = new LinkedHashSet<>();
        List<CategoryCourseDto> completedRequirementCourses = new ArrayList<>();
        List<RequirementCourseDto> remainingRequirementCourses = new ArrayList<>();

        BigDecimal earnedCredits = BigDecimal.ZERO;
        for (CourseRequirement courseRequirement : requirement.requiredCourses()) {
            CompletedCourseUploadRowDto matched = findMatchedCourse(
                    courseRequirement.equivalentNames(),
                    completedCourses,
                    consumedCourseKeys
            );
            if (matched == null) {
                remainingRequirementCourses.add(courseRequirement.toRequirementCourseDto());
                continue;
            }

            consumedCourseKeys.add(uniqueCourseKey(matched));
            earnedCredits = earnedCredits.add(toDecimal(courseRequirement.credit()));
            completedRequirementCourses.add(new CategoryCourseDto(
                    matched.courseCode(),
                    matched.courseName(),
                    matched.credit()
            ));
        }

        for (AlternativeRequirement alternativeRequirement : requirement.alternativeRequirements()) {
            List<CompletedCourseUploadRowDto> matchedOptions = findMatchedAlternativeCourses(
                    alternativeRequirement,
                    completedCourses,
                    consumedCourseKeys
            );
            matchedOptions.forEach(course -> completedRequirementCourses.add(new CategoryCourseDto(
                    course.courseCode(),
                    course.courseName(),
                    course.credit()
            )));
            matchedOptions.forEach(course -> consumedCourseKeys.add(uniqueCourseKey(course)));
            earnedCredits = earnedCredits.add(
                    toDecimal(alternativeRequirement.credit()).multiply(BigDecimal.valueOf(matchedOptions.size()))
            );

            int missingCount = Math.max(0, alternativeRequirement.requiredCount() - matchedOptions.size());
            if (missingCount > 0) {
                remainingRequirementCourses.add(new RequirementCourseDto(
                        null,
                        alternativeRequirement.displayName(),
                        formatDecimal(toDecimal(alternativeRequirement.credit()).multiply(BigDecimal.valueOf(missingCount))),
                        alternativeRequirement.recommendedTerm()
                ));
            }
        }

        return new AcademicFoundationEvaluation(
                true,
                earnedCredits,
                requirement.requiredCredits(),
                List.copyOf(completedRequirementCourses),
                List.copyOf(remainingRequirementCourses)
        );
    }

    boolean isPolicyApplied(Student student) {
        return resolveRequirement(student) != null;
    }

    private List<CompletedCourseUploadRowDto> findMatchedAlternativeCourses(
            AlternativeRequirement alternativeRequirement,
            List<CompletedCourseUploadRowDto> completedCourses,
            Set<String> consumedCourseKeys
    ) {
        List<CompletedCourseUploadRowDto> matched = new ArrayList<>();
        for (List<String> optionAliases : alternativeRequirement.options()) {
            CompletedCourseUploadRowDto course = findMatchedCourse(
                    Set.copyOf(optionAliases),
                    completedCourses,
                    consumedCourseKeys
            );
            if (course != null) {
                matched.add(course);
            }
        }

        matched.sort(Comparator
                .comparing((CompletedCourseUploadRowDto course) -> toDecimal(course.credit())).reversed()
                .thenComparing(course -> defaultString(course.courseCode()))
                .thenComparing(course -> defaultString(course.courseName())));

        if (matched.size() <= alternativeRequirement.requiredCount()) {
            return matched;
        }
        return matched.subList(0, alternativeRequirement.requiredCount());
    }

    private CompletedCourseUploadRowDto findMatchedCourse(
            Set<String> aliases,
            List<CompletedCourseUploadRowDto> completedCourses,
            Set<String> consumedCourseKeys
    ) {
        Set<String> normalizedAliases = normalizeAliases(aliases);
        return completedCourses.stream()
                .filter(course -> !consumedCourseKeys.contains(uniqueCourseKey(course)))
                .filter(course -> normalizedAliases.contains(normalizeCourseName(course.courseName())))
                .findFirst()
                .orElse(null);
    }

    private AcademicFoundationRequirement resolveRequirement(Student student) {
        if (student == null) {
            return null;
        }
        return resolveRequirement(student.getMajor(), student.getAdmissionYear());
    }

    public Set<String> requiredCourseNames(String major, Integer admissionYear) {
        AcademicFoundationRequirement requirement = resolveRequirement(major, admissionYear);
        if (requirement == null) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (CourseRequirement courseRequirement : requirement.requiredCourses()) {
            names.addAll(normalizeAliases(courseRequirement.equivalentNames()));
        }
        for (AlternativeRequirement alternativeRequirement : requirement.alternativeRequirements()) {
            for (List<String> option : alternativeRequirement.options()) {
                names.addAll(normalizeAliases(Set.copyOf(option)));
            }
        }
        return Set.copyOf(names);
    }

    public boolean matchesRequiredCourse(String courseName, String major, Integer admissionYear) {
        String normalized = normalizeCourseName(courseName);
        if (normalized.isBlank()) {
            return false;
        }
        return requiredCourseNames(major, admissionYear).contains(normalized);
    }

    /**
     * 아직 이수하지 않은 학과별 학문기초 필수 과목. 로드맵 빈 칸 주입에 사용한다.
     */
    public List<RequiredFoundationSlot> remainingRequiredSlots(
            String major,
            Integer admissionYear,
            List<CompletedCourseUploadRowDto> completedCourses
    ) {
        AcademicFoundationRequirement requirement = resolveRequirement(major, admissionYear);
        if (requirement == null) {
            return List.of();
        }

        List<CompletedCourseUploadRowDto> completed =
                completedCourses == null ? List.of() : completedCourses;
        Set<String> consumedCourseKeys = new LinkedHashSet<>();
        List<RequiredFoundationSlot> remaining = new ArrayList<>();

        for (CourseRequirement courseRequirement : requirement.requiredCourses()) {
            CompletedCourseUploadRowDto matched = findMatchedCourse(
                    courseRequirement.equivalentNames(),
                    completed,
                    consumedCourseKeys
            );
            if (matched == null) {
                remaining.add(new RequiredFoundationSlot(
                        courseRequirement.courseName(),
                        courseRequirement.credit(),
                        courseRequirement.recommendedTerm(),
                        Set.copyOf(courseRequirement.equivalentNames())
                ));
                continue;
            }
            consumedCourseKeys.add(uniqueCourseKey(matched));
        }

        for (AlternativeRequirement alternativeRequirement : requirement.alternativeRequirements()) {
            List<CompletedCourseUploadRowDto> matchedOptions = findMatchedAlternativeCourses(
                    alternativeRequirement,
                    completed,
                    consumedCourseKeys
            );
            matchedOptions.forEach(course -> consumedCourseKeys.add(uniqueCourseKey(course)));
            int missingCount = Math.max(0, alternativeRequirement.requiredCount() - matchedOptions.size());
            if (missingCount <= 0) {
                continue;
            }
            Set<String> aliases = new LinkedHashSet<>();
            aliases.add(alternativeRequirement.displayName());
            for (List<String> option : alternativeRequirement.options()) {
                aliases.addAll(option);
            }
            remaining.add(new RequiredFoundationSlot(
                    alternativeRequirement.displayName(),
                    alternativeRequirement.credit(),
                    alternativeRequirement.recommendedTerm(),
                    Set.copyOf(aliases)
            ));
        }
        return List.copyOf(remaining);
    }

    private AcademicFoundationRequirement resolveRequirement(String major, Integer admissionYear) {
        if (admissionYear == null || major == null || major.isBlank()) {
            return null;
        }

        Map<String, AcademicFoundationRequirement> requirements =
                REQUIREMENTS_BY_YEAR.get(admissionYear);
        if (requirements == null) {
            return null;
        }

        return requirements.get(normalizeMajor(major));
    }

    private static Map<Integer, Map<String, AcademicFoundationRequirement>> loadRequirements() {
        Map<Integer, Map<String, AcademicFoundationRequirement>> requirementsByYear = new LinkedHashMap<>();
        requirementsByYear.put(2021, load2021Requirements());
        requirementsByYear.put(2022, load2022Requirements());
        requirementsByYear.put(2023, load2023Requirements());
        requirementsByYear.put(2024, load2024Requirements());
        requirementsByYear.put(2025, load2025Requirements());
        requirementsByYear.put(2026, load2026Requirements());
        return requirementsByYear;
    }

    private static Map<String, AcademicFoundationRequirement> load2023Requirements() {
        Map<String, AcademicFoundationRequirement> requirements = new LinkedHashMap<>();

        AcademicFoundationRequirement humanitiesBase = requirement(
                course("전산개론-O", "3", "1-1", "컴퓨터사고기반기초코딩"),
                course("인공지능과빅데이터", "3", "2-1")
        );
        putAll(requirements, humanitiesBase,
                "국어국문학과", "영어영문학전공", "일어일문학전공", "중국통상학전공", "역사학과", "교육학과");
        requirements.put("행정학과", requirement(
                course("전산개론-O", "3", "1-1", "컴퓨터사고기반기초코딩"),
                course("프로그래밍활용-P", "3", "1-2"),
                course("인공지능과빅데이터", "3", "2-1")
        ));
        requirements.put("미디어커뮤니케이션학과", requirement(
                course("전산개론-O", "3", "1-1", "컴퓨터사고기반기초코딩"),
                course("인공지능과빅데이터", "3", "2-2")
        ));
        requirements.put("경영학부", requirement(
                course("전산개론-O", "3", "1-1", "컴퓨터사고기반기초코딩"),
                course("프로그래밍활용-P", "3", "1-2"),
                course("인공지능과빅데이터", "3", "2-2")
        ));
        requirements.put("경제학과", requirement(
                course("전산개론-O", "3", "1-1", "컴퓨터사고기반기초코딩"),
                course("인공지능과빅데이터", "3", "2-2"),
                course("경영수학", "3", "1-1", "기초미적분학"),
                course("통계학개론", "3", "1-2")
        ));
        putAll(requirements, requirement(
                        course("전산개론-O", "3", "1-1", "컴퓨터사고기반기초코딩"),
                        course("프로그래밍활용-P", "3", "1-2"),
                        course("인공지능과빅데이터", "3", "2-2")
                ),
                "호텔관광경영학전공", "외식경영학전공");

        requirements.put("수학통계학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("고급프로그래밍활용", "3", "1-2"),
                course("인공지능과빅데이터", "3", "2-2"),
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("다변수미적분학", "3", "1-2", "미적분학2")
        ));
        requirements.put("물리천문학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("고급프로그래밍활용", "3", "1-2"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("다변수미적분학", "3", "1-2", "미적분학2")
        ));
        requirements.put("화학과", requirement(
                List.of(
                        course("SW기초코딩", "3", "1-1"),
                        course("고급프로그래밍활용", "3", "1-2"),
                        course("인공지능과빅데이터", "3", "2-1"),
                        course("일반수미적분학", "3", "1-1", "미적분학1"),
                        course("다변수미적분학", "3", "1-2", "미적분학2"),
                        course("일반화학및실험1", "3", "1-1", "일반화학1"),
                        course("일반화학및실험2", "3", "1-2", "일반화학2")
                ),
                List.of(alternative(
                        "일반생물학 / 통계학개론 중 1과목",
                        "3",
                        "1-2",
                        1,
                        List.of("일반생물학"),
                        List.of("통계학개론")
                ))
        ));
        putAll(requirements, requirement(
                        course("SW기초코딩", "3", "1-1"),
                        course("인공지능과빅데이터", "3", "2-1"),
                        course("일반화학및실험1", "3", "1-1", "일반화학1"),
                        course("일반화학및실험2", "3", "1-2", "일반화학2")
                ),
                "식품생명공학전공", "바이오융합공학전공", "바이오산업자원공학전공");
        requirements.put("스마트생명산업융합학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("기초미적분학", "3", "1-1"),
                course("일반화학및실험1", "3", "1-1", "일반화학1"),
                course("일반화학및실험2", "3", "1-2", "일반화학2")
        ));

        requirements.put("전자정보통신공학과", requirement(
                course("프로그래밍활용-C", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("공업수학1", "3", "1-2"),
                course("공업수학2", "3", "2-1"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1"),
                course("일반물리학및실험2", "3", "1-2", "일반물리학2"),
                course("일반화학", "3", "1-1", "일반화학1", "일반화학및실험1")
        ));
        requirements.put("반도체시스템공학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("고급프로그래밍활용", "3", "1-2"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("기초미적분학", "3", "1-1"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1"),
                course("일반화학", "3", "1-1", "일반화학1")
        ));
        requirements.put("컴퓨터공학과", requirement(
                course("고급프로그래밍활용", "3", "1-2"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("기초미적분학", "3", "1-1"),
                course("공업수학1", "3", "1-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1")
        ));
        requirements.put("정보보호학과", requirement(
                course("고급프로그래밍활용", "3", "1-2"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("기초미적분학", "3", "1-1"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1"),
                course("일반생물학", "3", "1-2")
        ));
        requirements.put("소프트웨어학과", requirement(
                course("고급프로그래밍활용", "3", "1-2"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("기초미적분학", "3", "1-1"),
                course("공업수학1", "3", "1-2"),
                course("통계학개론", "3", "2-2")
        ));
        requirements.put("데이터사이언스학과", requirement(
                course("고급프로그래밍활용", "3", "1-2"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("공업수학1", "3", "1-2"),
                course("통계학개론", "3", "1-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1")
        ));
        requirements.put("지능기전공학과", requirement(
                course("고급프로그래밍활용", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("다변수미적분학", "3", "1-2", "미적분학2"),
                course("공업수학1", "3", "2-1")
        ));
        requirements.put("인공지능학과", requirement(
                course("고급프로그래밍활용", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("기초미적분학", "3", "1-1"),
                course("공업수학1", "3", "1-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1"),
                course("고급인공지능활용", "3", "2-2")
        ));
        putAll(requirements, requirement(), "디자인이노베이션전공", "만화애니메이션텍전공");

        requirements.put("건축공학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("고급프로그래밍활용", "3", "1-2"),
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("다변수미적분학", "3", "1-2", "미적분학2"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("통계학개론", "3", "3-1"),
                course("일반물리학및실험1", "3", "2-1", "일반물리학1"),
                course("일반물리학및실험2", "3", "2-2", "일반물리학2"),
                course("일반화학및실험1", "3", "2-1", "일반화학1"),
                course("일반화학및실험2", "3", "2-2", "일반화학2")
        ));
        requirements.put("건축학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("통계학개론", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-2")
        ));
        putAll(requirements, requirement(
                        course("SW기초코딩", "3", "1-1"),
                        course("고급프로그래밍활용", "3", "1-2"),
                        course("일반수미적분학", "3", "1-1", "미적분학1"),
                        course("다변수미적분학", "3", "1-2", "미적분학2"),
                        course("공업수학1", "3", "2-1"),
                        course("공업수학2", "3", "2-2"),
                        course("일반물리학및실험1", "3", "1-1", "일반물리학1"),
                        course("일반물리학및실험2", "3", "1-2", "일반물리학2")
                ),
                "건설환경공학과", "환경에너지공간융합학과", "지구자원시스템공학과",
                "기계공학과", "우주항공공학전공", "나노신소재공학과", "양자원자력공학과");
        requirements.put("항공시스템공학전공", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("일반수미적분학", "3", "1-2", "미적분학1"),
                course("다변수미적분학", "3", "2-1", "미적분학2"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1"),
                course("일반물리학및실험2", "3", "1-2", "일반물리학2")
        ));
        requirements.put("국방시스템공학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("프로그래밍활용-C", "3", "1-2"),
                course("일반수미적분학", "3", "1-2", "미적분학1"),
                course("다변수미적분학", "3", "2-1", "미적분학2"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1"),
                course("일반물리학및실험2", "3", "1-2", "일반물리학2"),
                course("인공지능과빅데이터", "3", "2-1")
        ));
        putAll(requirements, requirement(
                        course("전산개론-O", "3", "1-1", "컴퓨터사고기반기초코딩"),
                        course("인공지능과빅데이터", "3", "2-2")
                ),
                "회화과", "패션디자인학과", "음악과", "체육학과", "무용과", "영화예술학과", "법학전공");

        return requirements;
    }

    private static Map<String, AcademicFoundationRequirement> load2024Requirements() {
        return loadModernRequirements(2024);
    }

    private static Map<String, AcademicFoundationRequirement> load2025Requirements() {
        return loadModernRequirements(2025);
    }

    private static Map<String, AcademicFoundationRequirement> load2026Requirements() {
        return loadModernRequirements(2026);
    }

    private static Map<String, AcademicFoundationRequirement> loadModernRequirements(int admissionYear) {
        Map<String, AcademicFoundationRequirement> requirements = new LinkedHashMap<>();

        AcademicFoundationRequirement humanities = requirement(
                course("컴퓨터사고기반기초코딩", "3", "1-1", "전산개론-O"),
                course("인공지능과빅데이터", "3", admissionYear == 2023 ? "2-1" : "1-2")
        );
        putAll(requirements, humanities,
                "국어국문학과", "영어영문학전공", "영어데이터융합전공", "일어일문학전공", "중국통상학전공",
                "역사학과", "교육학과", "행정학과", "미디어커뮤니케이션학과", "법학전공",
                "회화과", "패션디자인학과", "음악과", "체육학과", "무용과", "영화예술학과");

        AcademicFoundationRequirement businessHotel = requirement(
                course("사회과학수학", "3", "1-1"),
                course("컴퓨터사고기반기초코딩", "3", "1-1", "전산개론-O"),
                course("인공지능과빅데이터", "3", "1-2")
        );
        putAll(requirements, businessHotel,
                "경영학부", "경제학과", "호텔관광경영학전공", "외식경영학전공");

        AcademicFoundationRequirement naturalLife = requirement(
                course("미적분학1", "3", "1-1", "일반수미적분학"),
                course("SW기초코딩", "3", "1-2"),
                course("인공지능과빅데이터", "3", "2-1")
        );
        putAll(requirements, naturalLife,
                "수학통계학과", "물리천문학과", "화학과", "식품생명공학전공",
                "바이오융합공학전공", "바이오산업자원공학전공", "스마트생명산업융합학과");

        AcademicFoundationRequirement it = requirement(
                course("미적분학1", "3", "1-1", "일반수미적분학"),
                course(admissionYear == 2024 ? "고급프로그래밍활용" : "고급프로그래밍활용", "3", "1-2"),
                course("인공지능과빅데이터", "3", "1-2")
        );
        if (admissionYear == 2024) {
            putAll(requirements, it,
                    "전자정보통신공학과", "반도체시스템공학과", "컴퓨터공학과", "정보보호학과",
                    "소프트웨어학과", "AI로봇학과", "인공지능데이터사이언스학과");
        } else if (admissionYear == 2025) {
            putAll(requirements, it,
                    "AI융합전자공학과", "반도체시스템공학과", "컴퓨터공학과", "정보보호학과",
                    "양자지능정보학과", "AI로봇학과", "인공지능데이터사이언스학과",
                    "지능정보융합학과", "콘텐츠소프트웨어학과");
        } else {
            putAll(requirements, it,
                    "AI융합전자공학과", "반도체시스템공학과", "컴퓨터공학과", "정보보호학과",
                    "양자지능정보학과", "AI로봇학과", "인공지능데이터사이언스학과",
                    "지능정보융합학과", "콘텐츠소프트웨어학과");
        }

        AcademicFoundationRequirement engineering = requirement(
                course("미적분학1", "3", "1-1", "일반수미적분학"),
                course("미적분학2", "3", "1-2", "다변수미적분학"),
                course("SW기초코딩", "3", "1-1"),
                course("고급프로그래밍활용", "3", "1-2")
        );
        putAll(requirements, engineering,
                "건축공학과", "건설환경공학과", "환경에너지공간융합학과", "환경융합공학과",
                "지구자원시스템공학과", "기계공학과", "우주항공공학전공", "지능형드론융합전공",
                "나노신소재공학과", "양자원자력공학과");
        requirements.put("나노신소재공학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("고급프로그래밍활용", "3", "1-2"),
                course("미적분학1", "3", "1-1", "일반수미적분학"),
                course("미적분학2", "3", "1-2", "다변수미적분학"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학1", "3", "1-1", "일반물리학및실험1"),
                course("일반물리학2", "3", "1-2", "일반물리학및실험2"),
                course("일반화학1", "3", "1-1", "일반화학및실험1"),
                course("일반화학2", "3", "1-2", "일반화학및실험2")
        ));

        requirements.put("건축학과", requirement(
                course("미적분학1", "3", "1-1", "일반수미적분학"),
                course("미적분학2", "3", "1-2", "다변수미적분학"),
                course("SW기초코딩", "3", "1-1"),
                course("고급프로그래밍활용", "3", "1-2"),
                course("인공지능과빅데이터", "3", "2-2")
        ));

        if (admissionYear == 2024 || admissionYear == 2025) {
            requirements.put("국방시스템공학과", requirement(
                    course("미적분학1", "3", "1-1", "일반수미적분학"),
                    course("미적분학2", "3", "1-2", "다변수미적분학"),
                    course("SW기초코딩", "3", "1-1"),
                    course("프로그래밍활용-C", "3", "1-2")
            ));
            requirements.put("항공시스템공학전공", requirement(
                    course("미적분학1", "3", "1-1", "일반수미적분학"),
                    course("미적분학2", "3", "1-2", "다변수미적분학"),
                    course("SW기초코딩", "3", "1-1"),
                    course("인공지능과빅데이터", "3", "2-1")
            ));
        }

        if (admissionYear == 2026) {
            requirements.put("국방AI융합시스템공학과", requirement(
                    course("미적분학1", "3", "1-1", "일반수미적분학"),
                    course("미적분학2", "3", "1-2", "다변수미적분학"),
                    course("SW기초코딩", "3", "1-1"),
                    course("프로그래밍활용-C", "3", "1-2"),
                    course("인공지능과빅데이터", "3", "2-1")
            ));
            requirements.put("국방AI로봇융합공학과", requirement(
                    course("미적분학1", "3", "1-1", "일반수미적분학"),
                    course("미적분학2", "3", "1-2", "다변수미적분학"),
                    course("SW기초코딩", "3", "1-1"),
                    course("프로그래밍활용-C", "3", "1-2"),
                    course("인공지능과빅데이터", "3", "2-1")
            ));
            requirements.put("항공시스템공학전공", requirement(
                    course("미적분학1", "3", "1-1", "일반수미적분학"),
                    course("미적분학2", "3", "1-2", "다변수미적분학"),
                    course("SW기초코딩", "3", "1-1"),
                    course("프로그래밍활용-C", "3", "1-2"),
                    course("인공지능과빅데이터", "3", "2-1")
            ));
        }

        putAll(requirements, requirement(), "한국언어문화전공", "국제통상전공", "국제협력전공");

        return requirements;
    }

    private static Map<String, AcademicFoundationRequirement> load2021Requirements() {
        Map<String, AcademicFoundationRequirement> requirements = new LinkedHashMap<>();

        AcademicFoundationRequirement humanitiesBase = requirement(
                course("컴퓨터사고기반기초코딩", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-1")
        );
        putAll(requirements, humanitiesBase,
                "국어국문학과", "영어영문학전공", "일어일문학전공", "중국통상학전공", "역사학과", "교육학과");

        requirements.put("행정학과", requirement(
                course("컴퓨터사고기반기초코딩", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-1")
        ));
        requirements.put("미디어커뮤니케이션학과", requirement(
                course("인공지능과빅데이터", "3", "2-2")
        ));
        requirements.put("경영학부", requirement());
        requirements.put("경제학과", requirement(
                course("기초미적분학", "3", "1-1"),
                course("통계학개론", "3", "1-2")
        ));
        putAll(requirements, requirement(), "호텔관광경영학전공", "외식경영학전공");

        requirements.put("수학통계학과", requirement(
                course("일반수미적분학", "3", "1-1"),
                course("다변수미적분학", "3", "1-2")
        ));
        requirements.put("물리천문학과", requirement(
                course("일반수미적분학", "3", "1-1"),
                course("다변수미적분학", "3", "1-2")
        ));
        requirements.put("화학과", requirement(
                List.of(
                        course("일반수미적분학", "3", "1-1"),
                        course("다변수미적분학", "3", "1-2"),
                        course("일반화학및실험1", "3", "1-1", "일반화학1"),
                        course("일반화학및실험2", "3", "1-2", "일반화학2")
                ),
                List.of(alternative(
                        "일반생물학 / 통계학개론 중 1과목",
                        "3",
                        "1-2",
                        1,
                        List.of("일반생물학"),
                        List.of("통계학개론")
                ))
        ));
        putAll(requirements, requirement(
                        course("기초미적분학", "3", "1-1"),
                        course("일반화학및실험1", "3", "1-1", "일반화학1"),
                        course("일반화학및실험2", "3", "1-2", "일반화학2")
                ),
                "식품생명공학전공", "바이오융합공학전공", "스마트생명산업융합학과");
        requirements.put("전자정보통신공학과", requirement(
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("공업수학1", "3", "1-2"),
                course("공업수학2", "3", "2-1"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1"),
                course("일반물리학및실험2", "3", "1-2", "일반물리학2"),
                course("일반화학", "3", "1-1", "일반화학1")
        ));
        requirements.put("컴퓨터공학과", requirement(
                course("기초미적분학", "3", "1-1"),
                course("공업수학1", "3", "1-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1")
        ));
        requirements.put("정보보호학과", requirement(
                course("기초미적분학", "3", "1-1"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1"),
                course("일반생물학", "3", "1-2")
        ));
        requirements.put("소프트웨어학과", requirement(
                course("기초미적분학", "3", "1-1"),
                course("공업수학1", "3", "1-2"),
                course("통계학개론", "3", "2-2")
        ));
        requirements.put("데이터사이언스학과", requirement(
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("공업수학1", "3", "1-2"),
                course("통계학개론", "3", "1-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1")
        ));

        requirements.put("무인이동체공학전공", requirement(
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("공업수학1", "3", "1-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1")
        ));
        requirements.put("스마트기기공학전공", requirement(
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("공업수학1", "3", "1-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1")
        ));
        putAll(requirements, requirement(), "디자인이노베이션전공", "만화애니메이션텍전공");
        requirements.put("인공지능학과", requirement(
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("공업수학1", "3", "1-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1")
        ));
        requirements.put("건축공학과", requirement(
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("다변수미적분학", "3", "1-2", "미적분학2"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학및실험1", "3", "2-1", "일반물리학1"),
                course("일반물리학및실험2", "3", "2-2", "일반물리학2"),
                course("일반화학및실험1", "3", "2-1", "일반화학1"),
                course("일반화학및실험2", "3", "2-2", "일반화학2")
        ));
        requirements.put("건축학과", requirement(
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("통계학개론", "3", "3-1")
        ));
        requirements.put("건설환경공학과", requirement(
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("다변수미적분학", "3", "1-2", "미적분학2"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1"),
                course("일반물리학및실험2", "3", "1-2", "일반물리학2"),
                course("일반화학", "3", "2-1", "일반화학1")
        ));
        requirements.put("환경에너지공간융합학과", requirement(
                course("기초미적분학", "3", "1-1"),
                course("다변수미적분학", "3", "1-2", "미적분학2"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1"),
                course("일반물리학및실험2", "3", "1-2", "일반물리학2"),
                course("일반화학", "3", "1-1", "일반화학1"),
                course("통계학개론", "3", "1-2")
        ));
        requirements.put("지구자원시스템공학과", requirement(
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("다변수미적분학", "3", "1-2", "미적분학2"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1"),
                course("일반물리학및실험2", "3", "1-2", "일반물리학2"),
                course("일반화학", "3", "2-1", "일반화학1")
        ));
        requirements.put("기계공학과", requirement(
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("다변수미적분학", "3", "1-2", "미적분학2"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1"),
                course("일반물리학및실험2", "3", "1-2", "일반물리학2")
        ));
        requirements.put("우주항공공학전공", requirement(
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("다변수미적분학", "3", "1-2", "미적분학2"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1"),
                course("일반물리학및실험2", "3", "1-2", "일반물리학2")
        ));
        requirements.put("나노신소재공학과", requirement(
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("다변수미적분학", "3", "1-2", "미적분학2"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1"),
                course("일반물리학및실험2", "3", "1-2", "일반물리학2"),
                course("일반화학및실험1", "3", "1-1", "일반화학1"),
                course("일반화학및실험2", "3", "1-2", "일반화학2")
        ));
        requirements.put("양자원자력공학과", requirement(
                course("일반수미적분학", "3", "1-1", "미적분학1"),
                course("다변수미적분학", "3", "1-2", "미적분학2"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1"),
                course("일반물리학및실험2", "3", "1-2", "일반물리학2")
        ));
        requirements.put("국방시스템공학과", requirement(
                course("일반수미적분학", "3", "1-2", "미적분학1"),
                course("다변수미적분학", "3", "2-1", "미적분학2"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1"),
                course("일반물리학및실험2", "3", "1-2", "일반물리학2")
        ));
        requirements.put("항공시스템공학전공", requirement(
                course("일반수미적분학", "3", "1-2", "미적분학1"),
                course("다변수미적분학", "3", "2-1", "미적분학2"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학및실험1", "3", "1-1", "일반물리학1"),
                course("일반물리학및실험2", "3", "1-2", "일반물리학2")
        ));

        return requirements;
    }

    private static Map<String, AcademicFoundationRequirement> load2022Requirements() {
        Map<String, AcademicFoundationRequirement> requirements = new LinkedHashMap<>();

        AcademicFoundationRequirement humanitiesBase = requirement(
                course("컴퓨터사고기반기초코딩", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-1")
        );
        putAll(requirements, humanitiesBase,
                "국어국문학과", "영어영문학전공", "일어일문학전공", "중국통상학전공", "역사학과", "교육학과");
        requirements.put("행정학과", requirement(
                course("컴퓨터사고기반기초코딩", "3", "1-1"),
                course("프로그래밍활용-P", "3", "1-2"),
                course("인공지능과빅데이터", "3", "2-1")
        ));
        requirements.put("미디어커뮤니케이션학과", requirement(
                course("컴퓨터사고기반기초코딩", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-2")
        ));
        requirements.put("경영학부", requirement(
                course("컴퓨터사고기반기초코딩", "3", "1-1"),
                course("프로그래밍활용-P", "3", "1-2"),
                course("인공지능과빅데이터", "3", "2-2")
        ));
        requirements.put("경제학과", requirement(
                course("컴퓨터사고기반기초코딩", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-2"),
                course("기초미적분학", "3", "1-1"),
                course("통계학개론", "3", "1-2")
        ));
        putAll(requirements, requirement(
                        course("컴퓨터사고기반기초코딩", "3", "1-1"),
                        course("프로그래밍활용-P", "3", "1-2"),
                        course("인공지능과빅데이터", "3", "2-2")
                ),
                "호텔관광경영학전공", "외식경영학전공");

        requirements.put("수학통계학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("고급프로그래밍활용", "3", "1-2"),
                course("인공지능과빅데이터", "3", "2-2"),
                course("미적분학1", "3", "1-1", "일반수미적분학"),
                course("미적분학2", "3", "1-2", "다변수미적분학")
        ));
        requirements.put("물리천문학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("고급프로그래밍활용", "3", "1-2"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("미적분학1", "3", "1-1", "일반수미적분학"),
                course("미적분학2", "3", "1-2", "다변수미적분학")
        ));
        requirements.put("화학과", requirement(
                List.of(
                        course("SW기초코딩", "3", "1-1"),
                        course("고급프로그래밍활용", "3", "1-2"),
                        course("인공지능과빅데이터", "3", "2-1"),
                        course("미적분학1", "3", "1-1", "일반수미적분학"),
                        course("미적분학2", "3", "1-2", "다변수미적분학"),
                        course("일반화학1", "3", "1-1", "일반화학및실험1"),
                        course("일반화학2", "3", "1-2", "일반화학및실험2")
                ),
                List.of(alternative(
                        "일반생물학 / 통계학개론 중 1과목",
                        "3",
                        "1-2",
                        1,
                        List.of("일반생물학"),
                        List.of("통계학개론")
                ))
        ));
        putAll(requirements, requirement(
                        course("SW기초코딩", "3", "1-1"),
                        course("인공지능과빅데이터", "3", "2-1"),
                        course("일반화학1", "3", "1-1", "일반화학및실험1"),
                        course("일반화학2", "3", "1-2", "일반화학및실험2")
                ),
                "식품생명공학전공", "바이오융합공학전공", "바이오산업자원공학전공");
        requirements.put("스마트생명산업융합학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("기초미적분학", "3", "1-1"),
                course("일반화학1", "3", "1-1", "일반화학및실험1"),
                course("일반화학2", "3", "1-2", "일반화학및실험2")
        ));

        requirements.put("전자정보통신공학과", requirement(
                course("프로그래밍활용-C", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("미적분학1", "3", "1-1", "일반수미적분학"),
                course("공업수학1", "3", "1-2"),
                course("공업수학2", "3", "2-1"),
                course("일반물리학1", "3", "1-1", "일반물리학및실험1"),
                course("일반물리학2", "3", "1-2", "일반물리학및실험2"),
                course("일반화학1", "3", "1-1", "일반화학", "일반화학및실험1")
        ));
        requirements.put("컴퓨터공학과", requirement(
                course("고급프로그래밍활용", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("기초미적분학", "3", "1-1"),
                course("공업수학1", "3", "1-2"),
                course("일반물리학1", "3", "1-1", "일반물리학및실험1")
        ));
        requirements.put("정보보호학과", requirement(
                course("고급프로그래밍활용", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("기초미적분학", "3", "1-1"),
                course("일반물리학1", "3", "1-1", "일반물리학및실험1"),
                course("일반생물학", "3", "1-2")
        ));
        requirements.put("소프트웨어학과", requirement(
                course("고급프로그래밍활용", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("기초미적분학", "3", "1-1"),
                course("공업수학1", "3", "1-2"),
                course("통계학개론", "3", "2-2")
        ));
        requirements.put("데이터사이언스학과", requirement(
                course("고급프로그래밍활용", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("미적분학1", "3", "1-1", "일반수미적분학"),
                course("공업수학1", "3", "1-2"),
                course("통계학개론", "3", "1-2"),
                course("일반물리학1", "3", "1-1", "일반물리학및실험1")
        ));

        requirements.put("무인이동체공학전공", requirement(
                course("고급프로그래밍활용", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("미적분학1", "3", "1-1", "일반수미적분학"),
                course("미적분학2", "3", "1-2", "다변수미적분학"),
                course("공업수학1", "3", "1-2"),
                course("일반물리학1", "3", "1-1", "일반물리학및실험1")
        ));
        requirements.put("스마트기기공학전공", requirement(
                course("고급프로그래밍활용", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("미적분학1", "3", "1-1", "일반수미적분학"),
                course("미적분학2", "3", "1-2", "다변수미적분학"),
                course("공업수학1", "3", "1-2"),
                course("일반물리학1", "3", "1-1", "일반물리학및실험1")
        ));
        requirements.put("인공지능학과", requirement(
                course("고급프로그래밍활용", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("기초미적분학", "3", "1-1"),
                course("공업수학1", "3", "1-2"),
                course("일반물리학1", "3", "1-1", "일반물리학및실험1"),
                course("고급인공지능활용", "3", "2-2")
        ));
        putAll(requirements, requirement(), "디자인이노베이션전공", "만화애니메이션텍전공");

        requirements.put("건축공학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("고급프로그래밍활용", "3", "1-2"),
                course("미적분학1", "3", "1-1", "일반수미적분학"),
                course("다변수미적분학", "3", "1-2", "미적분학2"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("통계학개론", "3", "3-1"),
                course("일반물리학1", "3", "2-1", "일반물리학및실험1"),
                course("일반물리학2", "3", "2-2", "일반물리학및실험2"),
                course("일반화학1", "3", "2-1", "일반화학및실험1"),
                course("일반화학2", "3", "2-2", "일반화학및실험2"),
                course("고급인공지능활용", "3", "2-2")
        ));
        requirements.put("건축학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("통계학개론", "3", "1-1"),
                course("인공지능과빅데이터", "3", "2-2")
        ));
        requirements.put("건설환경공학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("고급프로그래밍활용", "3", "1-2"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("미적분학1", "3", "1-1", "일반수미적분학"),
                course("미적분학2", "3", "1-2", "다변수미적분학"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학1", "3", "1-1", "일반물리학및실험1"),
                course("일반물리학2", "3", "1-2", "일반물리학및실험2"),
                course("일반화학1", "3", "1-1", "일반화학")
        ));
        requirements.put("환경에너지공간융합학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("고급프로그래밍활용", "3", "1-2"),
                course("기초미적분학", "3", "1-1"),
                course("미적분학2", "3", "1-2", "다변수미적분학"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("통계학개론", "3", "2-2"),
                course("일반물리학1", "3", "1-1", "일반물리학및실험1"),
                course("일반화학1", "3", "1-1", "일반화학")
        ));
        requirements.put("지구자원시스템공학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("고급프로그래밍활용", "3", "1-2"),
                course("미적분학1", "3", "1-1", "일반수미적분학"),
                course("미적분학2", "3", "1-2", "다변수미적분학"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학1", "3", "1-1", "일반물리학및실험1"),
                course("일반물리학2", "3", "1-2", "일반물리학및실험2"),
                course("일반화학1", "3", "1-1", "일반화학")
        ));
        requirements.put("기계공학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("프로그래밍활용-C", "3", "1-2"),
                course("고급프로그래밍활용", "3", "1-2"),
                course("미적분학1", "3", "1-1", "일반수미적분학"),
                course("미적분학2", "3", "1-2", "다변수미적분학"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학1", "3", "1-1", "일반물리학및실험1"),
                course("일반물리학2", "3", "1-2", "일반물리학및실험2")
        ));
        requirements.put("우주항공공학전공", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("프로그래밍활용-C", "3", "1-2"),
                course("고급프로그래밍활용", "3", "1-2"),
                course("미적분학1", "3", "1-1", "일반수미적분학"),
                course("미적분학2", "3", "1-2", "다변수미적분학"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학1", "3", "1-1", "일반물리학및실험1"),
                course("일반물리학2", "3", "1-2", "일반물리학및실험2")
        ));
        requirements.put("나노신소재공학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("고급인공지능활용", "3", "2-2"),
                course("미적분학1", "3", "1-1", "일반수미적분학"),
                course("미적분학2", "3", "1-2", "다변수미적분학"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학1", "3", "1-1", "일반물리학및실험1"),
                course("일반물리학2", "3", "1-2", "일반물리학및실험2"),
                course("일반화학1", "3", "1-1", "일반화학및실험1"),
                course("일반화학2", "3", "1-2", "일반화학및실험2")
        ));
        requirements.put("양자원자력공학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("고급인공지능활용", "3", "2-2"),
                course("미적분학1", "3", "1-1", "일반수미적분학"),
                course("미적분학2", "3", "1-2", "다변수미적분학"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학1", "3", "1-1", "일반물리학및실험1"),
                course("일반물리학2", "3", "1-2", "일반물리학및실험2")
        ));
        requirements.put("국방시스템공학과", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("프로그래밍활용-C", "3", "1-2"),
                course("고급인공지능활용", "3", "2-1"),
                course("미적분학1", "3", "1-2", "일반수미적분학"),
                course("미적분학2", "3", "2-1", "다변수미적분학"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학1", "3", "1-1", "일반물리학및실험1"),
                course("일반물리학2", "3", "1-2", "일반물리학및실험2")
        ));
        requirements.put("항공시스템공학전공", requirement(
                course("SW기초코딩", "3", "1-1"),
                course("프로그래밍활용-C", "3", "1-2"),
                course("인공지능과빅데이터", "3", "2-1"),
                course("미적분학1", "3", "1-2", "일반수미적분학"),
                course("미적분학2", "3", "2-1", "다변수미적분학"),
                course("공업수학1", "3", "2-1"),
                course("공업수학2", "3", "2-2"),
                course("일반물리학1", "3", "1-1", "일반물리학및실험1"),
                course("일반물리학2", "3", "1-2", "일반물리학및실험2")
        ));

        putAll(requirements, requirement(
                        course("컴퓨터사고기반기초코딩", "3", "1-1"),
                        course("인공지능과빅데이터", "3", "2-2")
                ),
                "회화과", "패션디자인학과", "음악과", "체육학과", "무용과", "영화예술학과", "법학전공");

        return requirements;
    }

    private static void putAll(
            Map<String, AcademicFoundationRequirement> requirements,
            AcademicFoundationRequirement requirement,
            String... majors
    ) {
        for (String major : majors) {
            requirements.put(major, requirement);
        }
    }

    private static AcademicFoundationRequirement requirement(CourseRequirement... requiredCourses) {
        return requirement(List.of(requiredCourses), List.of());
    }

    private static AcademicFoundationRequirement requirement(
            List<CourseRequirement> requiredCourses,
            List<AlternativeRequirement> alternativeRequirements
    ) {
        int requiredCredits = requiredCourses.stream()
                .map(CourseRequirement::credit)
                .mapToInt(Integer::parseInt)
                .sum();
        requiredCredits += alternativeRequirements.stream()
                .mapToInt(alternative -> Integer.parseInt(alternative.credit()) * alternative.requiredCount())
                .sum();
        return new AcademicFoundationRequirement(requiredCredits, List.copyOf(requiredCourses), List.copyOf(alternativeRequirements));
    }

    private static CourseRequirement course(String courseName, String credit, String recommendedTerm, String... aliases) {
        Set<String> equivalentNames = new LinkedHashSet<>();
        equivalentNames.add(courseName);
        equivalentNames.addAll(List.of(aliases));
        return new CourseRequirement(courseName, credit, recommendedTerm, Set.copyOf(equivalentNames));
    }

    @SafeVarargs
    private static AlternativeRequirement alternative(
            String displayName,
            String credit,
            String recommendedTerm,
            int requiredCount,
            List<String>... options
    ) {
        List<List<String>> optionList = new ArrayList<>();
        for (List<String> option : options) {
            optionList.add(List.copyOf(option));
        }
        return new AlternativeRequirement(displayName, credit, recommendedTerm, requiredCount, List.copyOf(optionList));
    }

    private Set<String> normalizeAliases(Set<String> aliases) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String alias : aliases) {
            String normalizedAlias = normalizeCourseName(alias);
            if (!normalizedAlias.isBlank()) {
                normalized.add(normalizedAlias);
            }
        }
        return normalized;
    }

    private String uniqueCourseKey(CompletedCourseUploadRowDto course) {
        if (course.courseCode() != null && !course.courseCode().isBlank()) {
            return "CODE:" + course.courseCode().trim();
        }
        return "NAME:" + normalizeCourseName(course.courseName());
    }

    private String normalizeMajor(String major) {
        if (major == null) {
            return "";
        }

        return switch (major.trim()) {
            case "컴퓨터공학" -> "컴퓨터공학과";
            case "영어영문학과" -> "영어영문학전공";
            case "일어일문학과" -> "일어일문학전공";
            case "수학전공", "응용통계학전공" -> "수학통계학과";
            case "전자공학과", "전자정보공학과" -> "전자정보통신공학과";
            case "데이터사이언스전공" -> "데이터사이언스학과";
            case "건축공학전공" -> "건축공학과";
            case "건축학전공" -> "건축학과";
            case "기계공학전공" -> "기계공학과";
            case "항공우주공학전공" -> "우주항공공학전공";
            case "법학", "법학과" -> "법학전공";
            default -> major.trim();
        };
    }

    private String normalizeCourseName(String courseName) {
        return courseName == null ? "" : courseName.replaceAll("\\s+", "").trim().toLowerCase();
    }

    private BigDecimal toDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private String formatDecimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    public record AcademicFoundationEvaluation(
            boolean policyApplied,
            BigDecimal earnedCredits,
            Integer requiredCredits,
            List<CategoryCourseDto> completedCourses,
            List<RequirementCourseDto> remainingCourses
    ) {
        private static AcademicFoundationEvaluation empty() {
            return new AcademicFoundationEvaluation(false, BigDecimal.ZERO, null, List.of(), List.of());
        }
    }

    public record RequiredFoundationSlot(
            String courseName,
            String credit,
            String recommendedTerm,
            Set<String> equivalentNames
    ) {
    }

    private record AcademicFoundationRequirement(
            Integer requiredCredits,
            List<CourseRequirement> requiredCourses,
            List<AlternativeRequirement> alternativeRequirements
    ) {
    }

    private record CourseRequirement(
            String courseName,
            String credit,
            String recommendedTerm,
            Set<String> equivalentNames
    ) {
        private RequirementCourseDto toRequirementCourseDto() {
            return new RequirementCourseDto(null, courseName, credit, recommendedTerm);
        }
    }

    private record AlternativeRequirement(
            String displayName,
            String credit,
            String recommendedTerm,
            int requiredCount,
            List<List<String>> options
    ) {
    }
}
