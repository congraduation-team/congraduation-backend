package com.example.congraduation.abeek.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SejongAbeekCourseCodeCatalogTest {

    private final SejongAbeekCourseCodeCatalog catalog = new SejongAbeekCourseCodeCatalog();

    @Test
    void findsAbeekFromSejongAndBack() {
        assertThat(catalog.findAbeekCourseCode("004310")).contains("MAJ_OS");
        assertThat(catalog.findSejongCourseCode("MAJ_OS")).contains("004310");
        assertThat(catalog.findSejongCourseCode("BSM_PROB")).contains("007330");
        assertThat(catalog.findAbeekCourseCode("001357")).contains("BSM_CALC1");
        assertThat(catalog.findAbeekCourseCode("002638")).contains("BSM_PHYS");
        assertThat(catalog.findAbeekCourseCode("006098")).contains("BSM_CALC");
        assertThat(catalog.findAbeekCourseCode("002647")).contains("BSM_PHYS_LAB");
        assertThat(catalog.findSejongCourseCode("BSM_CALC1")).contains("001357");
        assertThat(catalog.findSejongCourseCode("BSM_PHYS")).contains("002638");
        assertThat(catalog.findAbeekCourseCode("011304")).contains("GEN_UNI_ENG");
        assertThat(catalog.findAbeekCourseCode("011614")).contains("GEN_SEMINAR");
        assertThat(catalog.findAbeekCourseCode("011839")).contains("GEN_CAREER_EXP");
        assertThat(catalog.findAbeekCourseCode("011110")).contains("GEN_SEMINAR");
        assertThat(catalog.findAbeekCourseCode("011182")).contains("GEN_CAREER_EXP");
        assertThat(catalog.findSejongCourseCode("GEN_UNI_ENG")).contains("011304");
        assertThat(catalog.findSejongCourseCode("GEN_MGMT")).contains("011312");
        assertThat(catalog.findSejongCourseCode("GEN_FUSION_ART")).contains("011316");
        assertThat(catalog.findSejongCourseCode("UNKNOWN")).isEmpty();
    }
}
