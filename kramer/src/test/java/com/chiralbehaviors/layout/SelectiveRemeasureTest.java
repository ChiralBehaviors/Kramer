// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.LabelStyle;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Tests for Kramer-bhin Phase 3b: selective re-measure with bucket comparison.
 *
 * All tests are headless — no JavaFX required.
 */
class SelectiveRemeasureTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

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
    private static ArrayNode buildData2(String f1, String[] v1s,
                                         String f2, String[] v2s) {
        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < v1s.length; i++) {
            ObjectNode obj = JsonNodeFactory.instance.objectNode();
            obj.put(f1, v1s[i]);
            obj.put(f2, v2s[i]);
            arr.add(obj);
        }
        return arr;
    }

    /**
     * Build a measured RelationLayout + PrimitiveLayout for a single field,
     * with a style model whose charWidth=1.0.
     */
    private static RelationLayout buildAndMeasure(String rootField,
                                                   String primField,
                                                   JsonNode data,
                                                   Style model) {
        Relation rel = new Relation(rootField);
        rel.addChild(new Primitive(primField));
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout pl = new PrimitiveLayout(new Primitive(primField), primStyle);
        when(model.layout(any(Primitive.class))).thenReturn(pl);
        when(model.layout(any(com.chiralbehaviors.layout.schema.SchemaNode.class))).thenAnswer(inv -> {
            var n = inv.getArgument(0);
            if (n instanceof Primitive p) return model.layout(p);
            return null;
        });
        RelationLayout rl = new RelationLayout(rel, TestLayouts.mockRelationStyle());
        rl.buildPaths(new SchemaPath(rootField), model);
        rl.measure(data, n -> n, model);
        return rl;
    }

    private static Style mockNullStylesheetModel() {
        Style model = mock(Style.class);
        when(model.getStylesheet()).thenReturn(null);
        return model;
    }

    /**
     * Build a converged PrimitiveLayout at the given path with the specified
     * p90Width. Uses a stylesheet that lowers min-samples so convergence is
     * reachable quickly.
     *
     * The layout is measured {@code callsToConverge} times with {@code data}.
     */
    private static PrimitiveLayout buildConvergedPrimitive(SchemaPath path,
                                                            JsonNode data,
                                                            int callsToConverge) {
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout pl = new PrimitiveLayout(new Primitive(path.leaf()), primStyle);
        pl.setSchemaPath(path);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        sheet.setOverride(path, "stat-min-samples", 5);
        sheet.setOverride(path, "stat-convergence-k", callsToConverge);
        Style styleModel = mock(Style.class);
        when(styleModel.getStylesheet()).thenReturn(sheet);

        for (int i = 0; i < callsToConverge; i++) {
            pl.measure(data, n -> n, styleModel);
        }
        return pl;
    }

    // -----------------------------------------------------------------------
    // Case (a): changed value stays in same p90 bucket → no re-layout needed
    // -----------------------------------------------------------------------

    /**
     * When data changes but the new p90 falls in the same 10-unit bucket as
     * the snapshot, remeasureChanged() should return an empty set.
     */
    @Test
    void sameBucket_noRelayoutNeeded() {
        SchemaPath path = new SchemaPath("root", "name");

        // Build data that produces p90 = 5 (values all length 5)
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 30; i++) data.add("hello"); // len 5 → p90 bucket = (int)(5/10) = 0

        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout pl = new PrimitiveLayout(new Primitive("name"), primStyle);
        pl.setSchemaPath(path);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        sheet.setOverride(path, "stat-min-samples", 5);
        sheet.setOverride(path, "stat-convergence-k", 3);
        Style styleModel = mock(Style.class);
        when(styleModel.getStylesheet()).thenReturn(sheet);

        // Converge
        pl.measure(data, n -> n, styleModel);
        pl.measure(data, n -> n, styleModel);
        pl.measure(data, n -> n, styleModel);
        assertTrue(pl.isConverged(), "Must be converged before test");

        MeasureResult mr = pl.getMeasureResult();
        assertNotNull(mr, "measureResult must be non-null after convergence");
        assertNotNull(mr.contentStats(), "contentStats must be non-null");
        double oldP90 = mr.contentStats().p90Width();

        // Snapshot the p90
        Map<SchemaPath, Double> p90Snapshot = new HashMap<>();
        p90Snapshot.put(path, oldP90);

        // Clear frozen and prepare new data still in same bucket
        pl.clearFrozenResult();
        // New data: length 7 → p90 = 7.0 still in bucket 0 (7/10 = 0 == 5/10 = 0)
        ArrayNode newData = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 30; i++) newData.add("goodbye"); // len 7

        // Pre-measure to verify same bucket (using contentStats.p90Width, not columnWidth)
        pl.measure(newData, n -> n, styleModel);
        MeasureResult newMr = pl.getMeasureResult();
        double newP90 = (newMr != null && newMr.contentStats() != null)
                        ? newMr.contentStats().p90Width() : 0.0;

        int oldBucket = (int)(oldP90 / 10);
        int newBucket = (int)(newP90 / 10);
        assertEquals(oldBucket, newBucket,
                "Both p90 values (%.1f and %.1f) must be in same bucket for this test to be valid"
                        .formatted(oldP90, newP90));

        // Reset to cleared state for remeasureChangedForTest to re-measure
        pl.clearFrozenResult();

        // Build a layout tree with this PrimitiveLayout
        Relation rel = new Relation("root");
        rel.addChild(new Primitive("name"));
        RelationLayout rl = new RelationLayout(rel, TestLayouts.mockRelationStyle());
        rl.children.add(pl);

        // remeasureChanged with this single path should return empty (bucket unchanged)
        Set<SchemaPath> bucketChanged = AutoLayout.remeasureChangedForTest(
                Set.of(path), rl, newData, styleModel, p90Snapshot);

        assertTrue(bucketChanged.isEmpty(),
                "Same p90 bucket: no bucket change → remeasureChanged must be empty");
    }

    // -----------------------------------------------------------------------
    // Case (b): changed value crosses p90 bucket → re-layout needed
    // -----------------------------------------------------------------------

    @Test
    void differentBucket_relayoutNeeded() {
        SchemaPath path = new SchemaPath("root", "name");

        // Initial data: short values → p90 in bucket 0 (len ~5)
        ArrayNode shortData = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 30; i++) shortData.add("hello"); // len 5

        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout pl = new PrimitiveLayout(new Primitive("name"), primStyle);
        pl.setSchemaPath(path);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        sheet.setOverride(path, "stat-min-samples", 5);
        sheet.setOverride(path, "stat-convergence-k", 3);
        Style styleModel = mock(Style.class);
        when(styleModel.getStylesheet()).thenReturn(sheet);

        pl.measure(shortData, n -> n, styleModel);
        pl.measure(shortData, n -> n, styleModel);
        pl.measure(shortData, n -> n, styleModel);
        assertTrue(pl.isConverged());

        MeasureResult mr = pl.getMeasureResult();
        double oldP90 = mr.contentStats().p90Width(); // ~5.0 → bucket 0

        Map<SchemaPath, Double> p90Snapshot = new HashMap<>();
        p90Snapshot.put(path, oldP90);

        // Clear and prepare long-value data → p90 in a higher bucket
        pl.clearFrozenResult();
        ArrayNode longData = JsonNodeFactory.instance.arrayNode();
        // Use values of length 15 → contentStats.p90Width = 15.0 → bucket 1
        for (int i = 0; i < 30; i++) longData.add("averylongvalue!"); // len 15

        // Pre-measure to verify different bucket
        pl.measure(longData, n -> n, styleModel);
        MeasureResult newMr = pl.getMeasureResult();
        double newP90 = (newMr != null && newMr.contentStats() != null)
                        ? newMr.contentStats().p90Width() : 0.0;

        int oldBucket = (int)(oldP90 / 10);
        int newBucket = (int)(newP90 / 10);
        assertNotEquals(oldBucket, newBucket,
                "p90 values (%.1f and %.1f) must be in different buckets for this test"
                        .formatted(oldP90, newP90));

        // Reset for remeasureChangedForTest to re-measure
        pl.clearFrozenResult();

        Relation rel = new Relation("root");
        rel.addChild(new Primitive("name"));
        RelationLayout rl = new RelationLayout(rel, TestLayouts.mockRelationStyle());
        rl.children.add(pl);

        Set<SchemaPath> bucketChanged = AutoLayout.remeasureChangedForTest(
                Set.of(path), rl, longData, styleModel, p90Snapshot);

        assertEquals(Set.of(path), bucketChanged,
                "Different bucket: path must appear in bucketChanged set");
    }

    // -----------------------------------------------------------------------
    // Convergence fast-path: converged && new_value <= p90Snapshot → skip clear
    // -----------------------------------------------------------------------

    /**
     * If a primitive is converged and the new data's value does not exceed the
     * snapshot p90, clearFrozenResultForPaths should NOT clear frozenResult for
     * that path (the fast-path short-circuit).
     */
    @Test
    void convergenceFastPath_skipsClearWhenValueBelowSnapshot() {
        SchemaPath path = new SchemaPath("root", "name");

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 30; i++) data.add("hello"); // len 5

        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout pl = new PrimitiveLayout(new Primitive("name"), primStyle);
        pl.setSchemaPath(path);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        sheet.setOverride(path, "stat-min-samples", 5);
        sheet.setOverride(path, "stat-convergence-k", 3);
        Style styleModel = mock(Style.class);
        when(styleModel.getStylesheet()).thenReturn(sheet);

        pl.measure(data, n -> n, styleModel);
        pl.measure(data, n -> n, styleModel);
        pl.measure(data, n -> n, styleModel);
        assertTrue(pl.isConverged());

        double snapshotP90 = pl.getMeasureResult().contentStats().p90Width(); // ~5.0

        // New data with values narrower than snapshot → p90 will be <= snapshotP90
        ArrayNode narrower = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 30; i++) narrower.add("hi"); // len 2

        // Build tree
        Relation rel = new Relation("root");
        rel.addChild(new Primitive("name"));
        RelationLayout rl = new RelationLayout(rel, TestLayouts.mockRelationStyle());
        rl.children.add(pl);

        // Use the fast-path check: converged && new_value <= snapshotP90 → skip clearing
        // We test this by calling clearFrozenResultForPathsForTest with a p90Snapshot that
        // has a value >= the new data's max value.
        Map<SchemaPath, Double> p90snap = new HashMap<>();
        p90snap.put(path, snapshotP90);

        // Fast-path: should NOT clear frozen result since the new value is <= snapshot p90
        AutoLayout.clearFrozenResultForPathsForTest(Set.of(path), rl, narrower, styleModel, p90snap);

        // frozenResult should still be intact (was converged, new value fits within snapshot)
        assertTrue(pl.isConverged(),
                "Fast-path: converged layout with new value <= p90Snapshot must NOT be cleared");
    }

    // -----------------------------------------------------------------------
    // OUTLINE sibling expansion
    // -----------------------------------------------------------------------

    /**
     * When a changed primitive's parent RelationLayout is in OUTLINE mode
     * (useTable == false), all siblings should also be added to the re-measure set.
     */
    @Test
    void outlineSiblingExpansion_siblingsRemeasured() {
        SchemaPath rootPath = new SchemaPath("root");
        SchemaPath namePath = rootPath.child("name");
        SchemaPath agePath  = rootPath.child("age");

        // Build two primitives as siblings under an OUTLINE RelationLayout
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout namePl = new PrimitiveLayout(new Primitive("name"), primStyle);
        namePl.setSchemaPath(namePath);
        PrimitiveLayout agePl = new PrimitiveLayout(new Primitive("age"), primStyle);
        agePl.setSchemaPath(agePath);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        sheet.setOverride(namePath, "stat-min-samples", 5);
        sheet.setOverride(namePath, "stat-convergence-k", 3);
        sheet.setOverride(agePath, "stat-min-samples", 5);
        sheet.setOverride(agePath, "stat-convergence-k", 3);
        Style styleModel = mock(Style.class);
        when(styleModel.getStylesheet()).thenReturn(sheet);

        // Measure both to get a non-null measureResult
        ArrayNode nameData = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 30; i++) nameData.add("Alice");
        ArrayNode ageData = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 30; i++) ageData.add("30");
        namePl.measure(nameData, n -> n, styleModel);
        agePl.measure(ageData, n -> n, styleModel);

        Relation rel = new Relation("root");
        rel.addChild(new Primitive("name"));
        rel.addChild(new Primitive("age"));

        // OUTLINE mode: useTable = false
        RelationLayout rl = new RelationLayout(rel, TestLayouts.mockRelationStyle());
        rl.children.add(namePl);
        rl.children.add(agePl);
        // useTable defaults to false → OUTLINE

        // p90Snapshot: record old p90s
        Map<SchemaPath, Double> p90Snapshot = new HashMap<>();
        MeasureResult nameMr = namePl.getMeasureResult();
        MeasureResult ageMr  = agePl.getMeasureResult();
        if (nameMr != null && nameMr.contentStats() != null)
            p90Snapshot.put(namePath, nameMr.contentStats().p90Width());
        if (ageMr != null && ageMr.contentStats() != null)
            p90Snapshot.put(agePath,  ageMr.contentStats().p90Width());

        // Clear only 'name' — but expect sibling 'age' is included in re-measure
        namePl.clearFrozenResult();

        // Build combined data for re-measure (both fields)
        ArrayNode combined = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 30; i++) {
            ObjectNode row = JsonNodeFactory.instance.objectNode();
            row.put("name", "A very long name that changes the bucket significantly!");
            row.put("age", "30");
            combined.add(row);
        }

        Set<SchemaPath> bucketChanged = AutoLayout.remeasureChangedForTest(
                Set.of(namePath), rl, combined, styleModel, p90Snapshot);

        // 'name' has a very long new value → bucket definitely changed
        // 'age' was added as a sibling and re-measured, but its value is unchanged
        // So bucketChanged must contain at least 'name' (which crossed a bucket)
        assertTrue(bucketChanged.contains(namePath),
                "name path must be in bucketChanged (bucket crossed)");
    }

    // -----------------------------------------------------------------------
    // Empty changedPaths → no re-measure
    // -----------------------------------------------------------------------

    @Test
    void emptyChangedPaths_noRemeasure() {
        SchemaPath path = new SchemaPath("root", "name");

        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout pl = new PrimitiveLayout(new Primitive("name"), primStyle);
        pl.setSchemaPath(path);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        sheet.setOverride(path, "stat-min-samples", 5);
        sheet.setOverride(path, "stat-convergence-k", 3);
        Style styleModel = mock(Style.class);
        when(styleModel.getStylesheet()).thenReturn(sheet);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 30; i++) data.add("hello");
        pl.measure(data, n -> n, styleModel);
        pl.measure(data, n -> n, styleModel);
        pl.measure(data, n -> n, styleModel);
        assertTrue(pl.isConverged());

        Relation rel = new Relation("root");
        rel.addChild(new Primitive("name"));
        RelationLayout rl = new RelationLayout(rel, TestLayouts.mockRelationStyle());
        rl.children.add(pl);

        Map<SchemaPath, Double> p90Snapshot = new HashMap<>();

        // Empty changedPaths → remeasureChanged returns empty immediately
        Set<SchemaPath> result = AutoLayout.remeasureChangedForTest(
                Set.of(), rl, data, styleModel, p90Snapshot);

        assertTrue(result.isEmpty(), "Empty changedPaths must produce empty bucketChanged");
        assertTrue(pl.isConverged(),
                "PrimitiveLayout must not be disturbed when changedPaths is empty");
    }
}
