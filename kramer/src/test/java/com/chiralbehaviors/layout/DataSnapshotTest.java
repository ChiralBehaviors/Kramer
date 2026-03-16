// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Tests for Kramer-eyr: data snapshot + frozenResult invalidation.
 *
 * All tests are headless — no JavaFX required.
 */
class DataSnapshotTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Build a Relation schema with the given child primitive field names,
     * create the RelationLayout via mocked Style, build paths, and measure.
     *
     * <p>The Style mock uses {@code charWidth=1.0} PrimitiveStyle mocks and
     * returns null for the stylesheet so no CSS is consulted.
     */
    private static RelationLayout buildAndMeasure(String field, JsonNode data,
                                                   String... childFields) {
        Relation rel = new Relation(field);
        for (String cf : childFields) {
            rel.addChild(new Primitive(cf));
        }

        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);
        Style model = mockStyleModel(rel, primStyle);

        RelationLayout rl = new RelationLayout(rel, TestLayouts.mockRelationStyle());
        rl.buildPaths(new SchemaPath(field), model);
        rl.measure(data, n -> n, model);
        return rl;
    }

    /** Build a Style mock that creates PrimitiveLayouts with the given primStyle. */
    private static Style mockStyleModel(Relation schema, PrimitiveStyle primStyle) {
        Style model = mock(Style.class);
        for (var child : schema.getChildren()) {
            if (child instanceof Primitive p) {
                PrimitiveLayout pl = new PrimitiveLayout(p, primStyle);
                when(model.layout(p)).thenReturn(pl);
            }
        }
        when(model.layout((com.chiralbehaviors.layout.schema.SchemaNode) any())).thenAnswer(inv -> {
            var n = inv.getArgument(0);
            if (n instanceof Primitive p) return model.layout(p);
            return null;
        });
        when(model.getStylesheet()).thenReturn(null);
        return model;
    }

    /** Build an array of single-field objects. */
    private static ArrayNode buildData(String field, String... values) {
        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        for (String v : values) {
            ObjectNode obj = JsonNodeFactory.instance.objectNode();
            obj.put(field, v);
            arr.add(obj);
        }
        return arr;
    }

    /** Build rows with two fields. */
    private static ArrayNode buildData2(String f1, String[] v1s, String f2, String[] v2s) {
        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < v1s.length; i++) {
            ObjectNode obj = JsonNodeFactory.instance.objectNode();
            obj.put(f1, v1s[i]);
            obj.put(f2, v2s[i]);
            arr.add(obj);
        }
        return arr;
    }

    // -----------------------------------------------------------------------
    // PrimitiveLayout.clearFrozenResult()
    // -----------------------------------------------------------------------

    @Test
    void clearFrozenResultResetsConvergenceState() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout pl = new PrimitiveLayout(new Primitive("text"), style);
        SchemaPath path = new SchemaPath("text");
        pl.setSchemaPath(path);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        sheet.setOverride(path, "stat-min-samples", 5);
        sheet.setOverride(path, "stat-convergence-k", 3);
        Style styleModel = mock(Style.class);
        when(styleModel.getStylesheet()).thenReturn(sheet);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 4; i++) data.add("x");
        data.add("x".repeat(20));

        // 3 stable calls → converged
        pl.measure(data, n -> n, styleModel);
        pl.measure(data, n -> n, styleModel);
        pl.measure(data, n -> n, styleModel);
        assertTrue(pl.isConverged(), "Should be converged before clearFrozenResult()");

        pl.clearFrozenResult();

        assertFalse(pl.isConverged(), "clearFrozenResult() must clear convergence");
    }

    @Test
    void clearFrozenResultAllowsRemeasure() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout pl = new PrimitiveLayout(new Primitive("text"), style);
        SchemaPath path = new SchemaPath("text");
        pl.setSchemaPath(path);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        sheet.setOverride(path, "stat-min-samples", 5);
        sheet.setOverride(path, "stat-convergence-k", 3);
        Style styleModel = mock(Style.class);
        when(styleModel.getStylesheet()).thenReturn(sheet);

        // Narrow data — converge
        ArrayNode narrow = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 4; i++) narrow.add("x");
        narrow.add("x".repeat(20));
        pl.measure(narrow, n -> n, styleModel);
        pl.measure(narrow, n -> n, styleModel);
        pl.measure(narrow, n -> n, styleModel);
        assertTrue(pl.isConverged());
        double frozenWidth = pl.columnWidth();

        pl.clearFrozenResult();

        // Measure wider data — must NOT return old frozen width
        ArrayNode wide = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 4; i++) wide.add("x");
        wide.add("x".repeat(100));
        double newWidth = pl.measure(wide, n -> n, styleModel);

        assertNotEquals(frozenWidth, newWidth, 0.1,
                "After clearFrozenResult(), measure() must use new data");
    }

    // -----------------------------------------------------------------------
    // DataSnapshot.buildSnapshot()
    // -----------------------------------------------------------------------

    @Test
    void buildSnapshotCapturesPrimitiveValues() {
        ArrayNode data = buildData("name", "Alice", "Bob", "Carol");
        RelationLayout rl = buildAndMeasure("root", data, "name");

        Map<SchemaPath, List<String>> snapshot = DataSnapshot.buildSnapshot(rl, data);

        assertFalse(snapshot.isEmpty(), "Snapshot must not be empty");
        SchemaPath namePath = new SchemaPath("root", "name");
        assertTrue(snapshot.containsKey(namePath), "Snapshot must contain root/name");
        assertEquals(List.of("Alice", "Bob", "Carol"), snapshot.get(namePath));
    }

    @Test
    void buildSnapshotCapturesMultiplePrimitives() {
        ArrayNode data = buildData2("name", new String[]{"Alice", "Bob"},
                                    "age",  new String[]{"30", "25"});
        RelationLayout rl = buildAndMeasure("root", data, "name", "age");

        Map<SchemaPath, List<String>> snapshot = DataSnapshot.buildSnapshot(rl, data);

        assertEquals(List.of("Alice", "Bob"), snapshot.get(new SchemaPath("root", "name")));
        assertEquals(List.of("30", "25"),     snapshot.get(new SchemaPath("root", "age")));
    }

    @Test
    void buildSnapshotEmptyDataProducesEmptyLists() {
        // Measure with real data so children are populated
        ArrayNode initData = buildData("name", "Alice");
        RelationLayout rl = buildAndMeasure("root", initData, "name");

        ArrayNode empty = JsonNodeFactory.instance.arrayNode();
        Map<SchemaPath, List<String>> snapshot = DataSnapshot.buildSnapshot(rl, empty);

        SchemaPath namePath = new SchemaPath("root", "name");
        assertTrue(snapshot.containsKey(namePath), "Snapshot key must exist even for empty data");
        assertEquals(List.of(), snapshot.get(namePath));
    }

    // -----------------------------------------------------------------------
    // DataSnapshot.detectChangedPaths() — cold start
    // -----------------------------------------------------------------------

    @Test
    void coldStartReturnsAllPaths() {
        ArrayNode data = buildData2("name", new String[]{"Alice"},
                                    "age",  new String[]{"30"});
        RelationLayout rl = buildAndMeasure("root", data, "name", "age");

        // Cold start: prior snapshot is EMPTY
        Set<SchemaPath> changed = DataSnapshot.detectChangedPaths(rl, data, DataSnapshot.EMPTY);

        assertEquals(2, changed.size(), "Cold start must return all 2 primitive paths");
        assertTrue(changed.contains(new SchemaPath("root", "name")));
        assertTrue(changed.contains(new SchemaPath("root", "age")));
    }

    // -----------------------------------------------------------------------
    // DataSnapshot.detectChangedPaths() — same data → no changes
    // -----------------------------------------------------------------------

    @Test
    void sameDataProducesNoChangedPaths() {
        ArrayNode data = buildData("name", "Alice", "Bob");
        RelationLayout rl = buildAndMeasure("root", data, "name");

        Map<SchemaPath, List<String>> snapshot = DataSnapshot.buildSnapshot(rl, data);

        // Same data again → nothing changed
        Set<SchemaPath> changed = DataSnapshot.detectChangedPaths(rl, data, snapshot);
        assertTrue(changed.isEmpty(), "Identical data must produce no changed paths");
    }

    // -----------------------------------------------------------------------
    // DataSnapshot.detectChangedPaths() — changed value
    // -----------------------------------------------------------------------

    @Test
    void changedValueDetected() {
        ArrayNode data1 = buildData2("name", new String[]{"Alice", "Bob"},
                                     "age",  new String[]{"30", "25"});
        RelationLayout rl = buildAndMeasure("root", data1, "name", "age");

        Map<SchemaPath, List<String>> snapshot = DataSnapshot.buildSnapshot(rl, data1);

        // Change 'name' only; 'age' stays the same
        ArrayNode data2 = buildData2("name", new String[]{"Alice", "Charlie"},
                                     "age",  new String[]{"30", "25"});
        Set<SchemaPath> changed = DataSnapshot.detectChangedPaths(rl, data2, snapshot);

        assertEquals(1, changed.size(), "Only 'name' changed");
        assertTrue(changed.contains(new SchemaPath("root", "name")));
        assertFalse(changed.contains(new SchemaPath("root", "age")));
    }

    // -----------------------------------------------------------------------
    // DataSnapshot.detectChangedPaths() — row count change → full rebuild
    // -----------------------------------------------------------------------

    @Test
    void rowCountChangeReturnsAllPaths() {
        ArrayNode data1 = buildData2("name", new String[]{"Alice"},
                                     "age",  new String[]{"30"});
        RelationLayout rl = buildAndMeasure("root", data1, "name", "age");

        Map<SchemaPath, List<String>> snapshot = DataSnapshot.buildSnapshot(rl, data1);

        // Two rows now
        ArrayNode data2 = buildData2("name", new String[]{"Alice", "Bob"},
                                     "age",  new String[]{"30", "25"});
        Set<SchemaPath> changed = DataSnapshot.detectChangedPaths(rl, data2, snapshot);

        assertEquals(2, changed.size(), "Row count change must return all paths");
        assertTrue(changed.contains(new SchemaPath("root", "name")));
        assertTrue(changed.contains(new SchemaPath("root", "age")));
    }

    // -----------------------------------------------------------------------
    // clearFrozenResultForPaths integration via tree walk
    // -----------------------------------------------------------------------

    @Test
    void clearFrozenResultForPathsInvalidatesAffectedLayouts() {
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout pl = new PrimitiveLayout(new Primitive("name"), primStyle);
        SchemaPath path = new SchemaPath("root", "name");
        pl.setSchemaPath(path);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        sheet.setOverride(path, "stat-min-samples", 5);
        sheet.setOverride(path, "stat-convergence-k", 3);
        Style styleModel = mock(Style.class);
        when(styleModel.getStylesheet()).thenReturn(sheet);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 4; i++) data.add("x");
        data.add("x".repeat(20));
        pl.measure(data, n -> n, styleModel);
        pl.measure(data, n -> n, styleModel);
        pl.measure(data, n -> n, styleModel);
        assertTrue(pl.isConverged());

        // Simulate clearFrozenResultForPaths — path in changed set
        pl.clearFrozenResult();
        assertFalse(pl.isConverged(), "Affected PrimitiveLayout must lose convergence");
    }

    // -----------------------------------------------------------------------
    // Schema root change: EMPTY snapshot triggers full rebuild
    // -----------------------------------------------------------------------

    @Test
    void schemaRootChangeInvalidatesAllState() {
        ArrayNode data = buildData2("x", new String[]{"1", "2"},
                                    "y", new String[]{"a", "b"});
        RelationLayout rl = buildAndMeasure("v2root", data, "x", "y");

        // After schema root change, prior snapshot is reset to EMPTY → all paths changed
        Set<SchemaPath> changed = DataSnapshot.detectChangedPaths(rl, data, DataSnapshot.EMPTY);
        assertEquals(2, changed.size(), "EMPTY prior snapshot must return all primitive paths");
    }
}
