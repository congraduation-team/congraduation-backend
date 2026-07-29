package com.example.congraduation.abeek.service;

import com.example.congraduation.abeek.domain.CurriculumCourse;
import com.example.congraduation.abeek.domain.enums.CourseCategory;
import com.example.congraduation.abeek.dto.OfferedCurriculumResponse;
import com.example.congraduation.abeek.dto.OfferedCurriculumResponse.NotOfferedCourseDto;
import com.example.congraduation.abeek.dto.OfferedCurriculumResponse.OfferedCourseDto;
import com.example.congraduation.abeek.dto.OfferedCurriculumResponse.SectionDto;
import com.example.congraduation.abeek.repository.AbeekStudentRepository;
import com.example.congraduation.abeek.repository.CurriculumCourseRepository;
import com.example.congraduation.abeek.timetable.TimetableCatalog;
import com.example.congraduation.abeek.timetable.TimetableOffering;
import com.example.congraduation.abeek.timetable.TimetableTermData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OfferedCurriculumService {

    private final CurriculumCourseRepository curriculumCourseRepository;
    private final AbeekDepartmentCatalog departmentCatalog;
    private final TimetableCatalog timetableCatalog;
    private final AbeekStudentRepository abeekStudentRepository;

    @Transactional(readOnly = true)
    public OfferedCurriculumResponse listOfferedCourses(
            String departmentCode,
            int curriculumYear,
            Integer termYear,
            Integer semester
    ) {
        AbeekDepartmentCatalog.DepartmentInfo department = departmentCatalog.findByAbeekCode(departmentCode)
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 학과코드: " + departmentCode));

        TimetableTermData term = resolveTerm(termYear, semester);
        List<CurriculumCourse> curriculum = curriculumCourseRepository
                .findAllWithMasterByDepartmentCodeAndYear(department.abeekCode(), curriculumYear)
                .stream()
                .filter(course -> course.getCourseMaster().getCategory() != CourseCategory.GENERAL)
                .toList();

        Set<String> openingNames = departmentCatalog.openingDepartmentNames(department.abeekCode()).stream()
                .map(this::normalize)
                .collect(Collectors.toSet());

        Map<String, List<TimetableOffering>> offeringsByName = term.offerings().stream()
                .filter(offering -> matchesOpeningDepartment(offering, openingNames))
                .collect(Collectors.groupingBy(
                        offering -> normalizeCourseName(offering.courseName()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<OfferedCourseDto> offered = new ArrayList<>();
        List<NotOfferedCourseDto> notOffered = new ArrayList<>();

        for (CurriculumCourse course : curriculum) {
            String courseName = course.getCourseMaster().getName();
            List<TimetableOffering> sections = findOfferings(courseName, offeringsByName);
            if (sections.isEmpty()) {
                notOffered.add(NotOfferedCourseDto.builder()
                        .abeekCourseCode(course.getCourseMaster().getCourseCode())
                        .courseName(courseName)
                        .category(course.getCourseMaster().getCategory())
                        .role(course.getRole())
                        .recommendedTerm(course.getRecommendedTerm())
                        .build());
                continue;
            }

            offered.add(OfferedCourseDto.builder()
                    .abeekCourseCode(course.getCourseMaster().getCourseCode())
                    .courseName(courseName)
                    .category(course.getCourseMaster().getCategory())
                    .role(course.getRole())
                    .credits(course.getCredits())
                    .designCredits(course.getDesignCredits())
                    .designLevel(course.getDesignLevel())
                    .recommendedTerm(course.getRecommendedTerm())
                    .sections(sections.stream().map(this::toSection).toList())
                    .build());
        }

        return OfferedCurriculumResponse.builder()
                .departmentCode(department.abeekCode())
                .departmentName(department.name())
                .curriculumYear(curriculumYear)
                .termYear(term.termYear())
                .semester(term.semester())
                .curriculumCourseCount(curriculum.size())
                .offeredCourseCount(offered.size())
                .notOfferedCourseCount(notOffered.size())
                .offeredCourses(offered)
                .notOfferedCourses(notOffered)
                .build();
    }

    @Transactional(readOnly = true)
    public OfferedCurriculumResponse listOfferedCoursesForStudent(
            String studentId,
            Integer termYear,
            Integer semester
    ) {
        var student = abeekStudentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new IllegalArgumentException("ABEEK 학생 없음: " + studentId));
        return listOfferedCourses(
                student.getDepartmentCode(),
                student.getEntranceYear(),
                termYear,
                semester
        );
    }

    public List<Map<String, Object>> availableTerms() {
        return timetableCatalog.availableTerms().stream()
                .map(term -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("termYear", term.termYear());
                    map.put("semester", term.semester());
                    map.put("offeringCount", term.offerings() == null ? 0 : term.offerings().size());
                    return map;
                })
                .toList();
    }

    private TimetableTermData resolveTerm(Integer termYear, Integer semester) {
        if (termYear != null && semester != null) {
            return timetableCatalog.findTerm(termYear, semester)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "시간표 데이터 없음: " + termYear + "-" + semester));
        }
        return timetableCatalog.latestTerm()
                .orElseThrow(() -> new IllegalArgumentException("적재된 강의시간표가 없습니다."));
    }

    private boolean matchesOpeningDepartment(TimetableOffering offering, Set<String> openingNames) {
        if (openingNames.isEmpty()) {
            return false;
        }
        String opening = normalize(offering.openingDepartment());
        if (openingNames.contains(opening)) {
            return true;
        }
        // 일부 시간표는 "학부 xxx전공" 형태라 포함 매칭 허용
        for (String name : openingNames) {
            if (!name.isBlank() && (opening.contains(name) || name.contains(opening))) {
                return true;
            }
        }
        return false;
    }

    private List<TimetableOffering> findOfferings(String courseName, Map<String, List<TimetableOffering>> byName) {
        String normalized = normalizeCourseName(courseName);
        List<TimetableOffering> exact = byName.get(normalized);
        if (exact != null && !exact.isEmpty()) {
            return exact;
        }

        String withoutParen = normalizeCourseName(courseName.replaceAll("\\([^)]*\\)", ""));
        List<TimetableOffering> byParen = byName.get(withoutParen);
        if (byParen != null && !byParen.isEmpty()) {
            return byParen;
        }

        for (Map.Entry<String, List<TimetableOffering>> entry : byName.entrySet()) {
            if (entry.getKey().contains(normalized) || normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return List.of();
    }

    private SectionDto toSection(TimetableOffering offering) {
        return SectionDto.builder()
                .sejongCourseCode(offering.courseCode())
                .section(offering.section())
                .category(offering.category())
                .gradeYear(offering.gradeYear())
                .credits(offering.credits())
                .schedule(offering.schedule())
                .room(offering.room())
                .professor(offering.professor())
                .openingDepartment(offering.openingDepartment())
                .build();
    }

    private String normalizeCourseName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("\\s+", "")
                .replace('：', ':')
                .replace('（', '(')
                .replace('）', ')')
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
