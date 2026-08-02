package com.example.congraduation.abeek.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbeekJsonDataLoaderTest {

    @Test
    void normalizeEdgeType_mapsSolidAndDashedAliases() {
        assertEquals("MANDATORY", AbeekJsonDataLoader.normalizeEdgeType(null));
        assertEquals("MANDATORY", AbeekJsonDataLoader.normalizeEdgeType(""));
        assertEquals("MANDATORY", AbeekJsonDataLoader.normalizeEdgeType("MANDATORY"));
        assertEquals("MANDATORY", AbeekJsonDataLoader.normalizeEdgeType("solid"));
        assertEquals("RECOMMENDED", AbeekJsonDataLoader.normalizeEdgeType("RECOMMENDED"));
        assertEquals("RECOMMENDED", AbeekJsonDataLoader.normalizeEdgeType("RECOMMENDED_OCR_HEURISTIC"));
        assertEquals("RECOMMENDED", AbeekJsonDataLoader.normalizeEdgeType("dashed"));
        assertEquals("RECOMMENDED", AbeekJsonDataLoader.normalizeEdgeType("dotted"));
    }
}
