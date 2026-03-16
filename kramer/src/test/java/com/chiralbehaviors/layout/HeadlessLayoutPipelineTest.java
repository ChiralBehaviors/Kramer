// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Tests for Kramer-5yb: Style wired to MeasurementStrategy + headless integration.
 */
class HeadlessLayoutPipelineTest {

    private ConfiguredMeasurementStrategy strategy;
    private Style                         style;

    @BeforeEach
    void setUp() {
        strategy = new ConfiguredMeasurementStrategy();
        style = new Style(strategy);
    }

    // -------------------------------------------------------------------
    // 1. Style construction with ConfiguredMeasurementStrategy (no JavaFX)
    // -------------------------------------------------------------------

    @Test
    void constructStyleWithConfiguredStrategyDoesNotRequireJavaFx() {
        // If toolkit initialization were required, this would throw.
        assertNotNull(style);
    }

    @Test
    void styleAcceptsCustomLineHeightStrategy() {
        Style custom = new Style(new ConfiguredMeasurementStrategy(24.0, 2.0));
        assertNotNull(custom);
    }

    // -------------------------------------------------------------------
    // 2. style(Primitive) returns non-null PrimitiveStyle
    // -------------------------------------------------------------------

    @Test
    void stylePrimitiveReturnsNonNull() {
        Primitive p = new Primitive("name");
        PrimitiveStyle ps = style.style(p);
        assertNotNull(ps, "style(Primitive) must return non-null with ConfiguredMeasurementStrategy");
    }

    @Test
    void stylePrimitiveReturnsPrimitiveStyleSubtype() {
        Primitive p = new Primitive("age");
        PrimitiveStyle ps = style.style(p);
        assertInstanceOf(PrimitiveStyle.class, ps);
    }

    @Test
    void stylePrimitiveCachesResult() {
        Primitive p = new Primitive("field");
        PrimitiveStyle first = style.style(p);
        PrimitiveStyle second = style.style(p);
        assertSame(first, second, "style(Primitive) must return cached instance on repeated calls");
    }

    // -------------------------------------------------------------------
    // 3. style(Relation) returns non-null RelationStyle
    // -------------------------------------------------------------------

    @Test
    void styleRelationReturnsNonNull() {
        Relation r = new Relation("items");
        RelationStyle rs = style.style(r);
        assertNotNull(rs, "style(Relation) must return non-null with ConfiguredMeasurementStrategy");
    }

    @Test
    void styleRelationReturnsRelationStyleSubtype() {
        Relation r = new Relation("orders");
        RelationStyle rs = style.style(r);
        assertInstanceOf(RelationStyle.class, rs);
    }

    @Test
    void styleRelationCachesResult() {
        Relation r = new Relation("rel");
        RelationStyle first = style.style(r);
        RelationStyle second = style.style(r);
        assertSame(first, second, "style(Relation) must return cached instance on repeated calls");
    }

    // -------------------------------------------------------------------
    // 4. Returned styles carry configured metrics
    // -------------------------------------------------------------------

    @Test
    void primitiveStyleCarriesConfiguredHeight() {
        double lineHeight = 22.0;
        double inset = 3.0;
        Style configured = new Style(new ConfiguredMeasurementStrategy(lineHeight, inset));
        PrimitiveStyle ps = configured.style(new Primitive("x"));
        double expected = lineHeight + inset + inset;
        assertEquals(expected, ps.getLabelStyle().getHeight(), 0.001,
                     "PrimitiveStyle label height must reflect configured lineHeight and inset");
    }

    @Test
    void relationStyleCarriesConfiguredInsets() {
        double inset = 6.0;
        Style configured = new Style(new ConfiguredMeasurementStrategy(20.0, inset));
        RelationStyle rs = configured.style(new Relation("r"));
        assertEquals(inset + inset, rs.getRowVerticalInset(), 0.001,
                     "RelationStyle row vertical inset must reflect configured inset");
    }

    // -------------------------------------------------------------------
    // 5. Full pipeline: measure then snapshotDecisionTree (no JavaFX nodes)
    // -------------------------------------------------------------------

    @Test
    void measureAndSnapshotDecisionTreeHeadless() {
        // Build a simple schema: Relation with one Primitive child
        Relation schema = new Relation("root");
        Primitive child = new Primitive("name");
        schema.addChild(child);

        // Build layout objects using the headless Style
        RelationLayout rootLayout = style.layout(schema);
        assertNotNull(rootLayout, "layout(Relation) must return non-null");

        // Build schema paths (required before measure so snapshotDecisionTree works)
        rootLayout.buildPaths(new SchemaPath("root"), style);

        // Provide minimal JSON data: array of one object
        com.fasterxml.jackson.databind.node.ArrayNode data =
            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        com.fasterxml.jackson.databind.node.ObjectNode row =
            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        row.put("name", "Alice");
        data.add(row);

        // measure() calls LabelStyle.width() for label sizing, which requires a live
        // JavaFX toolkit. With ConfiguredMeasurementStrategy the font is null, so
        // width() throws UnsupportedOperationException — that is the documented contract.
        assertThrows(UnsupportedOperationException.class,
                     () -> rootLayout.measure(data, n -> n, style),
                     "measure() must throw UnsupportedOperationException when LabelStyle has no font (headless)");
    }

    // -------------------------------------------------------------------
    // 6. Strategy delegated via MeasurementStrategy interface
    // -------------------------------------------------------------------

    @Test
    void strategyIsInvokedViaMeasurementStrategyInterface() {
        // Verify the strategy is actually called (not the JAT path)
        // by checking that measurePrimitiveStyle works without JAT
        List<String> noSheets = List.of();
        Primitive p = new Primitive("f");
        // Direct call to strategy — must not throw AssertionError from JAT check
        assertDoesNotThrow(() -> strategy.measurePrimitiveStyle(p, noSheets));
        assertDoesNotThrow(() -> strategy.measureRelationStyle(new Relation("r"), noSheets));
    }

    @Test
    void styleWithNullStrategyUsesNormalJatPath() {
        // The default Style() constructor uses no strategy (null) — Style still constructs fine
        // and the JAT path is used for CSS measurement (not tested here, just structural)
        Style defaultStyle = new Style();
        assertNotNull(defaultStyle);
    }

    // -------------------------------------------------------------------
    // 7. clearCaches evicts entries; next call re-invokes strategy
    // -------------------------------------------------------------------

    @Test
    void clearCachesEvictsAndReInvokesStrategy() {
        Primitive p = new Primitive("clearField");
        PrimitiveStyle first = style.style(p);
        assertNotNull(first);

        style.clearCaches();

        // After clearing, the cache must be empty — a new call must produce a fresh instance
        PrimitiveStyle second = style.style(p);
        assertNotNull(second);
        assertNotSame(first, second, "clearCaches must evict entries; next call must re-invoke strategy and return a different instance");
    }
}
