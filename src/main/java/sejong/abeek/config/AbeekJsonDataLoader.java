package sejong.abeek.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sejong.abeek.domain.AbeekYearRequirement;
import sejong.abeek.domain.CourseMaster;
import sejong.abeek.domain.CoursePrerequisite;
import sejong.abeek.domain.CurriculumCourse;
import sejong.abeek.domain.enums.CourseCategory;
import sejong.abeek.domain.enums.CourseRole;
import sejong.abeek.domain.enums.DesignLevel;
import sejong.abeek.domain.enums.ElectiveArea;
import sejong.abeek.repository.AbeekYearRequirementRepository;
import sejong.abeek.repository.CourseMasterRepository;
import sejong.abeek.repository.CoursePrerequisiteRepository;
import sejong.abeek.repository.CurriculumCourseRepository;

import java.io.IOException;
import java.util.Locale;

/**
 * src/main/resources/abeek-data/{department}/{year}.json 에서 스크랩한 교육과정을 적재한다.
 * 리소스가 없는 배포본에서는 조용히 종료한다.
 */
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
            if (!resource.getFilename().equals("_summary.json")) {
                load(resource);
            }
        }
    }

    private void load(Resource resource) throws IOException {
        JsonNode root = objectMapper.readTree(resource.getInputStream());
        String departmentCode = text(root, "departmentCode").toUpperCase(Locale.ROOT);
        int year = root.path("year").asInt();
        if (departmentCode.isBlank() || year <= 0 || root.path("requirement").isMissingNode()) {
            return;
        }

        upsertRequirement(departmentCode, year, root.path("requirement"));
        for (JsonNode course : root.path("courses")) {
            upsertCourse(departmentCode, year, course);
        }
        upsertPrerequisites(departmentCode, year, root.path("flowchartOcr"), root.path("needsReview").asBoolean(false));
    }

    private void upsertRequirement(String departmentCode, int year, JsonNode node) {
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
        requirementRepository.save(requirement);
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

    private void upsertPrerequisites(String departmentCode, int year, JsonNode flowchartOcr, boolean rootNeedsReview) {
        if (flowchartOcr == null || !flowchartOcr.isObject()) {
            return;
        }
        boolean needsReview = flowchartOcr.path("needsReview").asBoolean(rootNeedsReview);
        for (JsonNode node : flowchartOcr.path("prerequisites")) {
            String from = text(node, "fromCourseCode", "from");
            String to = text(node, "toCourseCode", "to");
            if (from.isBlank() || to.isBlank()) {
                continue;
            }
            String rawType = text(node, "type").toUpperCase(Locale.ROOT);
            String type = rawType.contains("RECOMMENDED") ? "RECOMMENDED" : "MANDATORY";
            CoursePrerequisite prerequisite = prerequisiteRepository
                    .findByDepartmentCodeAndYearAndFromCourseCodeAndToCourseCodeAndType(
                            departmentCode, year, from, to, type)
                    .orElseGet(CoursePrerequisite::new);
            prerequisite.setDepartmentCode(departmentCode);
            prerequisite.setYear(year);
            prerequisite.setFromCourseCode(from);
            prerequisite.setToCourseCode(to);
            prerequisite.setType(type);
            prerequisite.setNeedsReview(needsReview || node.path("needsReview").asBoolean(false));
            prerequisiteRepository.save(prerequisite);
        }
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
