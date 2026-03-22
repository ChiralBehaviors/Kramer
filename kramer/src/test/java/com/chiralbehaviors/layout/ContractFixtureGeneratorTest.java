// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javafx.application.Platform;

/**
 * Generates contract test fixtures (JSON) for cross-language validation.
 * The fixtures are written to test/fixtures/ at the repo root and consumed
 * by both Java and TypeScript tests.
 */
class ContractFixtureGeneratorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private static final Path FIXTURES_DIR =
        Path.of(System.getProperty("user.dir")).getParent().resolve("test/fixtures");

    @BeforeAll
    static void initToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try { Platform.startup(latch::countDown); }
        catch (IllegalStateException e) { latch.countDown(); }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    private void runOnFx(Runnable task) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        var error = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Platform.runLater(() -> {
            try { task.run(); }
            catch (Throwable t) { error.set(t); }
            finally { latch.countDown(); }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) throw new RuntimeException("FX failed", error.get());
    }

    @Test
    void generateFlatFixture() throws Exception {
        runOnFx(() -> {
            try {
                Relation schema = new Relation("items");
                schema.addChild(new Primitive("name"));
                schema.addChild(new Primitive("price"));
                schema.addChild(new Primitive("quantity"));

                ArrayNode data = MAPPER.createArrayNode();
                data.add(item("Widget", 9.99, 100));
                data.add(item("Gadget", 24.50, 42));
                data.add(item("Doohickey", 3.75, 500));

                double width = 800;

                // Run pipeline
                Style style = new Style();
                AutoLayout al = new AutoLayout(null, style);
                al.setRoot(schema);
                al.measure(data);
                al.updateItem(data);
                al.resize(width, 600);

                RelationLayout rl = (RelationLayout) al.getLayoutTree();
                LayoutDecisionNode decision = rl.snapshotDecisionTree();

                // Build fixture
                ObjectNode fixture = MAPPER.createObjectNode();
                fixture.set("schema", serializeSchema(schema));
                fixture.set("data", data);
                fixture.put("width", width);
                fixture.set("expected", MAPPER.valueToTree(decision));

                writeFixture("flat-3-primitives.json", fixture);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void generateNestedFixture() throws Exception {
        runOnFx(() -> {
            try {
                Relation courses = new Relation("courses");
                courses.addChild(new Primitive("number"));
                courses.addChild(new Primitive("title"));
                courses.addChild(new Primitive("credits"));

                Relation depts = new Relation("departments");
                depts.addChild(new Primitive("name"));
                depts.addChild(new Primitive("building"));
                depts.addChild(courses);

                ArrayNode data = MAPPER.createArrayNode();
                ObjectNode cs = MAPPER.createObjectNode();
                cs.put("name", "CS");
                cs.put("building", "Gates");
                ArrayNode csCourses = MAPPER.createArrayNode();
                csCourses.add(course("CS101", "Intro", 3));
                csCourses.add(course("CS201", "Data Structures", 4));
                cs.set("courses", csCourses);
                data.add(cs);

                ObjectNode math = MAPPER.createObjectNode();
                math.put("name", "Math");
                math.put("building", "Hilbert");
                ArrayNode mathCourses = MAPPER.createArrayNode();
                mathCourses.add(course("MATH101", "Calculus", 4));
                math.set("courses", mathCourses);
                data.add(math);

                double width = 600;

                Style style = new Style();
                AutoLayout al = new AutoLayout(null, style);
                al.setRoot(depts);
                al.measure(data);
                al.updateItem(data);
                al.resize(width, 700);

                RelationLayout rl = (RelationLayout) al.getLayoutTree();
                LayoutDecisionNode decision = rl.snapshotDecisionTree();

                ObjectNode fixture = MAPPER.createObjectNode();
                fixture.set("schema", serializeSchema(depts));
                fixture.set("data", data);
                fixture.put("width", width);
                fixture.set("expected", MAPPER.valueToTree(decision));

                writeFixture("nested-2-level.json", fixture);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void generateThresholdFixture() throws Exception {
        runOnFx(() -> {
            try {
                Relation courses = new Relation("courses");
                courses.addChild(new Primitive("number"));
                courses.addChild(new Primitive("title"));
                courses.addChild(new Primitive("credits"));

                Relation depts = new Relation("departments");
                depts.addChild(new Primitive("name"));
                depts.addChild(new Primitive("building"));
                depts.addChild(courses);

                ArrayNode data = MAPPER.createArrayNode();
                ObjectNode cs = MAPPER.createObjectNode();
                cs.put("name", "CS");
                cs.put("building", "Gates");
                ArrayNode csCourses = MAPPER.createArrayNode();
                csCourses.add(course("CS101", "Intro", 3));
                cs.set("courses", csCourses);
                data.add(cs);

                // Generate at multiple widths
                ArrayNode widthTests = MAPPER.createArrayNode();
                for (double w : new double[]{1200, 800, 600, 400, 200}) {
                    Style style = new Style();
                    AutoLayout al = new AutoLayout(null, style);
                    al.setRoot(depts);
                    al.measure(data);
                    al.updateItem(data);
                    al.resize(w, 700);

                    RelationLayout rl = (RelationLayout) al.getLayoutTree();

                    ObjectNode entry = MAPPER.createObjectNode();
                    entry.put("width", w);
                    entry.put("rootMode", rl.isUseTable() ? "TABLE" : "OUTLINE");
                    widthTests.add(entry);
                }

                ObjectNode fixture = MAPPER.createObjectNode();
                fixture.set("schema", serializeSchema(depts));
                fixture.set("data", data);
                fixture.set("widthTests", widthTests);

                writeFixture("threshold-switching.json", fixture);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // --- Helpers ---

    private ObjectNode item(String name, double price, int qty) {
        ObjectNode o = MAPPER.createObjectNode();
        o.put("name", name); o.put("price", price); o.put("quantity", qty);
        return o;
    }

    private ObjectNode course(String number, String title, int credits) {
        ObjectNode o = MAPPER.createObjectNode();
        o.put("number", number); o.put("title", title); o.put("credits", credits);
        return o;
    }

    private JsonNode serializeSchema(Relation r) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("kind", "relation");
        node.put("field", r.getField());
        ArrayNode children = MAPPER.createArrayNode();
        for (var child : r.getChildren()) {
            if (child instanceof Primitive p) {
                ObjectNode pn = MAPPER.createObjectNode();
                pn.put("kind", "primitive");
                pn.put("field", p.getField());
                children.add(pn);
            } else if (child instanceof Relation cr) {
                children.add(serializeSchema(cr));
            }
        }
        node.set("children", children);
        return node;
    }

    private void writeFixture(String filename, ObjectNode fixture) throws Exception {
        Files.createDirectories(FIXTURES_DIR);
        File file = FIXTURES_DIR.resolve(filename).toFile();
        MAPPER.writeValue(file, fixture);
        System.out.println("[FIXTURE] Wrote " + file.getAbsolutePath());
    }
}
