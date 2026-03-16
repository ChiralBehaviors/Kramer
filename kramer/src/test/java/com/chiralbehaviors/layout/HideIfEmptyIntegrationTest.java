// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * End-to-end integration test for hideIfEmpty=true through the full
 * measure() → extractFrom() pipeline with realistic multi-level JSON data.
 *
 * Schema: Relation("catalog") with children:
 *   - Primitive("name")
 *   - Relation("orders") with child Primitive("orderId")
 *
 * Data fixture: 3 catalog items
 *   - "Alpha"  → 2 orders
 *   - "Beta"   → 0 orders (empty array)
 *   - "Gamma"  → 1 order
 */
class HideIfEmptyIntegrationTest {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;

    private Relation      catalogSchema;
    private RelationStyle relStyle;
    private PrimitiveStyle primStyle;
    private Style         model;

    @BeforeEach
    void setUp() {
        // Build schema: catalog → { name, orders → { orderId } }
        Relation orders = new Relation("orders");
        orders.addChild(new Primitive("orderId"));

        catalogSchema = new Relation("catalog");
        catalogSchema.addChild(new Primitive("name"));
        catalogSchema.addChild(orders);

        relStyle  = TestLayouts.mockRelationStyle();
        primStyle = TestLayouts.mockPrimitiveStyle(7.0);

        model = mock(Style.class);
        when(model.layout(any(SchemaNode.class))).thenAnswer(inv -> {
            SchemaNode n = inv.getArgument(0);
            if (n instanceof Primitive p) return new PrimitiveLayout(p, primStyle);
            if (n instanceof Relation  r) return new RelationLayout(r, relStyle);
            return null;
        });
    }

    // -----------------------------------------------------------------------
    // Fixture builders
    // -----------------------------------------------------------------------

    /** Build a catalog item with the given order IDs (may be empty). */
    private ObjectNode catalogItem(String name, String... orderIds) {
        ObjectNode item = NF.objectNode();
        item.put("name", name);
        ArrayNode orders = NF.arrayNode();
        for (String id : orderIds) {
            ObjectNode order = NF.objectNode();
            order.put("orderId", id);
            orders.add(order);
        }
        item.set("orders", orders);
        return item;
    }

    /** Standard 3-item fixture: Alpha(2 orders), Beta(0 orders), Gamma(1 order). */
    private ArrayNode threeItemFixture() {
        ArrayNode data = NF.arrayNode();
        data.add(catalogItem("Alpha", "O-001", "O-002"));
        data.add(catalogItem("Beta" /* no orders */));
        data.add(catalogItem("Gamma", "O-100"));
        return data;
    }

    /** Collect "name" field values from an extracted array, in order. */
    private List<String> names(JsonNode extracted) {
        List<String> result = new ArrayList<>();
        extracted.forEach(row -> {
            JsonNode n = row.get("name");
            result.add(n != null ? n.asText() : "<null>");
        });
        return result;
    }

    /** Run measure then extractFrom, returning the extracted array. */
    private JsonNode measureAndExtract(RelationLayout layout, ArrayNode data) {
        layout.measure(data, n -> n, model);
        ObjectNode parent = NF.objectNode();
        parent.set("catalog", data);
        return layout.extractFrom(parent);
    }

    // -----------------------------------------------------------------------
    // hideIfEmpty=false (default) — cardinality 3, all rows kept
    // -----------------------------------------------------------------------

    @Test
    void hideIfEmptyFalseDefaultSeesAllThreeRows() {
        // Default: hideIfEmpty is null (not set)
        assertNull(catalogSchema.getHideIfEmpty(),
                   "hideIfEmpty must be null by default");

        RelationLayout layout = new RelationLayout(catalogSchema, relStyle);
        ArrayNode data = threeItemFixture();

        layout.measure(data, n -> n, model);

        MeasureResult mr = layout.getMeasureResult();
        assertNotNull(mr);
        assertEquals(3, mr.maxCardinality(),
                     "hideIfEmpty=null must see cardinality 3 (all rows)");
    }

    @Test
    void hideIfEmptyExplicitFalseSeesAllThreeRows() {
        catalogSchema.setHideIfEmpty(false);

        RelationLayout layout = new RelationLayout(catalogSchema, relStyle);
        ArrayNode data = threeItemFixture();

        layout.measure(data, n -> n, model);

        MeasureResult mr = layout.getMeasureResult();
        assertEquals(3, mr.maxCardinality(),
                     "hideIfEmpty=false must see cardinality 3 (all rows)");
    }

    // -----------------------------------------------------------------------
    // hideIfEmpty=true — cardinality 2, Beta (empty orders) filtered out
    // -----------------------------------------------------------------------

    @Test
    void hideIfEmptyTrueSeesCardinalityTwo() {
        catalogSchema.setHideIfEmpty(true);

        RelationLayout layout = new RelationLayout(catalogSchema, relStyle);
        ArrayNode data = threeItemFixture();

        layout.measure(data, n -> n, model);

        MeasureResult mr = layout.getMeasureResult();
        assertNotNull(mr);
        assertEquals(2, mr.maxCardinality(),
                     "hideIfEmpty=true must filter Beta, leaving maxCardinality=2");
    }

    // -----------------------------------------------------------------------
    // extractFrom() consistency — only 2 rows returned from build path
    // -----------------------------------------------------------------------

    @Test
    void extractFromReturnsOnlyTwoRowsWhenHideIfEmptyTrue() {
        catalogSchema.setHideIfEmpty(true);

        RelationLayout layout = new RelationLayout(catalogSchema, relStyle);
        ArrayNode data = threeItemFixture();

        JsonNode extracted = measureAndExtract(layout, data);

        List<String> result = names(extracted);
        assertEquals(2, result.size(),
                     "extractFrom() must return 2 rows when hideIfEmpty=true");
        assertFalse(result.contains("Beta"),
                    "extractFrom() must not include 'Beta' (empty orders)");
        assertTrue(result.contains("Alpha"), "Alpha must be present");
        assertTrue(result.contains("Gamma"), "Gamma must be present");
    }

    // -----------------------------------------------------------------------
    // Sort + filter composition — sort by "name", then filter
    // -----------------------------------------------------------------------

    @Test
    void sortAndFilterComposedSortedAndFiltered() {
        catalogSchema.setHideIfEmpty(true);
        // Explicit sort by name so result order is deterministic
        catalogSchema.setSortFields(List.of("name"));

        RelationLayout layout = new RelationLayout(catalogSchema, relStyle);

        // Deliberately insert in reverse-alpha order to verify sort
        ArrayNode data = NF.arrayNode();
        data.add(catalogItem("Gamma", "O-100"));
        data.add(catalogItem("Beta" /* empty */));
        data.add(catalogItem("Alpha", "O-001", "O-002"));

        JsonNode extracted = measureAndExtract(layout, data);

        List<String> result = names(extracted);
        // After filter: Alpha, Gamma; after sort by name: Alpha < Gamma
        assertEquals(List.of("Alpha", "Gamma"), result,
                     "Sort + filter must produce sorted, filtered result");
    }

    // -----------------------------------------------------------------------
    // No child Relations — hideIfEmpty=true must keep all rows
    // -----------------------------------------------------------------------

    @Test
    void hideIfEmptyTrueOnRelationWithNoChildRelationsKeepsAllRows() {
        // Schema with only Primitive children — no Relation children at all
        Relation flatSchema = new Relation("items");
        flatSchema.addChild(new Primitive("name"));
        flatSchema.addChild(new Primitive("value"));
        flatSchema.setHideIfEmpty(true);

        RelationLayout layout = new RelationLayout(flatSchema, relStyle);

        ArrayNode data = NF.arrayNode();
        ObjectNode r1 = NF.objectNode(); r1.put("name", "X"); r1.put("value", 1); data.add(r1);
        ObjectNode r2 = NF.objectNode(); r2.put("name", "Y"); r2.put("value", 2); data.add(r2);
        ObjectNode r3 = NF.objectNode(); r3.put("name", "Z"); r3.put("value", 3); data.add(r3);

        layout.measure(data, n -> n, model);

        MeasureResult mr = layout.getMeasureResult();
        assertNotNull(mr);
        assertEquals(3, mr.maxCardinality(),
                     "hideIfEmpty=true with no child Relations must keep all rows " +
                     "(hasNonEmptyChildren returns true when no Relation children exist)");
    }
}
