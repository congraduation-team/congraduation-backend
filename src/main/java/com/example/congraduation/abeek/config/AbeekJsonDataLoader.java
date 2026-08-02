package com.example.congraduation.abeek.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.example.congraduation.abeek.domain.AbeekYearRequirement;
import com.example.congraduation.abeek.domain.CourseMaster;
import com.example.congraduation.abeek.domain.CoursePrerequisite;
import com.example.congraduation.abeek.domain.CurriculumCourse;
import com.example.congraduation.abeek.domain.enums.CourseCategory;
import com.example.congraduation.abeek.domain.enums.CourseRole;
import com.example.congraduation.abeek.domain.enums.DesignLevel;
import com.example.congraduation.abeek.domain.enums.ElectiveArea;
import com.example.congraduation.abeek.repository.AbeekYearRequirementRepository;
import com.example.congraduation.abeek.repository.CourseMasterRepository;
import com.example.congraduation.abeek.repository.CoursePrerequisiteRepository;
import com.example.congraduation.abeek.repository.CurriculumCourseRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * src/main/resources/abeek-data/{department}/{year}.json 에서 스크랩한 교육과정을 적재한다.
 * <p>
 * 선수 간선:
 * <ul>
 *   <li>루트 {@code prerequisites} 배열이 있으면 검수본으로 보고 해당 학과·연도 간선을 전부 교체한다.</li>
 *   <li>{@code flowchartOcr.prerequisites}(OCR 휴리스틱)는 적재하지 않는다.</li>
 * </ul>
 * 공통선수 영역: {@code commonMajorPrerequisiteCourseNames} → requirement 컬럼.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AbeekJsonDataLoader implements CommandLineRunner {

    private final ObjectMapper objectMapper;
    private final AbeekYearRequirementRepository requirementRepository;
    private final CourseMasterRepository courseMasterRepository;
    private final CurriculumCourseRepository curriculumCourseRepository;
    private final CoursePrerequisiteRepository prerequisiteRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:abeek-data/*/*.json");
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || filename.startsWith("_")) {
                continue;
            }
            load(resource);
        }
    }

    private void load(Resource resource) throws IOException {
        JsonNode root = objectMapper.readTree(resource.getInputStream());
        String departmentCode = text(root, "departmentCode").toUpperCase(Locale.ROOT);
        int year = root.path("year").asInt();
        if (departmentCode.isBlank() || year <= 0 || root.path("requirement").isMissingNode()) {
            return;
        }

        upsertRequirement(departmentCode, year, root.path("requirement"), root);
        for (JsonNode course : root.path("courses")) {
            upsertCourse(departmentCode, year, course);
        }
        loadCuratedPrerequisites(departmentCode, year, root);
    }

    private void upsertRequirement(String departmentCode, int year, JsonNode node, JsonNode root) {
        AbeekYearRequirement requirement = requirementRepository
                .findByDepartmentCodeAndYear(departmentCode, year)
                .orElseGet(AbeekYearRequirement::new);
        requirement.setDepartmentCode(departmentCode);
        requirement.setYear(year);
        requirement.setGeneralMinCredits(number(node, "generalMinCredits"));
        requirement.setBsmMinCredits(number(node, "bsmMinCredits"));
        requirement.setMajorMinCredits(number(node, "majorMinCredits"));
        requirement.setDesignMinCredits(node.path("designMinCredits").asDouble());
        JsonNode cert = node.path("certElective");
        requirement.setCertElectiveMinCourses(cert.path("minCourses").asInt());
        requirement.setCertElectiveMinCredits(cert.path("minCredits").asInt());
        requirement.setCertElectiveMinAreas(cert.path("minAreas").asInt());
        requirement.setNote(notes(node.path("rawNotes")));
        if (hasCommonMajorNames(root)) {
            requirement.setCommonMajorPrerequisiteNames(serializeCommonMajorNames(root));
        }
        requirementRepository.save(requirement);
    }

    private boolean hasCommonMajorNames(JsonNode root) {
        JsonNode names = root.get("commonMajorPrerequisiteCourseNames");
        if (names != null && names.isArray()) {
            return true;
        }
        names = root.get("commonMajorPrerequisites");
        return names != null && names.isArray();
    }

    private String serializeCommonMajorNames(JsonNode root) {
        JsonNode names = root.path("commonMajorPrerequisiteCourseNames");
        if (!names.isArray()) {
            names = root.path("commonMajorPrerequisites");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : names) {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (IOException ex) {
            log.warn("Failed to serialize commonMajorPrerequisiteCourseNames: {}", ex.getMessage());
            return null;
        }
    }

    private void upsertCourse(String departmentCode, int year, JsonNode node) {
        String name = text(node, "name");
        if (name.isBlank()) {
            return;
        }
        CourseCategory category = enumValue(CourseCategory.class, text(node, "category"), CourseCategory.MAJOR);
        String courseCode = courseCode(departmentCode, name);
        CourseMaster master = courseMasterRepository.findByCourseCode(courseCode)
                .orElseGet(CourseMaster::new);
        master.setCourseCode(courseCode);
        master.setName(name);
        master.setCategory(category);
        master.setEquivalenceGroup(courseCode);
        master.setElectiveArea(enumValue(ElectiveArea.class, text(node, "electiveArea"), ElectiveArea.NONE));
        master.setDepartmentCourse(category != CourseCategory.GENERAL);
        master = courseMasterRepository.save(master);

        CurriculumCourse course = curriculumCourseRepository
                .findByCurriculumYearAndDepartmentCodeAndCourseMaster_CourseCode(year, departmentCode, courseCode)
                .orElseGet(CurriculumCourse::new);
        course.setDepartmentCode(departmentCode);
        course.setCurriculumYear(year);
        course.setCourseMaster(master);
        course.setCredits(number(node, "credits"));
        course.setDesignCredits(node.path("designCredits").asDouble());
        course.setDesignLevel(enumValue(DesignLevel.class, text(node, "designLevel"), DesignLevel.NONE));
        course.setRole(enumValue(CourseRole.class, text(node, "role"), CourseRole.ELECTIVE));
        course.setRecommendedTerm(text(node, "recommendedTerm"));
        course.setNewlyIntroducedRequired(node.path("newlyIntroducedRequired").asBoolean(false));
        curriculumCourseRepository.save(course);
    }

    /**
     * 루트 {@code prerequisites} 가 배열이면 검수본으로 교체한다.
     * 키가 없으면(아직 Phase 1 이전 파일) 기존 DB 간선을 건드리지 않고 OCR도 넣지 않는다.
     */
    private void loadCuratedPrerequisites(String departmentCode, int year, JsonNode root) {
        JsonNode curated = root.get("prerequisites");
        if (curated == null || !curated.isArray()) {
            return;
        }

        prerequisiteRepository.deleteByDepartmentCodeAndYear(departmentCode, year);

        int saved = 0;
        for (JsonNode node : curated) {
            String from = text(node, "fromCourseCode", "from");
            String to = text(node, "toCourseCode", "to");
            if (from.isBlank() || to.isBlank()) {
                continue;
            }
            String type = normalizeEdgeType(text(node, "type"));
            boolean needsReview = node.path("needsReview").asBoolean(false);
            CoursePrerequisite prerequisite = CoursePrerequisite.builder()
                    .departmentCode(departmentCode)
                    .year(year)
                    .fromCourseCode(from)
                    .toCourseCode(to)
                    .type(type)
                    .needsReview(needsReview)
                    .build();
            prerequisiteRepository.save(prerequisite);
            saved++;
        }
        log.info("Loaded curated prerequisites {}/{}: {} edges", departmentCode, year, saved);
    }

    static String normalizeEdgeType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "MANDATORY";
        }
        String upper = rawType.toUpperCase(Locale.ROOT);
        if (upper.contains("RECOMMENDED") || upper.contains("DASH") || upper.contains("DOT")) {
            return "RECOMMENDED";
        }
        return "MANDATORY";
    }

    private int number(JsonNode node, String field) {
        return (int) Math.round(node.path(field).asDouble());
    }

    private String notes(JsonNode rawNotes) {
        if (!rawNotes.isArray()) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        for (JsonNode note : rawNotes) {
            if (!result.isEmpty()) {
                result.append('\n');
            }
            result.append(note.asText());
        }
        return result.toString();
    }

    private String courseCode(String departmentCode, String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (slug.isBlank()) {
            slug = "course";
        }
        return departmentCode + "_" + slug + "_" + Integer.toUnsignedString(name.hashCode(), 36);
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("").trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try {
            return value.isBlank() ? fallback : Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
