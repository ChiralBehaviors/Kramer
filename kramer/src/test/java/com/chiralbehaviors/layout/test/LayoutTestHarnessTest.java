// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * Verifies that {@link LayoutTestHarness} itself works correctly.
 */
@ExtendWith(ApplicationExtension.class)
class LayoutTestHarnessTest {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;
    private Stage testStage;

    @Start
    void start(Stage stage) {
        this.testStage = stage;
        stage.setScene(new Scene(new Pane(), 1200, 600));
        stage.show();
    }

    private Relation trivialSchema() {
        Relation r = new Relation("items");
        r.addChild(new Primitive("name"));
        r.addChild(new Primitive("value"));
        return r;
    }

    private ArrayNode trivialData() {
        ArrayNode data = NF.arrayNode();
        for (String[] row : new String[][] {
            {"Alice", "100"}, {"Bob", "200"}, {"Charlie", "300"}
        }) {
            ObjectNode obj = NF.objectNode();
            obj.put("name", row[0]);
            obj.put("value", Integer.parseInt(row[1]));
            data.add(obj);
        }
        return data;
    }

    @Test
    void harnessReturnsNonNullResult() {
        var harness = new LayoutTestHarness(testStage, trivialSchema(),
            trivialData(), Set.of("items", "name", "value"));
        LayoutTestResult r = harness.run(800, 400);
        assertNotNull(r, "run() must return a result");
    }

    @Test
    void resultContainsLabels() {
        var harness = new LayoutTestHarness(testStage, trivialSchema(),
            trivialData(), Set.of("items", "name", "value"));
        LayoutTestResult r = harness.run(800, 400);
        assertFalse(r.getRenderedTexts().isEmpty(),
            "Scene graph must contain labels. Dump:\n" + r.dump());
    }

    @Test
    void resultContainsDataValues() {
        var harness = new LayoutTestHarness(testStage, trivialSchema(),
            trivialData(), Set.of("items", "name", "value"));
        LayoutTestResult r = harness.run(800, 400);

        assertTrue(r.containsData("Alice"),
            "Data value 'Alice' must appear. Dump:\n" + r.dump());
        assertTrue(r.containsData("Bob"),
            "Data value 'Bob' must appear. Dump:\n" + r.dump());
    }

    @Test
    void dataTextsExcludesFieldNames() {
        var harness = new LayoutTestHarness(testStage, trivialSchema(),
            trivialData(), Set.of("items", "name", "value"));
        LayoutTestResult r = harness.run(800, 400);

        var dataTexts = r.getDataTexts();
        assertFalse(dataTexts.contains("name"),
            "Field name 'name' should not be in data texts");
        assertFalse(dataTexts.contains("value"),
            "Field name 'value' should not be in data texts");
    }

    @Test
    void tableModeAtWideWidth() {
        var harness = new LayoutTestHarness(testStage, trivialSchema(),
            trivialData(), Set.of("items", "name", "value"));
        LayoutTestResult r = harness.run(800, 400);
        assertTrue(r.tableMode(),
            "Trivial 2-column schema should be table at 800px");
    }

    @Test
    void fieldWidthsPopulated() {
        var harness = new LayoutTestHarness(testStage, trivialSchema(),
            trivialData(), Set.of("items", "name", "value"));
        LayoutTestResult r = harness.run(800, 400);

        assertFalse(r.fieldWidths().isEmpty(),
            "Field widths must be populated");
        assertTrue(r.fieldWidths().containsKey("name"),
            "Field widths must include 'name'");
        assertTrue(r.fieldWidths().get("name") > 0,
            "Field 'name' width must be > 0");
    }

    @Test
    void multipleRunsSimulateResize() {
        var harness = new LayoutTestHarness(testStage, trivialSchema(),
            trivialData(), Set.of("items", "name", "value"));

        LayoutTestResult wide = harness.run(800, 400);
        LayoutTestResult narrow = harness.run(300, 400);

        // Both must have data
        assertTrue(wide.containsData("Alice"),
            "Wide: Alice must appear. Dump:\n" + wide.dump());
        assertTrue(narrow.containsData("Alice"),
            "Narrow: Alice must appear after resize. Dump:\n" + narrow.dump());
    }

    @Test
    void dumpProducesReadableOutput() {
        var harness = new LayoutTestHarness(testStage, trivialSchema(),
            trivialData(), Set.of("items", "name", "value"));
        LayoutTestResult r = harness.run(800, 400);

        String dump = r.dump();
        assertFalse(dump.isEmpty(), "dump() must produce output");
        assertTrue(dump.contains("width="), "dump must show width");
        assertTrue(dump.contains("TABLE") || dump.contains("OUTLINE"),
            "dump must show mode");
    }
}
