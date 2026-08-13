package com.example.congraduation.service.graduation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DoubleMajorRequiredCoursePolicyServiceTest {

    private final DoubleMajorRequiredCoursePolicyService service = new DoubleMajorRequiredCoursePolicyService();

    @Test
    void computerScienceAcceptsListedCourseCodes() {
        var result = service.evaluate("컴퓨터공학과", List.of(
                row("009912", "C프로그래밍및실습", "3"),
                row("009954", "알고리즘및실습", "3"),
                row("009952", "자료구조및실습", "3"),
                row("004310", "운영체제", "3")
        ));

        assertTrue(result.policyApplied());
        assertTrue(result.satisfied());
        assertEquals(4, result.requiredCourseCount());
        assertEquals(4, result.completedCourseCount());
    }

    @Test
    void computerScienceFailsWhenOperatingSystemMissing() {
        var result = service.evaluate("컴퓨터공학과", List.of(
                row("009912", "C프로그래밍및실습", "3"),
                row("009954", "알고리즘및실습", "3"),
                row("009952", "자료구조및실습", "3")
        ));

        assertFalse(result.satisfied());
        assertEquals(3, result.completedCourseCount());
        assertEquals(1, result.missingCourses().size());
        assertEquals("004310", result.missingCourses().getFirst().courseCode());
    }

    @Test
    void musicAcceptsSelectionRulesFromHandbook() {
        List<CompletedCourseUploadRowDto> courses = new ArrayList<>();
        // 전공실기1~4 (4과목)
        courses.add(row("002894", "전공실기1", "1"));
        courses.add(row("002900", "전공실기2", "1"));
        courses.add(row("002902", "전공실기3", "1"));
        courses.add(row("002905", "전공실기4", "1"));
        courses.add(row("002913", "전공실기8", "1"));
        // 연주1~4 = 8학점
        courses.add(row("004134", "연주1", "2"));
        courses.add(row("002111", "연주2", "2"));
        courses.add(row("002112", "연주3", "2"));
        courses.add(row("002113", "연주4", "2"));
        courses.add(row("010395", "연주8", "2"));
        courses.add(row("005764", "졸업작품(P/NP)", "0"));

        var result = service.evaluate("음악과", courses);

        assertTrue(result.satisfied());
        assertTrue(result.missingCourses().isEmpty());
    }

    @Test
    void musicFailsWhenPerformanceSelectionInsufficient() {
        List<CompletedCourseUploadRowDto> courses = new ArrayList<>();
        courses.add(row("002894", "전공실기1", "1"));
        courses.add(row("002900", "전공실기2", "1"));
        courses.add(row("002913", "전공실기8", "1"));
        courses.add(row("004134", "연주1", "2"));
        courses.add(row("002111", "연주2", "2"));
        courses.add(row("010395", "연주8", "2"));
        courses.add(row("005764", "졸업작품(P/NP)", "0"));

        var result = service.evaluate("음악과", courses);

        assertFalse(result.satisfied());
        assertTrue(result.missingCourses().stream().anyMatch(course -> course.courseName().contains("전공실기1~7")));
    }

    @Test
    void filmSatisfiedWhenActingTrackCompleted() {
        var result = service.evaluate("영화예술학과", List.of(
                row("009218", "공연의이해와감상", "2"),
                row("009976", "기초연기1(근육과감각훈련)", "3"),
                row("010047", "기초연기2(감성과체험훈련)", "3"),
                row("007034", "무대매커니즘1", "2"),
                row("010054", "텍스트와연기실습1", "3"),
                row("008697", "공연제작Project2", "3")
        ));

        assertTrue(result.satisfied());
    }

    @Test
    void filmSatisfiedWhenDirectingTrackCompleted() {
        var result = service.evaluate("영화예술학과", List.of(
                row("005627", "영화개론", "3"),
                row("004652", "연출론", "2"),
                row("006371", "영화제작론", "2"),
                row("008687", "동양영화사", "2"),
                row("004522", "작품분석", "2"),
                row("004725", "다큐영화제작", "2"),
                row("008673", "스토리텔링", "2")
        ));

        assertTrue(result.satisfied());
    }

    @Test
    void filmFailsWhenNeitherTrackCompleted() {
        var result = service.evaluate("영화예술학과", List.of(
                row("009218", "공연의이해와감상", "2"),
                row("005627", "영화개론", "3")
        ));

        assertFalse(result.satisfied());
    }

    @Test
    void departmentWithoutDesignatedCoursesSkipsRequiredCoursePolicy() {
        var result = service.evaluate("소프트웨어학과", List.of(
                row("009912", "C프로그래밍및실습", "3")
        ));

        assertFalse(result.policyApplied());
        assertTrue(result.satisfied());
    }

    @Test
    void iotRequiresExchangeMdCoursesAndSharedCredits() {
        var result = service.evaluate("지능IoT학과", List.of(
                row("011901", "지능사물인터넷개론", "3"),
                row("012025", "사물인공지능", "3"),
                row("012027", "사물강화학습", "3"),
                row("011962", "자율지능시스템설계", "3"),
                row("011990", "지능IoT플랫폼", "3"),
                row("011956", "사물연동디지털트윈", "3")
        ));

        assertTrue(result.policyApplied());
        assertTrue(result.satisfied());
    }

    @Test
    void iotFailsWhenSharedMicrodegreeCreditsInsufficient() {
        var result = service.evaluate("지능IOT학과", List.of(
                row("011901", "지능사물인터넷개론", "3"),
                row("012025", "사물인공지능", "3"),
                row("012027", "사물강화학습", "3"),
                row("006935", "정보시스템설계", "3")
        ));

        assertFalse(result.satisfied());
        assertTrue(result.missingCourses().stream()
                .anyMatch(course -> course.courseName().contains("공유형")));
    }

    @Test
    void iotCountsOnDeviceCourseByNameWhenCodeUnknown() {
        var result = service.evaluate("지능IoT학과", List.of(
                row("011901", "지능사물인터넷개론", "3"),
                row("012025", "사물인공지능", "3"),
                row("012027", "사물강화학습", "3"),
                row("011962", "자율지능시스템설계", "3"),
                row("011990", "지능IoT플랫폼", "3"),
                row("", "온디바이스 AI 개론", "3"),
                row("011956", "사물연동디지털트윈", "3")
        ));

        assertTrue(result.satisfied());
        assertTrue(result.completedCourses().stream()
                .anyMatch(course -> course.courseName().contains("온디바이스")));
    }

    private static CompletedCourseUploadRowDto row(String code, String name, String credit) {
        return new CompletedCourseUploadRowDto(
                "2024", "1학기", code, name, "복필", credit, "GRADE", "A0", "4.0"
        );
    }
}
