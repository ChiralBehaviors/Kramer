// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.explorer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.*;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javafx.application.Platform;

/**
 * Tests that 3-level nesting correctly uses outline mode when table mode
 * would overflow. Verifies the constraint solver's decisions directly.
 */
class ThreeLevelNestingTest {

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

    private Relation buildSchema() {
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

    private ArrayNode buildData() {
        ArrayNode depts = MAPPER.createArrayNode();
        depts.add(dept("CS", "Gates",
            course("CS101", "Intro to Prog", 3, "Turing",
                section("A", 45, "Room 101", "MWF 9:00")),
            course("CS201", "Data Structures", 4, "Knuth",
                section("A", 35, "Room 301", "MWF 11:00"))));
        depts.add(dept("Math", "Hilbert",
            course("MATH101", "Calculus I", 4, "Euler",
                section("A", 50, "Room 110", "MWF 8:00"))));
        return depts;
    }

    private ObjectNode dept(String n, String b, ObjectNode... c) {
        ObjectNode d = MAPPER.createObjectNode(); d.put("name", n); d.put("building", b);
        ArrayNode a = MAPPER.createArrayNode(); for (ObjectNode x : c) a.add(x);
        d.set("courses", a); return d;
    }
    private ObjectNode course(String num, String t, int cr, String i, ObjectNode... s) {
        ObjectNode c = MAPPER.createObjectNode(); c.put("number", num); c.put("title", t);
        c.put("credits", cr); c.put("instructor", i);
        ArrayNode a = MAPPER.createArrayNode(); for (ObjectNode x : s) a.add(x);
        c.set("sections", a); return c;
    }
    private ObjectNode section(String n, int e, String r, String s) {
        ObjectNode o = MAPPER.createObjectNode(); o.put("section", n);
        o.put("enrollment", e); o.put("room", r); o.put("schedule", s); return o;
    }

    /**
     * Measure the schema, compute readableTableWidth, and verify the solver
     * assigns OUTLINE to at least one relation when table is too wide.
     */
    @Test
    void solverAssignsOutlineWhenTableTooWide() throws Exception {
        runOnFx(() -> {
            Style style = new Style();
            Relation schema = buildSchema();
            ArrayNode data = buildData();

            SchemaNodeLayout rootLayout = style.layout(schema);
            rootLayout.buildPaths(new SchemaPath("departments"), style);
            rootLayout.measure(data, style);

            assertTrue(rootLayout instanceof RelationLayout);
            RelationLayout rl = (RelationLayout) rootLayout;

            double readableWidth = rl.readableTableWidth();
            double availableWidth = 1000.0;

            System.out.println("[SOLVER] readableTableWidth=" + readableWidth
                + " available=" + availableWidth);

            // Build constraint tree
            RelationConstraint constraint = buildConstraintTree(rl, availableWidth);
            System.out.println("[SOLVER] root fitsTable=" + constraint.fitsTable());

            // Run solver
            var solver = new ExhaustiveConstraintSolver();
            Map<SchemaPath, RelationRenderMode> result = solver.solve(constraint);

            System.out.println("[SOLVER] assignments (size=" + result.size() + "):");
            for (var entry : result.entrySet()) {
                System.out.println("  " + entry.getKey() + " → " + entry.getValue());
            }

            if (readableWidth > availableWidth) {
                // Table too wide → solver MUST assign outline to at least one
                boolean hasOutline = result.values().stream()
                    .anyMatch(m -> m == RelationRenderMode.OUTLINE);
                assertTrue(hasOutline,
                    "Solver must assign OUTLINE when readableTableWidth (" +
                    readableWidth + ") > available (" + availableWidth +
                    "). Assignments: " + result);
            }
        });
    }

    private RelationConstraint buildConstraintTree(RelationLayout rl,
                                                    double availableWidth) {
        double tableWidth = rl.calculateTableColumnWidth();
        double nestedInset = rl.getStyle().getNestedHorizontalInset();
        double lw = rl.getLabelWidth();
        double childOutline = (availableWidth - lw)
            - rl.getStyle().getOutlineCellHorizontalInset();
        long numRel = rl.getChildren().stream()
            .filter(c -> c instanceof RelationLayout).count();
        double childTable = numRel > 0 ? tableWidth / numRel : Double.MAX_VALUE;

        List<RelationConstraint> children = new ArrayList<>();
        for (SchemaNodeLayout child : rl.getChildren()) {
            if (child instanceof RelationLayout childRl) {
                children.add(buildConstraintTree(childRl, childOutline));
            }
        }

        return new RelationConstraint(
            rl.getSchemaPath(), tableWidth, rl.readableTableWidth(),
            nestedInset, availableWidth, Double.MAX_VALUE,
            children, false, 0.0, false);
    }
}
