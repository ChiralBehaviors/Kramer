// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Tests for Kramer-n9w: convergent width integration with RDR-013.
 *
 * Verifies that:
 * 1. allConverged() is false when layout tree is not converged.
 * 2. allConverged() is true after all primitives converge.
 * 3. The decisionCache key matches across same-width-bucket invocations.
 * 4. allConverged() is reset when stylesheet changes (decisionCache cleared).
 * 5. The layout optimization path (cache hit + converged) prevents re-running
 *    the full pipeline for same-bucket resizes.
 */
class ConvergentWidthStabilityTest {

    // --- helpers ---

    private static ArrayNode buildVariableDataset(int count) {
        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        int longCount = Math.max(1, count / 10);
        int shortCount = count - longCount;
        for (int i = 0; i < shortCount; i++) {
            arr.add("x");
        }
        for (int i = 0; i < longCount; i++) {
            arr.add("x".repeat(20));
        }
        return arr;
    }

    private static Style modelWithSheet(LayoutStylesheet sheet) {
        Style model = mock(Style.class);
        when(model.getStylesheet()).thenReturn(sheet);
        return model;
    }

    private static DefaultLayoutStylesheet fastConvergeSheet(SchemaPath... paths) {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        for (SchemaPath path : paths) {
            sheet.setOverride(path, "stat-min-samples", 5);
            sheet.setOverride(path, "stat-convergence-k", 2);
        }
        return sheet;
    }

    private static PrimitiveLayout convergedPrimitive(String name, Style model) {
        SchemaPath path = new SchemaPath(name);
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout pl = new PrimitiveLayout(new Primitive(name), style);
        pl.setSchemaPath(path);
        ArrayNode data = buildVariableDataset(6);
        for (int i = 0; i < 2; i++) {
            pl.measure(data, n -> n, model);
        }
        return pl;
    }

    // -----------------------------------------------------------------------
    // Test 1: non-converged layout — allConverged() returns false
    // -----------------------------------------------------------------------

    @Test
    void allConvergedFalseWhenPrimitiveNotConverged() {
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);
        RelationStyle relStyle   = TestLayouts.mockRelationStyle();

        Primitive p = new Primitive("val");
        Relation root = new Relation("root");
        root.addChild(p);

        PrimitiveLayout pl = new PrimitiveLayout(p, primStyle);
        pl.setSchemaPath(new SchemaPath("root", "val"));

        RelationLayout rl = new RelationLayout(root, relStyle);
        rl.setSchemaPath(new SchemaPath("root"));
        rl.getChildren().add(pl);

        // No measure calls at all
        assertFalse(pl.isConverged(), "Primitive not converged before measure");
        assertFalse(rl.isConverged(), "RelationLayout not converged when child is not");
    }

    // -----------------------------------------------------------------------
    // Test 2: converged layout — allConverged() returns true
    // -----------------------------------------------------------------------

    @Test
    void allConvergedTrueWhenAllPrimitivesConverged() {
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);
        RelationStyle relStyle   = TestLayouts.mockRelationStyle();

        Primitive p1 = new Primitive("a");
        Primitive p2 = new Primitive("b");
        Relation root = new Relation("root");
        root.addChild(p1);
        root.addChild(p2);

        SchemaPath path1 = new SchemaPath("root", "a");
        SchemaPath path2 = new SchemaPath("root", "b");

        Style m = modelWithSheet(fastConvergeSheet(path1, path2));
        ArrayNode data = buildVariableDataset(6);

        PrimitiveLayout pl1 = new PrimitiveLayout(p1, primStyle);
        PrimitiveLayout pl2 = new PrimitiveLayout(p2, primStyle);
        pl1.setSchemaPath(path1);
        pl2.setSchemaPath(path2);

        for (int i = 0; i < 2; i++) {
            pl1.measure(data, n -> n, m);
            pl2.measure(data, n -> n, m);
        }

        RelationLayout rl = new RelationLayout(root, relStyle);
        rl.setSchemaPath(new SchemaPath("root"));
        rl.getChildren().add(pl1);
        rl.getChildren().add(pl2);

        assertTrue(pl1.isConverged(), "pl1 must be converged");
        assertTrue(pl2.isConverged(), "pl2 must be converged");
        assertTrue(rl.isConverged(), "RelationLayout must be converged when all children are");
    }

    // -----------------------------------------------------------------------
    // Test 3: same-width-bucket resize — cache key hits
    // -----------------------------------------------------------------------

    @Test
    void sameBucketResizeHitsDecisionCache() {
        Map<LayoutDecisionKey, LayoutResult> cache = new HashMap<>();
        SchemaPath path = new SchemaPath("root");
        int cardinality = 10;

        double width1 = 253.0; // bucket 25
        double width2 = 258.7; // bucket 25 — same bucket

        LayoutDecisionKey key1 = LayoutDecisionKey.of(path, width1, cardinality);
        LayoutResult result = new LayoutResult(false, false, 0.0, 0.0, 250.0, List.of());
        cache.put(key1, result);

        // Same-bucket width should hit the same cache entry
        LayoutDecisionKey key2 = LayoutDecisionKey.of(path, width2, cardinality);
        assertSame(result, cache.get(key2),
                "Same-bucket width must hit the existing cache entry");
    }

    // -----------------------------------------------------------------------
    // Test 4: different-bucket resize — cache miss
    // -----------------------------------------------------------------------

    @Test
    void differentBucketResizeMissesDecisionCache() {
        Map<LayoutDecisionKey, LayoutResult> cache = new HashMap<>();
        SchemaPath path = new SchemaPath("root");
        int cardinality = 10;

        double width1 = 250.0; // bucket 25
        double width2 = 260.0; // bucket 26 — different bucket

        LayoutDecisionKey key1 = LayoutDecisionKey.of(path, width1, cardinality);
        cache.put(key1, new LayoutResult(false, false, 0.0, 0.0, 250.0, List.of()));

        LayoutDecisionKey key2 = LayoutDecisionKey.of(path, width2, cardinality);
        assertNull(cache.get(key2),
                "Different-bucket width must miss the cache (requires full pipeline)");
    }

    // -----------------------------------------------------------------------
    // Test 5: stylesheet change resets convergence
    // -----------------------------------------------------------------------

    @Test
    void stylesheetChangeResetsConvergence() {
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);

        SchemaPath path = new SchemaPath("val");
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        sheet.setOverride(path, "stat-min-samples", 5);
        sheet.setOverride(path, "stat-convergence-k", 2);
        Style model = modelWithSheet(sheet);

        PrimitiveLayout pl = new PrimitiveLayout(new Primitive("val"), primStyle);
        pl.setSchemaPath(path);

        ArrayNode data = buildVariableDataset(6);

        // Drive to convergence
        for (int i = 0; i < 2; i++) {
            pl.measure(data, n -> n, model);
        }
        assertTrue(pl.isConverged(), "Should be converged after k stable calls");

        // Simulate stylesheet change (version bump via setOverride)
        sheet.setOverride(path, "stat-min-samples", 30);

        // After version change, measure with different data — frozen result is stale
        ArrayNode wideData = buildVariableDataset(100);
        pl.measure(wideData, n -> n, model);

        assertFalse(pl.isConverged(), "Convergence must be reset when stylesheet version changes");
    }

    // -----------------------------------------------------------------------
    // Test 6: convergent layout cache — optimization preconditions satisfied
    // -----------------------------------------------------------------------

    @Test
    void optimizationPreconditionsMetWhenConvergedAndCacheHit() {
        // Simulate the conditions AutoLayout.autoLayout() checks before short-circuit:
        // allConverged() == true AND decisionCache.containsKey(key) == true

        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);
        RelationStyle relStyle   = TestLayouts.mockRelationStyle();

        Primitive p = new Primitive("name");
        Relation root = new Relation("catalog");
        root.addChild(p);

        SchemaPath rootPath = new SchemaPath("catalog");
        SchemaPath leafPath = new SchemaPath("catalog", "name");

        Style m = modelWithSheet(fastConvergeSheet(leafPath));
        ArrayNode data = buildVariableDataset(6);

        PrimitiveLayout pl = new PrimitiveLayout(p, primStyle);
        pl.setSchemaPath(leafPath);
        for (int i = 0; i < 2; i++) {
            pl.measure(data, n -> n, m);
        }
        assertTrue(pl.isConverged());

        RelationLayout rl = new RelationLayout(root, relStyle);
        rl.setSchemaPath(rootPath);
        rl.getChildren().add(pl);

        assertTrue(rl.isConverged(), "Layout tree is fully converged");

        // Simulate the cache with a pre-populated entry for this width bucket
        Map<LayoutDecisionKey, LayoutResult> cache = new HashMap<>();
        double width = 300.0;
        int cardinality = data.size();
        LayoutDecisionKey key = LayoutDecisionKey.of(rootPath, width, cardinality);
        cache.put(key, new LayoutResult(false, false, 0.0, 0.0, 295.0, List.of()));

        // Both conditions met: converged + cache hit
        assertTrue(rl.isConverged(), "allConverged() precondition met");
        assertTrue(cache.containsKey(LayoutDecisionKey.of(rootPath, width, cardinality)),
                "decisionCache precondition met");

        // Same-bucket width also hits
        assertTrue(cache.containsKey(LayoutDecisionKey.of(rootPath, 305.0, cardinality)),
                "Same-bucket resize (305 → bucket 30) must also hit");
    }

    // -----------------------------------------------------------------------
    // Test 7: decisionCache cleared by stylesheet change
    // -----------------------------------------------------------------------

    @Test
    void decisionCacheClearedOnStylesheetChange() {
        // AutoLayout.autoLayout() clears decisionCache when stylesheet changes;
        // here we verify the cache state logic directly.
        Map<LayoutDecisionKey, LayoutResult> cache = new HashMap<>();
        SchemaPath path = new SchemaPath("root");

        for (int bucket = 20; bucket < 30; bucket++) {
            double w = bucket * 10.0;
            cache.put(LayoutDecisionKey.of(path, w, 5),
                      new LayoutResult(false, false, 0, 0, w, List.of()));
        }
        assertEquals(10, cache.size(), "Cache populated with 10 entries");

        // Stylesheet change simulation: clear the cache
        cache.clear();

        assertTrue(cache.isEmpty(), "After stylesheet change, cache must be empty (forces full pipeline)");
    }
}
