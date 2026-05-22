package com.novahub.search.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class IndexServiceTest {

    @Test
    void contentMappingUsesBuiltInAnalyzer() throws Exception {
        Field field = IndexService.class.getDeclaredField("CONTENT_MAPPING");
        field.setAccessible(true);
        String mapping = (String) field.get(null);

        assertThat(mapping).contains("\"analyzer\": \"standard\"");
        assertThat(mapping).doesNotContain("ik_max_word");
        assertThat(mapping).doesNotContain("ik_smart");
    }
}
