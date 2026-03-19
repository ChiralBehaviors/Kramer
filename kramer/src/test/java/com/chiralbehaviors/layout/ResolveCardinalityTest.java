// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Tests for the phantom-row bug (KRAMER-ktfq): resolveCardinality must return
 * 0 when all items have been filtered out (maxCardinality == 0).
 */
class ResolveCardinalityTest {

    /**
     * When maxCardinality is 0 (no data exists / all items filtered),
     * resolveCardinality must return 0 — not the old Math.max(1,...) phantom 1.
     */
    @Test
    void resolveCardinalityZeroWhenAllFiltered() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));

        RelationStyle style = TestLayouts.mockRelationStyle();
        RelationLayout layout = new RelationLayout(schema, style);

        // Measure against an empty array → maxCardinality stays 0.
        ArrayNode empty = JsonNodeFactory.instance.arrayNode();
        layout.measure(empty, n -> n, mockStyle(schema));

        // resolvedCardinality when caller passes 0 (fully filtered) must be 0.
        int resolved = layout.resolveCardinality(0);
        assertEquals(0, resolved, "All items filtered → cardinality must be 0, not 1");
    }

    /**
     * Normal case: data exists (maxCardinality > 0) but the caller asks for 0
     * (e.g., before any scroll position is computed). The old Math.max(1) guard
     * must still kick in so the layout renders at least one row.
     */
    @Test
    void resolveCardinalityStillClampsToOneNormally() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));

        RelationStyle style = TestLayouts.mockRelationStyle();
        RelationLayout layout = new RelationLayout(schema, style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 5; i++) {
            ObjectNode r = JsonNodeFactory.instance.objectNode();
            r.put("name", "item" + i);
            data.add(r);
        }
        layout.measure(data, n -> n, mockStyle(schema));

        // Caller passes 0 but data exists → clamp up to 1.
        int resolved = layout.resolveCardinality(0);
        assertEquals(1, resolved, "Data present but cardinality=0 → clamp to 1");
    }

    // ------------------------------------------------------------------ //

    /** Build a Style mock that delegates child layout — pre-creates PrimitiveLayouts
     *  before any when() call to avoid Mockito UnfinishedStubbingException. */
    private Style mockStyle(Relation schema) {
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);

        // Pre-create all PrimitiveLayouts outside of when() chains.
        java.util.Map<Primitive, PrimitiveLayout> primLayouts = new java.util.LinkedHashMap<>();
        for (SchemaNode child : schema.getChildren()) {
            if (child instanceof Primitive p) {
                primLayouts.put(p, new PrimitiveLayout(p, primStyle));
            }
        }

        Style model = mock(Style.class);
        for (var entry : primLayouts.entrySet()) {
            when(model.layout(entry.getKey())).thenReturn(entry.getValue());
        }
        when(model.layout(any(SchemaNode.class))).thenAnswer(inv -> {
            SchemaNode n = inv.getArgument(0);
            if (n instanceof Primitive p) return model.layout(p);
            return model.layout((Relation) n);
        });
        return model;
    }
}
