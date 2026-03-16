// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Relation;

/**
 * Tests for Relation.sortFields and Relation.autoSort properties.
 */
class RelationSortFieldsTest {

    @Test
    void sortFieldsDefaultsToEmptyList() {
        var r = new Relation("items");
        assertNotNull(r.getSortFields());
        assertTrue(r.getSortFields().isEmpty());
    }

    @Test
    void autoSortDefaultsFalse() {
        var r = new Relation("items");
        assertFalse(r.isAutoSort());
    }

    @Test
    void setSortFieldsRoundtrip() {
        var r = new Relation("items");
        var fields = List.of("name", "age");
        r.setSortFields(fields);
        assertEquals(fields, r.getSortFields());
    }

    @Test
    void setAutoSortRoundtrip() {
        var r = new Relation("items");
        r.setAutoSort(true);
        assertTrue(r.isAutoSort());
        r.setAutoSort(false);
        assertFalse(r.isAutoSort());
    }

    @Test
    void setSortFieldsToNullNormalizesToEmpty() {
        var r = new Relation("items");
        r.setSortFields(null);
        assertNotNull(r.getSortFields());
        assertTrue(r.getSortFields().isEmpty());
    }

    @Test
    void sortFieldsListIsDefensiveCopy() {
        var r = new Relation("items");
        var mutable = new ArrayList<>(List.of("x", "y"));
        r.setSortFields(mutable);
        List<String> stored = r.getSortFields();
        assertEquals(List.of("x", "y"), stored);
        // Mutate the original list after setting — stored copy must be unaffected
        mutable.add("z");
        assertEquals(List.of("x", "y"), r.getSortFields(),
                     "setSortFields must store a defensive copy, not a reference to the passed list");
    }
}
