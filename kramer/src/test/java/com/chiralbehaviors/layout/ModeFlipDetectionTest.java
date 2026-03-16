// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.PrimitiveStyle;

/**
 * Tests for Kramer-nyqy Phase 3c: Lightweight mode-flip detection.
 *
 * Uses AutoLayout.detectModeFlipForTest() — a static test-visible entry-point
 * that drives the same tree-walk logic as the private detectModeFlip() method,
 * without requiring a live AutoLayout or JavaFX.
 *
 * All tests are headless.
 */
class ModeFlipDetectionTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Build a single-child RelationLayout with a PrimitiveLayout whose
     * dataWidth is set to {@code primDataWidth}. The RelationLayout is forced
     * into TABLE mode via nestTableColumn(). mockRelationStyle has all insets=0,
     * so calculateTableColumnWidth() == primDataWidth.
     *
     * @param rootPath      path for the RelationLayout
     * @param primPath      path for the PrimitiveLayout child
     * @param primDataWidth the dataWidth to inject directly into the PrimitiveLayout
     * @return RelationLayout in TABLE mode
     */
    private static RelationLayout buildTableModeRelation(SchemaPath rootPath,
                                                          SchemaPath primPath,
                                                          double primDataWidth) {
        RelationLayout rl = buildRelationLayout(rootPath, primPath, primDataWidth);
        rl.nestTableColumn(SchemaNodeLayout.Indent.TOP, new javafx.geometry.Insets(0));
        return rl;
    }

    /**
     * Build a single-child RelationLayout in OUTLINE mode (useTable=false).
     */
    private static RelationLayout buildOutlineModeRelation(SchemaPath rootPath,
                                                            SchemaPath primPath,
                                                            double primDataWidth) {
        return buildRelationLayout(rootPath, primPath, primDataWidth);
    }

    /**
     * Constructs a RelationLayout with one PrimitiveLayout child. Injects
     * {@code primDataWidth} directly into the PrimitiveLayout's dataWidth field
     * so that tableColumnWidth() returns a predictable value without needing
     * a full measure() pipeline.
     */
    private static RelationLayout buildRelationLayout(SchemaPath rootPath,
                                                       SchemaPath primPath,
                                                       double primDataWidth) {
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout pl = new PrimitiveLayout(new Primitive(primPath.leaf()), primStyle);
        pl.setSchemaPath(primPath);
        // Directly set dataWidth so tableColumnWidth() returns a known value.
        // dataWidth is protected and tests are in the same package.
        pl.dataWidth = primDataWidth;

        Relation rel = new Relation(rootPath.leaf());
        rel.addChild(new Primitive(primPath.leaf()));

        RelationLayout rl = new RelationLayout(rel, TestLayouts.mockRelationStyle());
        rl.children.add(pl);
        rl.setSchemaPath(rootPath);
        return rl;
    }

    // -----------------------------------------------------------------------
    // Test 1: p90 change within threshold → no mode flip, just rebind
    // -----------------------------------------------------------------------

    /**
     * RelationLayout is TABLE mode. calculateTableColumnWidth() returns a value
     * that still fits within availableWidth. No mode flip expected.
     *
     * tableColumnWidth = 50, nestedInset = 0, availableWidth = 200
     * 50 + 0 <= 200 → still fits → current mode TABLE == new mode TABLE → no flip
     */
    @Test
    void p90WithinThreshold_noModeFlip() {
        SchemaPath rootPath = new SchemaPath("root");
        SchemaPath primPath = rootPath.child("name");

        // useTable=true, calculateTableColumnWidth()=50, availableWidth=200 → fits
        RelationLayout rl = buildTableModeRelation(rootPath, primPath, 50.0);
        double availableWidth = 200.0;

        boolean flipped = AutoLayout.detectModeFlipForTest(Set.of(primPath), rl, availableWidth);

        assertFalse(flipped,
                "Table still fits in available width: no mode flip expected");
    }

    // -----------------------------------------------------------------------
    // Test 2: p90 increase crosses tableColumnWidth threshold → TABLE→OUTLINE
    // -----------------------------------------------------------------------

    /**
     * RelationLayout is TABLE mode (useTable=true). After re-measure,
     * calculateTableColumnWidth() now exceeds availableWidth. Mode flips TABLE→OUTLINE.
     *
     * tableColumnWidth = 50, nestedInset = 0, availableWidth = 40
     * 50 + 0 > 40 → would be OUTLINE → current mode TABLE != OUTLINE → FLIP
     */
    @Test
    void p90IncreaseCrossesThreshold_tableToOutlineFlip() {
        SchemaPath rootPath = new SchemaPath("root");
        SchemaPath primPath = rootPath.child("value");

        RelationLayout rl = buildTableModeRelation(rootPath, primPath, 50.0);
        double availableWidth = 40.0; // too narrow for tableColumnWidth=50

        boolean flipped = AutoLayout.detectModeFlipForTest(Set.of(primPath), rl, availableWidth);

        assertTrue(flipped,
                "Table no longer fits: TABLE→OUTLINE mode flip must be detected");
    }

    // -----------------------------------------------------------------------
    // Test 3: p90 decrease crosses threshold → OUTLINE→TABLE flip
    // -----------------------------------------------------------------------

    /**
     * RelationLayout is OUTLINE mode (useTable=false). After re-measure,
     * calculateTableColumnWidth() now fits in availableWidth. Mode flips OUTLINE→TABLE.
     *
     * tableColumnWidth = 30, nestedInset = 0, availableWidth = 100
     * 30 + 0 <= 100 → would be TABLE → current mode OUTLINE != TABLE → FLIP
     */
    @Test
    void p90DecreaseCrossesThreshold_outlineToTableFlip() {
        SchemaPath rootPath = new SchemaPath("root");
        SchemaPath primPath = rootPath.child("label");

        RelationLayout rl = buildOutlineModeRelation(rootPath, primPath, 30.0);
        double availableWidth = 100.0;

        boolean flipped = AutoLayout.detectModeFlipForTest(Set.of(primPath), rl, availableWidth);

        assertTrue(flipped,
                "Table now fits: OUTLINE→TABLE mode flip must be detected");
    }

    // -----------------------------------------------------------------------
    // Test 4: null rootLayout → fall through to full re-layout
    // -----------------------------------------------------------------------

    /**
     * When detectModeFlipForTest is called with null rootLayout, it must return
     * true so the caller falls through to full re-layout (conservative path).
     */
    @Test
    void nullRootLayout_fallsThroughToFullRelayout() {
        boolean flipped = AutoLayout.detectModeFlipForTest(
                Set.of(new SchemaPath("root", "x")),
                null,
                100.0);

        assertTrue(flipped,
                "Null rootLayout must return true to trigger full re-layout");
    }

    // -----------------------------------------------------------------------
    // Test 5: LCA subtree identification — only affected subtree checked
    // -----------------------------------------------------------------------

    /**
     * Two child RelationLayouts under a root. Only leftBranch has a changed path.
     * leftBranch is TABLE but its calculateTableColumnWidth() exceeds the available
     * width → flip detected. rightBranch is stable (not evaluated). The overall
     * result is true because leftBranch flips.
     */
    @Test
    void lcaSubtree_onlyAffectedSubtreeChecked() {
        SchemaPath rootPath  = new SchemaPath("root");
        SchemaPath leftPath  = rootPath.child("left");
        SchemaPath rightPath = rootPath.child("right");
        SchemaPath leftPrimPath  = leftPath.child("leftPrim");
        SchemaPath rightPrimPath = rightPath.child("rightPrim");

        // leftBranch: TABLE mode, tableColumnWidth=80, availableWidth=60 → won't fit → FLIP
        RelationLayout leftRl  = buildTableModeRelation(leftPath, leftPrimPath, 80.0);
        // rightBranch: TABLE mode, tableColumnWidth=30, availableWidth=60 → fits → no flip
        RelationLayout rightRl = buildTableModeRelation(rightPath, rightPrimPath, 30.0);

        Relation rootRel = new Relation("root");
        rootRel.addChild(new Relation("left"));
        rootRel.addChild(new Relation("right"));
        RelationLayout rootRl = new RelationLayout(rootRel, TestLayouts.mockRelationStyle());
        rootRl.children.add(leftRl);
        rootRl.children.add(rightRl);
        rootRl.setSchemaPath(rootPath);

        // Only leftPrimPath changed; availableWidth chosen so left flips but right doesn't
        double availableWidth = 60.0;
        boolean flipped = AutoLayout.detectModeFlipForTest(Set.of(leftPrimPath), rootRl, availableWidth);

        assertTrue(flipped,
                "LCA subtree: leftBranch flip must be detected from root walk");
    }

    // -----------------------------------------------------------------------
    // Test 6: changed path with no RelationLayout ancestor → no flip
    // -----------------------------------------------------------------------

    /**
     * Changed path is a single-segment path (no parent). No RelationLayout in
     * the tree has it as a descendant, so no mode flip is detected.
     */
    @Test
    void noAncestorRelation_noFlip() {
        SchemaPath rootPath = new SchemaPath("root");
        SchemaPath primPath = rootPath.child("name");

        // Tree only contains root/name, but changed path is unrelated
        RelationLayout rl = buildTableModeRelation(rootPath, primPath, 30.0);
        SchemaPath unrelatedPath = new SchemaPath("other", "field");

        boolean flipped = AutoLayout.detectModeFlipForTest(
                Set.of(unrelatedPath), rl, 200.0);

        assertFalse(flipped,
                "Unrelated changed path must not trigger mode flip detection");
    }
}
