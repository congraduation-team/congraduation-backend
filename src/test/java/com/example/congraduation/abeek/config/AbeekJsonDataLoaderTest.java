package com.example.congraduation.abeek.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

    @Test
    void prerequisiteEdgeKey_distinguishesTypeAndEndpoints() {
        assertEquals(
                AbeekJsonDataLoader.prerequisiteEdgeKey("에너지재료", "고체화학", "RECOMMENDED"),
                AbeekJsonDataLoader.prerequisiteEdgeKey("에너지재료", "고체화학", "RECOMMENDED")
        );
        assertNotEquals(
                AbeekJsonDataLoader.prerequisiteEdgeKey("에너지재료", "고체화학", "RECOMMENDED"),
                AbeekJsonDataLoader.prerequisiteEdgeKey("에너지재료", "고체화학", "MANDATORY")
        );
        assertNotEquals(
                AbeekJsonDataLoader.prerequisiteEdgeKey("에너지재료", "고체화학", "RECOMMENDED"),
                AbeekJsonDataLoader.prerequisiteEdgeKey("에너지재료", "산화물재료", "RECOMMENDED")
        );
    }
}
