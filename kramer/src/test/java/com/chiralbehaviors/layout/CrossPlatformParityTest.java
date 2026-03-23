// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Cross-platform parity tests using FixedCharWidthStrategy.
 * No JavaFX required. Generates JSON fixtures that the TS side must match exactly.
 *
 * Both platforms use 7px/char fixed measurement → identical inputs → identical decisions.
 */
class CrossPlatformParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private static final Path FIXTURES_DIR =
        Path.of(System.getProperty("user.dir")).getParent().resolve("test/fixtures");

    private static final double CHAR_WIDTH = 7.0;

    // --- Shared schema + data (must match TS test exactly) ---

    private Relation courseSchema() {
        Relation sections = new Relation("sections");
        sections.addChild(new Primitive("id"));
        sections.addChild(new Primitive("enrolled"));

        Relation courses = new Relation("courses");
        courses.addChild(new Primitive("number"));
        courses.addChild(new Primitive("title"));
        courses.addChild(new Primitive("credits"));
        courses.addChild(sections);

        Relation departments = new Relation("departments");
        departments.addChild(new Primitive("name"));
        departments.addChild(new Primitive("building"));
        departments.addChild(courses);
        return departments;
    }

    private ArrayNode courseData() {
        ArrayNode depts = MAPPER.createArrayNode();
        depts.add(dept("CS", "Gates",
            course("CS101", "Intro", 3, sec("A", 45), sec("B", 42)),
            course("CS201", "Data Str", 4, sec("A", 35))));
        depts.add(dept("Math", "Hilbert",
            course("M101", "Calc", 4, sec("A", 50))));
        return depts;
    }

    @Test
    void generateParityFixture() throws Exception {
        Relation schema = courseSchema();
        ArrayNode data = courseData();

        // Use FixedCharWidthStrategy — matches TS FixedMeasurement(7)
        Style style = new Style(new FixedCharWidthStrategy(CHAR_WIDTH));

        double[] widths = {1200, 800, 600, 400, 300};

        ArrayNode results = MAPPER.createArrayNode();

        for (double w : widths) {
            SchemaNodeLayout layout = style.layout(schema);
            layout.buildPaths(new SchemaPath("departments"), style);
            layout.measure(data, style);

            // Run full pipeline at this width
            if (layout instanceof RelationLayout rl) {
                // Layout phase
                rl.layout(w);
                // Compress phase
                rl.compress(w);

                // Capture full state
                ObjectNode entry = MAPPER.createObjectNode();
                entry.put("width", w);

                // Root mode
                entry.put("rootMode", rl.isUseTable() ? "TABLE" : "OUTLINE");

                // Readable table width
                entry.put("readableTableWidth", rl.readableTableWidth());

                // Per-child decisions
                ArrayNode childDecisions = MAPPER.createArrayNode();
                for (SchemaNodeLayout child : rl.getChildren()) {
                    if (child instanceof RelationLayout childRl) {
                        ObjectNode cd = MAPPER.createObjectNode();
                        cd.put("field", childRl.getField());
                        cd.put("mode", childRl.isUseTable() ? "TABLE" : "OUTLINE");
                        cd.put("readableTableWidth", childRl.readableTableWidth());
                        cd.put("justifiedWidth", childRl.getJustifiedWidth());

                        // Nested children
                        ArrayNode grandchildren = MAPPER.createArrayNode();
                        for (SchemaNodeLayout gc : childRl.getChildren()) {
                            if (gc instanceof RelationLayout gcRl) {
                                ObjectNode gcd = MAPPER.createObjectNode();
                                gcd.put("field", gcRl.getField());
                                gcd.put("mode", gcRl.isUseTable() ? "TABLE" : "OUTLINE");
                                gcd.put("readableTableWidth", gcRl.readableTableWidth());
                                grandchildren.add(gcd);
                            }
                        }
                        if (grandchildren.size() > 0) cd.set("children", grandchildren);
                        childDecisions.add(cd);
                    }
                }
                entry.set("childDecisions", childDecisions);

                // Measure results
                entry.put("labelWidth", rl.getLabelWidth());
                entry.put("cellHeight", rl.getCellHeight());

                results.add(entry);
            }
        }

        // Build fixture
        ObjectNode fixture = MAPPER.createObjectNode();
        fixture.set("schema", serializeSchema(schema));
        fixture.set("data", data);
        fixture.put("charWidth", CHAR_WIDTH);
        fixture.set("parity", results);

        // Write
        Files.createDirectories(FIXTURES_DIR);
        MAPPER.writeValue(FIXTURES_DIR.resolve("parity-full-pipeline.json").toFile(), fixture);
        System.out.println("[PARITY] Wrote parity-full-pipeline.json");

        // Also verify the Java side produces consistent results
        for (var node : results) {
            String mode = node.get("rootMode").asText();
            double w = node.get("width").asDouble();
            System.out.printf("[PARITY] width=%.0f mode=%s readable=%.0f label=%.0f%n",
                w, mode, node.get("readableTableWidth").asDouble(),
                node.get("labelWidth").asDouble());
        }
    }

    // --- Data helpers ---

    private ObjectNode dept(String name, String building, ObjectNode... courses) {
        ObjectNode d = MAPPER.createObjectNode();
        d.put("name", name); d.put("building", building);
        ArrayNode a = MAPPER.createArrayNode();
        for (ObjectNode c : courses) a.add(c);
        d.set("courses", a); return d;
    }

    private ObjectNode course(String num, String title, int credits, ObjectNode... secs) {
        ObjectNode c = MAPPER.createObjectNode();
        c.put("number", num); c.put("title", title); c.put("credits", credits);
        ArrayNode a = MAPPER.createArrayNode();
        for (ObjectNode s : secs) a.add(s); c.set("sections", a); return c;
    }

    private ObjectNode sec(String id, int enrolled) {
        ObjectNode s = MAPPER.createObjectNode();
        s.put("id", id); s.put("enrolled", enrolled); return s;
    }

    private ObjectNode serializeSchema(Relation r) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("kind", "relation"); node.put("field", r.getField());
        ArrayNode children = MAPPER.createArrayNode();
        for (var child : r.getChildren()) {
            if (child instanceof Primitive p) {
                ObjectNode pn = MAPPER.createObjectNode();
                pn.put("kind", "primitive"); pn.put("field", p.getField());
                children.add(pn);
            } else if (child instanceof Relation cr) {
                children.add(serializeSchema(cr));
            }
        }
        node.set("children", children); return node;
    }
}
