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
        assertThat(catalog.findAbeekCourseCode("011304")).contains("GEN_UNI_ENG");
        assertThat(catalog.findSejongCourseCode("GEN_UNI_ENG")).contains("011304");
        assertThat(catalog.findSejongCourseCode("GEN_MGMT")).contains("011312");
        assertThat(catalog.findSejongCourseCode("GEN_FUSION_ART")).contains("011316");
        assertThat(catalog.findSejongCourseCode("UNKNOWN")).isEmpty();
    }
}
