package com.example.congraduation.service.graduation;

import com.example.congraduation.dto.transcript.CategoryCourseDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 수강편람 복수전공 지정 필수과목 정책.
 * <p>
 * 학수번호는 2025-2 / 2026-1 강의시간표로 검증한 값을 사용한다.
 */
@Service
public class DoubleMajorRequiredCoursePolicyService {

    private static final Map<String, RequiredCoursePolicy> POLICIES = createPolicies();

    public RequiredCourseEvaluation evaluate(String department, List<CompletedCourseUploadRowDto> courses) {
        RequiredCoursePolicy policy = POLICIES.get(normalizeDepartment(department));
        if (policy == null) {
            // 편람상 지정 필수과목이 없는 학과 → 필수과목 검사는 적용하지 않음(학점 요건만 별도 검사)
            return new RequiredCourseEvaluation(false, 0, 0, true, List.of(), List.of());
        }

        CompletedCourseIndex index = indexCompletedCourses(courses);
        return evaluateRules(policy.rules(), index);
    }

    private RequiredCourseEvaluation evaluateRules(
            List<RequirementRule> rules,
            CompletedCourseIndex index
    ) {
        List<CategoryCourseDto> completedCourses = new ArrayList<>();
        List<CategoryCourseDto> missingCourses = new ArrayList<>();
        int requiredCount = 0;
        int completedCount = 0;
        boolean satisfied = true;

        for (RequirementRule rule : rules) {
            RuleEvaluation evaluation = evaluateRule(rule, index);
            requiredCount += evaluation.requiredCount();
            completedCount += evaluation.completedCount();
            completedCourses.addAll(evaluation.completedCourses());
            missingCourses.addAll(evaluation.missingCourses());
            satisfied = satisfied && evaluation.satisfied();
        }

        return new RequiredCourseEvaluation(
                true,
                requiredCount,
                completedCount,
                satisfied,
                List.copyOf(completedCourses),
                List.copyOf(missingCourses)
        );
    }

    private RuleEvaluation evaluateRule(
            RequirementRule rule,
            CompletedCourseIndex index
    ) {
        return switch (rule.type()) {
            case ALL_OF -> evaluateAllOf(rule, index);
            case CHOOSE_COUNT -> evaluateChooseCount(rule, index);
            case CHOOSE_CREDITS -> evaluateChooseCredits(rule, index);
            case ANY_TRACK -> evaluateAnyTrack(rule, index);
        };
    }

    private RuleEvaluation evaluateAllOf(
            RequirementRule rule,
            CompletedCourseIndex index
    ) {
        List<CategoryCourseDto> completed = new ArrayList<>();
        List<CategoryCourseDto> missing = new ArrayList<>();
        for (CourseOption option : rule.options()) {
            CompletedCourseUploadRowDto matched = findFirstCompleted(option, index, null);
            if (matched != null) {
                completed.add(toDto(matched));
            } else {
                missing.add(new CategoryCourseDto(option.primaryCode(), option.displayName(), "0"));
            }
        }
        return new RuleEvaluation(
                rule.options().size(),
                completed.size(),
                missing.isEmpty(),
                completed,
                missing
        );
    }

    private RuleEvaluation evaluateChooseCount(
            RequirementRule rule,
            CompletedCourseIndex index
    ) {
        List<CategoryCourseDto> matched = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (CourseOption option : rule.options()) {
            CompletedCourseUploadRowDto course = findFirstCompleted(option, index, used);
            if (course != null) {
                used.add(courseKey(course));
                matched.add(toDto(course));
            }
        }

        int required = rule.chooseCount();
        List<CategoryCourseDto> completed = matched.size() <= required
                ? matched
                : matched.subList(0, required);
        List<CategoryCourseDto> missing = new ArrayList<>();
        if (completed.size() < required) {
            missing.add(new CategoryCourseDto(
                    "",
                    rule.label() + " (" + completed.size() + "/" + required + "과목)",
                    "0"
            ));
        }
        return new RuleEvaluation(required, completed.size(), missing.isEmpty(), completed, missing);
    }

    private RuleEvaluation evaluateChooseCredits(
            RequirementRule rule,
            CompletedCourseIndex index
    ) {
        List<CategoryCourseDto> matched = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        BigDecimal earned = BigDecimal.ZERO;
        for (CourseOption option : rule.options()) {
            CompletedCourseUploadRowDto course = findFirstCompleted(option, index, used);
            if (course == null) {
                continue;
            }
            used.add(courseKey(course));
            matched.add(toDto(course));
            earned = earned.add(toDecimal(course.credit()));
            if (option.defaultCredit().signum() > 0 && toDecimal(course.credit()).signum() == 0) {
                earned = earned.add(option.defaultCredit());
            }
        }

        BigDecimal required = BigDecimal.valueOf(rule.chooseCredits());
        List<CategoryCourseDto> missing = new ArrayList<>();
        if (earned.compareTo(required) < 0) {
            missing.add(new CategoryCourseDto(
                    "",
                    rule.label() + " (" + earned.stripTrailingZeros().toPlainString()
                            + "/" + rule.chooseCredits() + "학점)",
                    "0"
            ));
        }
        return new RuleEvaluation(
                rule.chooseCredits(),
                matched.size(),
                missing.isEmpty(),
                matched,
                missing
        );
    }

    private RuleEvaluation evaluateAnyTrack(
            RequirementRule rule,
            CompletedCourseIndex index
    ) {
        RuleEvaluation bestUnsatisfied = null;
        for (TrackDefinition track : rule.tracks()) {
            RequiredCourseEvaluation trackResult = evaluateRules(track.rules(), index);
            if (trackResult.satisfied()) {
                return new RuleEvaluation(
                        trackResult.requiredCourseCount(),
                        trackResult.completedCourseCount(),
                        true,
                        trackResult.completedCourses(),
                        List.of()
                );
            }
            if (bestUnsatisfied == null
                    || trackResult.completedCourseCount() > bestUnsatisfied.completedCount()) {
                bestUnsatisfied = new RuleEvaluation(
                        trackResult.requiredCourseCount(),
                        trackResult.completedCourseCount(),
                        false,
                        trackResult.completedCourses(),
                        List.of(new CategoryCourseDto(
                                "",
                                rule.label() + " 미충족 (연기 또는 연출제작 트랙 중 하나 필요)",
                                "0"
                        ))
                );
            }
        }
        return bestUnsatisfied == null
                ? new RuleEvaluation(0, 0, false, List.of(), List.of())
                : bestUnsatisfied;
    }

    private CompletedCourseUploadRowDto findFirstCompleted(
            CourseOption option,
            CompletedCourseIndex index,
            Set<String> used
    ) {
        for (String code : option.codes()) {
            CompletedCourseUploadRowDto matched = index.byCode().get(code);
            if (matched != null && (used == null || !used.contains(courseKey(matched)))) {
                return matched;
            }
        }
        if (option.nameKeywords().isEmpty()) {
            return null;
        }
        for (CompletedCourseUploadRowDto course : index.all()) {
            if (used != null && used.contains(courseKey(course))) {
                continue;
            }
            if (matchesName(course, option.nameKeywords())) {
                return course;
            }
        }
        return null;
    }

    private boolean matchesName(CompletedCourseUploadRowDto course, List<String> keywords) {
        String name = normalizeName(course.courseName());
        if (name.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            String normalizedKeyword = normalizeName(keyword);
            if (!normalizedKeyword.isBlank() && name.contains(normalizedKeyword)) {
                return true;
            }
        }
        return false;
    }

    private CompletedCourseIndex indexCompletedCourses(List<CompletedCourseUploadRowDto> courses) {
        Map<String, CompletedCourseUploadRowDto> completedByCode = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        List<CompletedCourseUploadRowDto> all = new ArrayList<>();
        for (CompletedCourseUploadRowDto course : courses) {
            all.add(course);
            String code = normalizeCode(course.courseCode());
            if (code.isBlank() || !seen.add(code)) {
                continue;
            }
            completedByCode.put(code, course);
        }
        return new CompletedCourseIndex(completedByCode, List.copyOf(all));
    }

    private String courseKey(CompletedCourseUploadRowDto course) {
        String code = normalizeCode(course.courseCode());
        if (!code.isBlank()) {
            return "C:" + code;
        }
        return "N:" + normalizeName(course.courseName()) + ":" + firstNonBlank(course.credit(), "0");
    }

    private static Map<String, RequiredCoursePolicy> createPolicies() {
        Map<String, RequiredCoursePolicy> policies = new LinkedHashMap<>();

        policies.put("국제일본학전공", policy(allOf(opt("006408", "일본어능력시험"))));
        policies.put("일어일문학전공", policies.get("국제일본학전공"));

        policies.put("중국통상학전공", policy(allOf(opt("008758", "중국어능력시험"))));

        policies.put("경제학과", policy(allOf(
                opt("000078", "거시경제학"),
                opt("000209", "계량경제학"),
                opt("007768", "경제수학"),
                opt("001342", "미시경제학")
        )));
        policies.put("경제통상학과", policies.get("경제학과"));

        policies.put("경영학전공", policy(allOf(opt("000137", "경영학원론"))));
        policies.put("경영학부", policies.get("경영학전공"));

        policies.put("기계공학과", policy(allOf(
                opt("004510", "고체역학"),
                opt("005641", "유체역학"),
                opt("004714", "열역학"),
                opt("004642", "동역학")
        )));
        policies.put("기계공학전공", policies.get("기계공학과"));

        policies.put("컴퓨터공학과", policy(allOf(
                opt("009912", "C프로그래밍및실습"),
                opt("009954", "알고리즘및실습"),
                opt("009952", "자료구조및실습"),
                opt("004310", "운영체제")
        )));

        // 교류형: 세종대 IoT 인공지능 MD
        // 공유형: 초급/중급/고급 MD(9~12학점). 세종 개설 과목만 자동 합산, 타대학 과목은 안내.
        policies.put("지능IoT학과", policy(
                allOf(
                        opt("011901", "지능사물인터넷개론"),
                        opt("012025", "사물인공지능"),
                        opt("012027", "사물강화학습"),
                        optAny("정보시스템설계 또는 자율지능시스템설계", "006935", "011962")
                ),
                chooseCredits(
                        "공유형 마이크로디그리(세종 개설 과목 9학점 이상)",
                        9,
                        opt("011901", "지능사물인터넷개론", "3"),
                        opt("011990", "지능IoT플랫폼", "3"),
                        opt("011956", "사물연동디지털트윈", "3"),
                        opt("012092", "지능형IoT융합서비스(모듈형)", "3"),
                        opt("006139", "임베디드시스템", "3"),
                        opt("012091", "사물기계학습", "3"),
                        // 학수번호 확인이 어려운 경우 과목명에 '온디바이스'가 있으면 인정
                        optByName("온디바이스AI개론", "3", "온디바이스")
                )
        ));
        policies.put("지능IOT학과", policies.get("지능IoT학과"));

        // 편람: 전공실기1~7 중 4 + 전공실기8 + 연주1~7 중 8학점 + 연주8 + 졸업작품
        policies.put("음악과", policy(
                chooseCount(
                        "전공실기1~7 중 4과목",
                        4,
                        opt("002894", "전공실기1", "1"),
                        opt("002900", "전공실기2", "1"),
                        opt("002902", "전공실기3", "1"),
                        opt("002905", "전공실기4", "1"),
                        opt("002907", "전공실기5", "1"),
                        opt("002909", "전공실기6", "1"),
                        opt("002911", "전공실기7", "1")
                ),
                allOf(opt("002913", "전공실기8", "1")),
                chooseCredits(
                        "연주1~7 중 8학점",
                        8,
                        opt("004134", "연주1", "2"),
                        opt("002111", "연주2", "2"),
                        opt("002112", "연주3", "2"),
                        opt("002113", "연주4", "2"),
                        opt("004139", "연주5", "2"),
                        opt("002110", "연주6", "2"),
                        opt("010394", "연주7", "2")
                ),
                allOf(
                        opt("010395", "연주8", "2"),
                        opt("005764", "졸업작품(P/NP)", "0")
                )
        ));

        // 편람: 연기 트랙 또는 연출제작 트랙 중 하나 (졸업작품은 별도 예체능 추가요건으로 판정)
        policies.put("영화예술학과", policy(
                anyTrack(
                        "영화예술학과 지정필수",
                        track("연기", allOf(
                                opt("009218", "공연의이해와감상", "2"),
                                opt("009976", "기초연기1(근육과감각훈련)", "3"),
                                opt("010047", "기초연기2(감성과체험훈련)", "3"),
                                opt("007034", "무대매커니즘1", "2"),
                                opt("010054", "텍스트와연기실습1", "3"),
                                opt("008697", "공연제작Project2", "3")
                        )),
                        track("연출제작", allOf(
                                opt("005627", "영화개론", "3"),
                                opt("004652", "연출론", "2"),
                                opt("006371", "영화제작론", "2"),
                                opt("008687", "동양영화사", "2"),
                                opt("004522", "작품분석", "2"),
                                opt("004725", "다큐영화제작", "2"),
                                opt("008673", "스토리텔링", "2")
                        ))
                )
        ));

        return Map.copyOf(policies);
    }

    private static RequiredCoursePolicy policy(RequirementRule... rules) {
        return new RequiredCoursePolicy(List.of(rules));
    }

    private static RequirementRule allOf(CourseOption... options) {
        return new RequirementRule(RuleType.ALL_OF, "지정필수", List.of(options), 0, 0, List.of());
    }

    private static RequirementRule chooseCount(String label, int count, CourseOption... options) {
        return new RequirementRule(RuleType.CHOOSE_COUNT, label, List.of(options), count, 0, List.of());
    }

    private static RequirementRule chooseCredits(String label, int credits, CourseOption... options) {
        return new RequirementRule(RuleType.CHOOSE_CREDITS, label, List.of(options), 0, credits, List.of());
    }

    private static RequirementRule anyTrack(String label, TrackDefinition... tracks) {
        return new RequirementRule(RuleType.ANY_TRACK, label, List.of(), 0, 0, List.of(tracks));
    }

    private static TrackDefinition track(String name, RequirementRule... rules) {
        return new TrackDefinition(name, List.of(rules));
    }

    private static CourseOption opt(String code, String name) {
        return opt(code, name, "0");
    }

    private static CourseOption opt(String code, String name, String defaultCredit) {
        return new CourseOption(code, name, List.of(code), List.of(), new BigDecimal(defaultCredit));
    }

    private static CourseOption optAny(String displayName, String... codes) {
        List<String> acceptable = List.of(codes);
        return new CourseOption(codes[0], displayName, acceptable, List.of(), BigDecimal.ZERO);
    }

    private static CourseOption optByName(String displayName, String defaultCredit, String... nameKeywords) {
        return new CourseOption("", displayName, List.of(), List.of(nameKeywords), new BigDecimal(defaultCredit));
    }

    private CategoryCourseDto toDto(CompletedCourseUploadRowDto course) {
        return new CategoryCourseDto(
                course.courseCode(),
                firstNonBlank(course.courseName(), course.courseCode()),
                course.credit()
        );
    }

    private String normalizeDepartment(String department) {
        if (department == null) {
            return "";
        }
        return switch (department.trim()) {
            case "일어일문학전공", "국제일본학전공" -> "국제일본학전공";
            case "경영학과", "경영학부", "경영학전공" -> "경영학전공";
            case "기계공학전공", "기계공학과" -> "기계공학과";
            case "컴퓨터공학" -> "컴퓨터공학과";
            case "지능IOT학과", "지능IoT학과" -> "지능IoT학과";
            default -> department.trim();
        };
    }

    private String normalizeCode(String courseCode) {
        return courseCode == null ? "" : courseCode.trim();
    }

    private String normalizeName(String courseName) {
        if (courseName == null) {
            return "";
        }
        return courseName.replaceAll("\\s+", "").toLowerCase();
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private BigDecimal toDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    public record RequiredCourseEvaluation(
            boolean policyApplied,
            int requiredCourseCount,
            int completedCourseCount,
            boolean satisfied,
            List<CategoryCourseDto> completedCourses,
            List<CategoryCourseDto> missingCourses
    ) {
    }

    private enum RuleType {
        ALL_OF,
        CHOOSE_COUNT,
        CHOOSE_CREDITS,
        ANY_TRACK
    }

    private record RequiredCoursePolicy(List<RequirementRule> rules) {
    }

    private record RequirementRule(
            RuleType type,
            String label,
            List<CourseOption> options,
            int chooseCount,
            int chooseCredits,
            List<TrackDefinition> tracks
    ) {
    }

    private record TrackDefinition(String name, List<RequirementRule> rules) {
    }

    private record CourseOption(
            String primaryCode,
            String displayName,
            List<String> codes,
            List<String> nameKeywords,
            BigDecimal defaultCredit
    ) {
    }

    private record CompletedCourseIndex(
            Map<String, CompletedCourseUploadRowDto> byCode,
            List<CompletedCourseUploadRowDto> all
    ) {
    }

    private record RuleEvaluation(
            int requiredCount,
            int completedCount,
            boolean satisfied,
            List<CategoryCourseDto> completedCourses,
            List<CategoryCourseDto> missingCourses
    ) {
    }
}
