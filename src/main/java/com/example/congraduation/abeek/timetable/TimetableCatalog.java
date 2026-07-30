package com.example.congraduation.abeek.timetable;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimetableCatalog {

    private final ObjectMapper objectMapper;
    private final List<TimetableTermData> terms = new ArrayList<>();

    @PostConstruct
    void load() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:timetable-data/*.json");
        for (Resource resource : resources) {
            TimetableTermData data = objectMapper.readValue(resource.getInputStream(), TimetableTermData.class);
            terms.add(data);
            log.info("Loaded timetable {}-{} ({} offerings)",
                    data.termYear(), data.semester(), data.offerings() == null ? 0 : data.offerings().size());
        }
        terms.sort(Comparator
                .comparingInt(TimetableTermData::termYear).reversed()
                .thenComparingInt(TimetableTermData::semester).reversed());
    }

    public List<TimetableTermData> availableTerms() {
        return List.copyOf(terms);
    }

    public Optional<TimetableTermData> findTerm(int termYear, int semester) {
        return terms.stream()
                .filter(term -> term.termYear() == termYear && term.semester() == semester)
                .findFirst();
    }

    public Optional<TimetableTermData> latestTerm() {
        return terms.stream().findFirst();
    }

    /** 해당 학기(1/2)의 가장 최근 시간표 */
    public Optional<TimetableTermData> latestTermForSemester(int semester) {
        return terms.stream()
                .filter(term -> term.semester() == semester)
                .findFirst();
    }
}
