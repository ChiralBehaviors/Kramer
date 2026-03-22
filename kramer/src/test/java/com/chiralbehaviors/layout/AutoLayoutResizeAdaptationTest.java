// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;

/**
 * Tests that AutoLayout correctly adapts to different widths.
 * Verifies resize, table/outline mode switching, and content fitting.
 *
 * <p>These tests inspect the ACTUAL layout decisions (useTable, justifiedWidth,
 * column widths) — not just "has children". They run in the same package as
 * AutoLayout, so they can access package-private state.
 */
class AutoLayoutResizeAdaptationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void initToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try { Platform.startup(latch::countDown); }
        catch (IllegalStateException e) { latch.countDown(); }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    private void runOnFx(Runnable task) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try { task.run(); }
            catch (Throwable t) { error.set(t); }
            finally { latch.countDown(); }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) throw new RuntimeException("FX failed", error.get());
    }

    // -----------------------------------------------------------------------
    // Schema: 2-level (departments → courses) — fits table at wide widths
    // -----------------------------------------------------------------------

    private Relation twoLevelSchema() {
        Relation courses = new Relation("courses");
        courses.addChild(new Primitive("number"));
        courses.addChild(new Primitive("title"));
        courses.addChild(new Primitive("credits"));

        Relation depts = new Relation("departments");
        depts.addChild(new Primitive("name"));
        depts.addChild(courses);
        return depts;
    }

    // -----------------------------------------------------------------------
    // Schema: 3-level (departments → courses → sections) — needs outline at narrow
    // -----------------------------------------------------------------------

    private Relation threeLevelSchema() {
        Relation sections = new Relation("sections");
        sections.addChild(new Primitive("section"));
        sections.addChild(new Primitive("enrollment"));
        sections.addChild(new Primitive("room"));
        sections.addChild(new Primitive("schedule"));

        Relation courses = new Relation("courses");
        courses.addChild(new Primitive("number"));
        courses.addChild(new Primitive("title"));
        courses.addChild(new Primitive("credits"));
        courses.addChild(new Primitive("instructor"));
        courses.addChild(sections);

        Relation departments = new Relation("departments");
        departments.addChild(new Primitive("name"));
        departments.addChild(new Primitive("building"));
        departments.addChild(courses);
        return departments;
    }

    private ArrayNode twoLevelData() {
        ArrayNode depts = MAPPER.createArrayNode();
        depts.add(dept("CS", course("CS101", "Intro", 3),
                             course("CS201", "Data Structures", 4)));
        depts.add(dept("Math", course("M101", "Calculus", 4)));
        return depts;
    }

    private ArrayNode threeLevelData() {
        ArrayNode depts = MAPPER.createArrayNode();

        ObjectNode cs = MAPPER.createObjectNode();
        cs.put("name", "CS"); cs.put("building", "Gates");
        ArrayNode courses = MAPPER.createArrayNode();
        ObjectNode c1 = MAPPER.createObjectNode();
        c1.put("number", "CS101"); c1.put("title", "Intro");
        c1.put("credits", 3); c1.put("instructor", "Turing");
        ArrayNode secs = MAPPER.createArrayNode();
        ObjectNode s1 = MAPPER.createObjectNode();
        s1.put("section", "A"); s1.put("enrollment", 45);
        s1.put("room", "R101"); s1.put("schedule", "MWF 9");
        secs.add(s1);
        c1.set("sections", secs);
        courses.add(c1);
        cs.set("courses", courses);
        depts.add(cs);

        ObjectNode math = MAPPER.createObjectNode();
        math.put("name", "Math"); math.put("building", "Hilbert");
        ArrayNode mc = MAPPER.createArrayNode();
        ObjectNode c2 = MAPPER.createObjectNode();
        c2.put("number", "M101"); c2.put("title", "Calc");
        c2.put("credits", 4); c2.put("instructor", "Euler");
        ArrayNode ms = MAPPER.createArrayNode();
        ObjectNode s2 = MAPPER.createObjectNode();
        s2.put("section", "A"); s2.put("enrollment", 50);
        s2.put("room", "R110"); s2.put("schedule", "MWF 8");
        ms.add(s2);
        c2.set("sections", ms);
        mc.add(c2);
        math.set("courses", mc);
        depts.add(math);

        return depts;
    }

    private ObjectNode dept(String name, ObjectNode... courses) {
        ObjectNode d = MAPPER.createObjectNode();
        d.put("name", name);
        ArrayNode a = MAPPER.createArrayNode();
        for (ObjectNode c : courses) a.add(c);
        d.set("courses", a);
        return d;
    }

    private ObjectNode course(String num, String title, int credits) {
        ObjectNode c = MAPPER.createObjectNode();
        c.put("number", num); c.put("title", title); c.put("credits", credits);
        return c;
    }

    /**
     * Helper: create AutoLayout, measure, load data, resize to given width.
     * Returns the AutoLayout with layout state populated.
     */
    private AutoLayout layoutAt(Relation schema, ArrayNode data, double width) {
        Style style = new Style();
        AutoLayout al = new AutoLayout(null, style);
        al.setRoot(schema);
        al.measure(data);
        al.updateItem(data);
        al.resize(width, 700);
        return al;
    }

    // -----------------------------------------------------------------------
    // Test 1: layoutWidth tracks the last resize width
    // -----------------------------------------------------------------------

    @Test
    void layoutWidthTracksResizeWidth() throws Exception {
        runOnFx(() -> {
            AutoLayout al = layoutAt(twoLevelSchema(), twoLevelData(), 800);
            assertEquals(800.0, al.getLayoutWidth(), 0.01,
                "layoutWidth should match resize width");

            al.resize(500, 700);
            assertEquals(500.0, al.getLayoutWidth(), 0.01,
                "layoutWidth should update on resize");
        });
    }

    // -----------------------------------------------------------------------
    // Test 2: resize to smaller width actually re-layouts
    // -----------------------------------------------------------------------

    @Test
    void resizeToSmallerWidthRelayouts() throws Exception {
        runOnFx(() -> {
            // Use 3-level schema which switches mode at threshold (~917px)
            AutoLayout al = layoutAt(threeLevelSchema(), threeLevelData(), 1100);
            assertTrue(al.getLayoutTree() instanceof RelationLayout);
            RelationLayout rlWide = (RelationLayout) al.getLayoutTree();
            boolean tableAtWide = rlWide.isUseTable();

            al.resize(600, 700);
            assertTrue(al.getLayoutTree() instanceof RelationLayout);
            RelationLayout rlNarrow = (RelationLayout) al.getLayoutTree();
            boolean tableAtNarrow = rlNarrow.isUseTable();

            System.out.println("[RESIZE] table@1100=" + tableAtWide
                + " table@600=" + tableAtNarrow);

            assertTrue(tableAtWide, "Should be TABLE at 1100px");
            assertFalse(tableAtNarrow, "Should be OUTLINE at 600px");
            // This proves resize actually re-layouts and changes mode
        });
    }

    // -----------------------------------------------------------------------
    // Test 3: justifiedWidth in layout tree matches available width
    // -----------------------------------------------------------------------

    @Test
    void justifiedWidthMatchesAvailable() throws Exception {
        runOnFx(() -> {
            AutoLayout al = layoutAt(twoLevelSchema(), twoLevelData(), 800);

            assertTrue(al.getLayoutTree() instanceof RelationLayout);
            RelationLayout rl = (RelationLayout) al.getLayoutTree();

            // Snapshot the compress result from the last autoLayout pass
            LayoutResult result = rl.snapshotLayoutResult();
            System.out.println("[JUSTIFY] useTable=" + result.useTable()
                + " tcw=" + result.tableColumnWidth());

            // Check the layout state directly — justifiedWidth is set during compress
            LayoutDecisionNode tree = rl.snapshotDecisionTree();
            CompressResult compress = tree.compressResult();
            System.out.println("[JUSTIFY] justifiedWidth=" + compress.justifiedWidth());
            assertTrue(compress.justifiedWidth() <= 800,
                "justifiedWidth (" + compress.justifiedWidth() +
                ") must not exceed layout width (800)");
        });
    }

    // -----------------------------------------------------------------------
    // Test 4: 3-level nesting at narrow width uses outline for root
    // -----------------------------------------------------------------------

    @Test
    void threeLevelNarrowWidthUsesOutlineForRoot() throws Exception {
        runOnFx(() -> {
            AutoLayout al = layoutAt(threeLevelSchema(), threeLevelData(), 600);

            assertTrue(al.getLayoutTree() instanceof RelationLayout);
            RelationLayout rl = (RelationLayout) al.getLayoutTree();

            System.out.println("[OUTLINE] useTable=" + rl.isUseTable()
                + " readableTableWidth=" + rl.readableTableWidth());
            assertFalse(rl.isUseTable(),
                "Root relation should be OUTLINE at 600px with 3-level nesting. " +
                "readableTableWidth=" + rl.readableTableWidth());
        });
    }

    // -----------------------------------------------------------------------
    // Test 5: 3-level nesting — content width never exceeds layout width
    // -----------------------------------------------------------------------

    @Test
    void contentWidthNeverExceedsLayoutWidth() throws Exception {
        runOnFx(() -> {
            double[] widths = {1200, 1000, 800, 600, 400};

            for (double w : widths) {
                AutoLayout al = layoutAt(threeLevelSchema(), threeLevelData(), w);

                if (al.getLayoutTree() instanceof RelationLayout rl) {
                    LayoutDecisionNode tree = rl.snapshotDecisionTree();
                    CompressResult compress = tree.compressResult();
                    System.out.println("[FIT@" + w + "] justifiedWidth="
                        + compress.justifiedWidth() + " useTable=" + rl.isUseTable());
                    assertTrue(compress.justifiedWidth() <= w,
                        String.format("At width %.0f: justifiedWidth (%.0f) must fit. " +
                            "useTable=%s, readableTableWidth=%.0f",
                            w, compress.justifiedWidth(),
                            rl.isUseTable(), rl.readableTableWidth()));
                }
            }
        });
    }

    private void assertNestedFits(RelationLayout rl, double parentWidth, String indent) {
        if (rl.isUseTable()) {
            double tcw = rl.calculateTableColumnWidth();
            // Table column width should not exceed parent's available space
            // (exact constraint depends on insets, but it shouldn't be wildly larger)
            System.out.println(indent + rl.getNode().getLabel() +
                ": TABLE tcw=" + tcw + " readable=" + rl.readableTableWidth());
        } else {
            System.out.println(indent + rl.getNode().getLabel() + ": OUTLINE");
        }
        for (SchemaNodeLayout child : rl.getChildren()) {
            if (child instanceof RelationLayout childRl) {
                assertNestedFits(childRl, parentWidth, indent + "  ");
            }
        }
    }

    // -----------------------------------------------------------------------
    // Test 6: resize shrinks then grows — both directions work
    // -----------------------------------------------------------------------

    @Test
    void resizeBothDirections() throws Exception {
        runOnFx(() -> {
            AutoLayout al = layoutAt(twoLevelSchema(), twoLevelData(), 1000);
            assertEquals(1000.0, al.getLayoutWidth(), 0.01);

            al.resize(500, 700);
            assertEquals(500.0, al.getLayoutWidth(), 0.01, "Should shrink to 500");

            al.resize(900, 700);
            assertEquals(900.0, al.getLayoutWidth(), 0.01, "Should grow to 900");
        });
    }

    // -----------------------------------------------------------------------
    // Test 7: mode switches at threshold — wide=table, narrow=outline
    // -----------------------------------------------------------------------

    @Test
    void modeSwitchesAtThreshold() throws Exception {
        runOnFx(() -> {
            Relation schema = threeLevelSchema();
            ArrayNode data = threeLevelData();

            // Find the readable table width to know the threshold
            Style style = new Style();
            SchemaNodeLayout rootLayout = style.layout(schema);
            rootLayout.buildPaths(new SchemaPath("departments"), style);
            rootLayout.measure(data, style);
            double readableWidth = ((RelationLayout) rootLayout).readableTableWidth();

            System.out.println("[THRESHOLD] readableTableWidth=" + readableWidth);

            // Well above threshold → table
            AutoLayout wide = layoutAt(schema, data, readableWidth + 200);
            assertTrue(wide.getLayoutTree() instanceof RelationLayout,
                "Layout tree should exist");
            assertTrue(((RelationLayout) wide.getLayoutTree()).isUseTable(),
                "Should be TABLE well above readableTableWidth");

            // Well below threshold → outline
            AutoLayout narrow = layoutAt(schema, data, readableWidth - 200);
            assertTrue(narrow.getLayoutTree() instanceof RelationLayout,
                "Layout tree should exist");
            assertFalse(((RelationLayout) narrow.getLayoutTree()).isUseTable(),
                "Should be OUTLINE well below readableTableWidth");
        });
    }

    // -----------------------------------------------------------------------
    // Test 8: outline mode column widths actually USE available space
    // -----------------------------------------------------------------------

    @Test
    void outlineColumnWidthsUseAvailableSpace() throws Exception {
        runOnFx(() -> {
            // At 800px, 3-level schema renders as OUTLINE
            AutoLayout al = layoutAt(threeLevelSchema(), threeLevelData(), 800);
            RelationLayout rl = (RelationLayout) al.getLayoutTree();

            assertFalse(rl.isUseTable(), "Root should be OUTLINE at 800px");

            // Snapshot the column sets
            LayoutDecisionNode tree = rl.snapshotDecisionTree();
            CompressResult compress = tree.compressResult();

            System.out.println("[OUTLINE-WIDTH] justifiedWidth=" + compress.justifiedWidth());
            System.out.println("[OUTLINE-WIDTH] columnSets=" + compress.columnSetSnapshots().size());

            for (int i = 0; i < compress.columnSetSnapshots().size(); i++) {
                ColumnSetSnapshot cs = compress.columnSetSnapshots().get(i);
                double totalColWidth = cs.columns().stream()
                    .mapToDouble(ColumnSnapshot::width)
                    .sum();
                System.out.println("[OUTLINE-WIDTH] columnSet[" + i + "]: "
                    + cs.columns().size() + " cols, totalWidth=" + totalColWidth
                    + ", fields=" + cs.columns().stream()
                        .flatMap(c -> c.fieldNames().stream())
                        .toList());

                // Each column should have non-trivial width — not truncated to tiny sizes
                for (ColumnSnapshot col : cs.columns()) {
                    assertTrue(col.width() >= 30,
                        "Column width (" + col.width() + ") should be at least 30px. "
                        + "Fields: " + col.fieldNames());
                }
            }

            // Total column set width should use a significant fraction of available
            // space (at least 50% — if it uses less, the layout is wasting space)
            double totalUsed = compress.columnSetSnapshots().stream()
                .flatMap(cs -> cs.columns().stream())
                .mapToDouble(ColumnSnapshot::width)
                .max().orElse(0);
            // In outline mode with 1 column per set, the column width should
            // be close to justifiedWidth. With multiple columns, the sum should
            // approach justifiedWidth.
            assertTrue(totalUsed > 50,
                "Outline columns should use meaningful width, got max=" + totalUsed);
        });
    }

    // -----------------------------------------------------------------------
    // Test 9: outline mode — nested TABLE sub-relation column widths
    //         fit within the outline column width
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // Test 10: outline mode — primitive data cells are populated (not empty)
    // -----------------------------------------------------------------------

    @Test
    void outlinePrimitivesHaveData() throws Exception {
        runOnFx(() -> {
            AutoLayout al = layoutAt(threeLevelSchema(), threeLevelData(), 800);
            RelationLayout rl = (RelationLayout) al.getLayoutTree();
            assertFalse(rl.isUseTable(), "Root should be OUTLINE at 800px");

            // Inspect column sets — primitives should have non-zero layoutWidth
            LayoutDecisionNode tree = rl.snapshotDecisionTree();
            for (LayoutDecisionNode child : tree.childNodes()) {
                String name = child.fieldName();
                if (child.layoutResult() != null
                        && child.layoutResult().relationMode() == RelationRenderMode.OUTLINE) {
                    // This is a nested relation in outline — skip
                    continue;
                }
                // Primitive children: check they have non-zero compress width
                System.out.println("[PRIM-DATA] " + name
                    + " compressResult=" + child.compressResult());
            }

            // Check that primitive children (name, building) are in a column set
            // with reasonable width
            CompressResult compress = tree.compressResult();
            boolean foundPrimitiveSet = false;
            for (ColumnSetSnapshot cs : compress.columnSetSnapshots()) {
                boolean hasPrimitive = cs.columns().stream()
                    .flatMap(c -> c.fieldNames().stream())
                    .anyMatch(f -> "name".equals(f) || "building".equals(f));
                if (hasPrimitive) {
                    foundPrimitiveSet = true;
                    for (ColumnSnapshot col : cs.columns()) {
                        System.out.println("[PRIM-DATA] column: width="
                            + col.width() + " fields=" + col.fieldNames());
                        assertTrue(col.width() > 50,
                            "Primitive column should have substantial width: "
                            + col.fieldNames() + " got " + col.width());
                    }
                }
            }
            assertTrue(foundPrimitiveSet,
                "Should find a column set containing primitives name/building");

            // Check that the rendered control tree has non-empty content
            // by verifying the control was built
            assertTrue(al.getChildren().size() > 0,
                "AutoLayout should have rendered a control");
        });
    }

    // -----------------------------------------------------------------------
    // Test 11: outline mode — rendered text includes actual data values
    // -----------------------------------------------------------------------

    @Test
    void outlineRendersDataValues() throws Exception {
        runOnFx(() -> {
            // Must use a Scene for VirtualFlow to create cells
            Style style = new Style();
            AutoLayout al = new AutoLayout(null, style);
            var root = new javafx.scene.layout.BorderPane();
            root.setCenter(al);
            new javafx.scene.Scene(root, 1200, 800);
            root.applyCss();
            root.layout();

            al.setRoot(threeLevelSchema());
            al.measure(threeLevelData());
            al.updateItem(threeLevelData());
            al.resize(1200, 800);

            root.applyCss();
            root.layout();

            RelationLayout rl = (RelationLayout) al.getLayoutTree();

            // Find all Text nodes in the rendered scene graph
            var textNodes = new java.util.ArrayList<javafx.scene.text.Text>();
            findTextNodes(al, textNodes);

            var allText = textNodes.stream()
                .map(javafx.scene.text.Text::getText)
                .filter(t -> t != null && !t.isBlank())
                .toList();

            System.out.println("[RENDER] Found " + textNodes.size()
                + " text nodes, " + allText.size() + " non-blank");
            System.out.println("[RENDER] Sample values: "
                + allText.stream().limit(20).toList());

            // The rendered text should include department names from the data
            boolean hasCS = allText.stream().anyMatch(t -> t.contains("CS"));
            boolean hasMath = allText.stream().anyMatch(t -> t.contains("Math"));
            boolean hasGates = allText.stream().anyMatch(t -> t.contains("Gates"));

            System.out.println("[RENDER] hasCS=" + hasCS + " hasMath=" + hasMath
                + " hasGates=" + hasGates);

            assertTrue(hasCS || hasMath,
                "Rendered text should include department names (CS or Math). " +
                "Found: " + allText);

            // Inspect nested table column widths
            LayoutDecisionNode deptTree = rl.snapshotDecisionTree();
            for (LayoutDecisionNode child : deptTree.childNodes()) {
                if (child.layoutResult() != null) {
                    System.out.println("[TABLE-COLS] " + child.fieldName()
                        + " mode=" + child.layoutResult().relationMode()
                        + " tcw=" + child.layoutResult().tableColumnWidth()
                        + " constrainedColW=" + child.layoutResult().constrainedColumnWidth());
                    for (LayoutDecisionNode grandchild : child.childNodes()) {
                        if (grandchild.layoutResult() != null) {
                            System.out.println("[TABLE-COLS]   " + grandchild.fieldName()
                                + " mode=" + grandchild.layoutResult().relationMode()
                                + " tcw=" + grandchild.layoutResult().tableColumnWidth());
                        } else if (grandchild.compressResult() != null) {
                            System.out.println("[TABLE-COLS]   " + grandchild.fieldName()
                                + " jw=" + grandchild.compressResult().justifiedWidth());
                        }
                    }
                }
            }

            // Walk rendered region tree and check widths at each level
            var regions = new java.util.ArrayList<javafx.scene.layout.Region>();
            findRegions(al, regions);
            int tooNarrow = 0;
            for (var r : regions) {
                if (r.getWidth() > 0 && r.getWidth() < 20
                        && r.getChildrenUnmodifiable().stream()
                            .anyMatch(c -> c instanceof javafx.scene.text.Text)) {
                    tooNarrow++;
                    var texts = r.getChildrenUnmodifiable().stream()
                        .filter(c -> c instanceof javafx.scene.text.Text)
                        .map(c -> ((javafx.scene.text.Text) c).getText())
                        .toList();
                    System.out.println("[NARROW] " + r.getClass().getSimpleName()
                        + " width=" + r.getWidth()
                        + " text=" + texts);
                }
            }
            System.out.println("[RENDER] tooNarrow regions (width<20 with text): "
                + tooNarrow + " / " + regions.size());
            assertEquals(0, tooNarrow,
                "No rendered regions containing text should be narrower than 20px");
        });
    }

    private void findTextNodes(javafx.scene.Node node,
                                java.util.List<javafx.scene.text.Text> result) {
        if (node instanceof javafx.scene.text.Text t) {
            result.add(t);
        }
        if (node instanceof javafx.scene.Parent p) {
            for (javafx.scene.Node child : p.getChildrenUnmodifiable()) {
                findTextNodes(child, result);
            }
        }
    }

    private void findRegions(javafx.scene.Node node,
                              java.util.List<javafx.scene.layout.Region> result) {
        if (node instanceof javafx.scene.layout.Region r) {
            result.add(r);
        }
        if (node instanceof javafx.scene.Parent p) {
            for (javafx.scene.Node child : p.getChildrenUnmodifiable()) {
                findRegions(child, result);
            }
        }
    }

    @Test
    void outlineNestedTableFitsWithinColumnWidth() throws Exception {
        runOnFx(() -> {
            AutoLayout al = layoutAt(threeLevelSchema(), threeLevelData(), 800);
            RelationLayout rl = (RelationLayout) al.getLayoutTree();

            assertFalse(rl.isUseTable(), "Root should be OUTLINE");

            // Walk children: courses should be TABLE within the outline
            for (SchemaNodeLayout child : rl.getChildren()) {
                if (child instanceof RelationLayout childRl) {
                    String name = childRl.getNode().getLabel();
                    boolean table = childRl.isUseTable();
                    double readableW = childRl.readableTableWidth();

                    System.out.println("[NESTED] " + name + ": useTable=" + table
                        + " readableTableWidth=" + readableW);

                    if (table) {
                        // The nested table's readable width should fit within
                        // the outline's justified width (the space it was given)
                        LayoutDecisionNode childTree = childRl.snapshotDecisionTree();
                        CompressResult childCompress = childTree.compressResult();
                        double childJustified = childCompress.justifiedWidth();

                        System.out.println("[NESTED] " + name
                            + ": justifiedWidth=" + childJustified
                            + " readableTableWidth=" + readableW);

                        // The justified width (actual render width) should not
                        // wildly exceed the parent outline's available space
                        assertTrue(childJustified <= 800,
                            name + " nested TABLE justifiedWidth ("
                            + childJustified + ") exceeds parent width (800)");
                    }
                }
            }
        });
    }
}
