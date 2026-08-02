package com.example.congraduation.abeek.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * ddl-auto=update 는 기존 VARCHAR(255) note 컬럼을 TEXT로 넓히지 않으므로
 * 데이터 로더 실행 전에 MySQL 컬럼 타입을 보정한다.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class AbeekSchemaMigrator implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute("ALTER TABLE abeek_year_requirement MODIFY COLUMN note TEXT NULL");
            log.info("Ensured abeek_year_requirement.note is TEXT");
        } catch (Exception ex) {
            // H2 테스트/최초 생성 전이면 무시. Hibernate가 columnDefinition으로 생성한다.
            log.debug("Skip note column migration: {}", ex.getMessage());
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() "
                            + "AND TABLE_NAME = 'completed_courses' "
                            + "AND COLUMN_NAME = 'opening_department_code'",
                    Integer.class
            );
            if (count == null || count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE completed_courses ADD COLUMN opening_department_code VARCHAR(20) NULL");
                log.info("Added completed_courses.opening_department_code");
            }
        } catch (Exception ex) {
            try {
                jdbcTemplate.execute(
                        "ALTER TABLE completed_courses ADD COLUMN opening_department_code VARCHAR(20) NULL");
                log.info("Added completed_courses.opening_department_code (fallback)");
            } catch (Exception ignored) {
                log.debug("Skip opening_department_code migration: {}", ex.getMessage());
            }
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() "
                            + "AND TABLE_NAME = 'abeek_year_requirement' "
                            + "AND COLUMN_NAME = 'common_major_prerequisite_names'",
                    Integer.class
            );
            if (count == null || count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE abeek_year_requirement "
                                + "ADD COLUMN common_major_prerequisite_names TEXT NULL");
                log.info("Added abeek_year_requirement.common_major_prerequisite_names");
            }
        } catch (Exception ex) {
            log.debug("Skip common_major_prerequisite_names migration: {}", ex.getMessage());
        }
    }
}
