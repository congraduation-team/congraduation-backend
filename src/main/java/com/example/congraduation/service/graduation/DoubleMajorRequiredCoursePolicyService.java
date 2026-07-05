package com.example.congraduation.service.graduation;

import com.example.congraduation.dto.transcript.CategoryCourseDto;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DoubleMajorRequiredCoursePolicyService {

    private static final Map<String, RequiredCoursePolicy> POLICIES = createPolicies();

    public RequiredCourseEvaluation evaluate(String department, List<CompletedCourseUploadRowDto> courses) {
        RequiredCoursePolicy policy = POLICIES.get(normalizeDepartment(department));
        if (policy == null) {
            return new RequiredCourseEvaluation(false, 0, 0, true, List.of(), List.of());
        }

        Map<String, CategoryCourseDto> completedByCode = new LinkedHashMap<>();
        Set<String> seenCourseCodes = new LinkedHashSet<>();
        for (CompletedCourseUploadRowDto course : courses) {
            String courseCode = normalizeCode(course.courseCode());
            if (courseCode.isBlank() || !seenCourseCodes.add(courseCode)) {
                continue;
            }
            if (!policy.requiredCourses().containsKey(courseCode)) {
                continue;
            }
            completedByCode.put(courseCode, new CategoryCourseDto(
                    courseCode,
                    firstNonBlank(course.courseName(), policy.requiredCourses().get(courseCode)),
                    course.credit()
            ));
        }

        List<CategoryCourseDto> completedCourses = new ArrayList<>();
        List<CategoryCourseDto> missingCourses = new ArrayList<>();

        for (Map.Entry<String, String> entry : policy.requiredCourses().entrySet()) {
            CategoryCourseDto completed = completedByCode.get(entry.getKey());
            if (completed != null) {
                completedCourses.add(completed);
                continue;
            }
            missingCourses.add(new CategoryCourseDto(entry.getKey(), entry.getValue(), "0"));
        }

        return new RequiredCourseEvaluation(
                true,
                policy.requiredCourses().size(),
                completedCourses.size(),
                missingCourses.isEmpty(),
                completedCourses,
                missingCourses
        );
    }

    private static Map<String, RequiredCoursePolicy> createPolicies() {
        Map<String, RequiredCoursePolicy> policies = new LinkedHashMap<>();

        policies.put("국제일본학전공", policy(
                course("006408", "일본어능력시험")
        ));
        policies.put("일어일문학전공", policies.get("국제일본학전공"));

        policies.put("중국통상학전공", policy(
                course("008758", "중국어능력시험")
        ));

        policies.put("경제학과", policy(
                course("000078", "거시경제학"),
                course("000209", "계량경제학"),
                course("007768", "경제수학"),
                course("001342", "미시경제학")
        ));
        policies.put("경제통상학과", policies.get("경제학과"));

        policies.put("경영학전공", policy(
                course("000137", "경영학원론")
        ));
        policies.put("경영학부", policies.get("경영학전공"));

        policies.put("기계공학과", policy(
                course("004510", "고체역학"),
                course("005641", "유체역학"),
                course("004714", "열역학"),
                course("004642", "동역학")
        ));
        policies.put("기계공학전공", policies.get("기계공학과"));

        policies.put("컴퓨터공학과", policy(
                course("009912", "C프로그래밍및실습"),
                course("009954", "알고리즘및실습"),
                course("009952", "자료구조및실습"),
                course("004310", "운영체제")
        ));

        policies.put("음악과", policy(
                course("002894", "전공실기1"),
                course("002900", "전공실기2"),
                course("002902", "전공실기3"),
                course("002905", "전공실기4"),
                course("002907", "전공실기5"),
                course("002909", "전공실기6"),
                course("002911", "전공실기7"),
                course("002913", "전공실기8"),
                course("004134", "연주1"),
                course("002111", "연주2"),
                course("002112", "연주3"),
                course("002113", "연주4"),
                course("004139", "연주5"),
                course("010394", "연주7"),
                course("010395", "연주8"),
                course("005764", "졸업작품(P/NP)")
        ));

        policies.put("영화예술학과", policy(
                course("009218", "공연의이해와감상"),
                course("009976", "기초연기1(근육과감각훈련)"),
                course("010047", "기초연기2(감성과체험훈련)"),
                course("007034", "무대매커니즘1"),
                course("006905", "무대매커니즘2"),
                course("010056", "공연프로덕션실습1"),
                course("010048", "공연프로덕션실습2"),
                course("008696", "공연제작Project1"),
                course("008697", "공연제작Project2"),
                course("005627", "영화개론"),
                course("004652", "연출론"),
                course("004522", "작품분석"),
                course("008673", "스토리텔링"),
                course("006534", "작가연구"),
                course("004725", "다큐영화제작"),
                course("005764", "졸업작품(P/NP)")
        ));

        return Map.copyOf(policies);
    }

    private static RequiredCoursePolicy policy(CourseDefinition... courses) {
        Map<String, String> requiredCourses = new LinkedHashMap<>();
        for (CourseDefinition course : courses) {
            requiredCourses.put(course.code(), course.name());
        }
        return new RequiredCoursePolicy(Map.copyOf(requiredCourses));
    }

    private static CourseDefinition course(String code, String name) {
        return new CourseDefinition(code, name);
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
            default -> department.trim();
        };
    }

    private String normalizeCode(String courseCode) {
        return courseCode == null ? "" : courseCode.trim();
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
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

    private record RequiredCoursePolicy(Map<String, String> requiredCourses) {
    }

    private record CourseDefinition(String code, String name) {
    }
}
