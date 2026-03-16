// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Relation;

/**
 * Tests for Relation.hideIfEmpty property.
 */
class RelationHideIfEmptyPropertyTest {

    @Test
    void hideIfEmptyDefaultsToNull() {
        var r = new Relation("items");
        assertNull(r.getHideIfEmpty());
    }

    @Test
    void setHideIfEmptyTrueRoundtrip() {
        var r = new Relation("items");
        r.setHideIfEmpty(true);
        assertEquals(Boolean.TRUE, r.getHideIfEmpty());
    }

    @Test
    void setHideIfEmptyFalseExplicitlyDisables() {
        var r = new Relation("items");
        r.setHideIfEmpty(false);
        assertEquals(Boolean.FALSE, r.getHideIfEmpty());
    }

    @Test
    void setHideIfEmptyNullResetsToDelegate() {
        var r = new Relation("items");
        r.setHideIfEmpty(true);
        r.setHideIfEmpty(null);
        assertNull(r.getHideIfEmpty());
    }
}
