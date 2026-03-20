// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import com.chiralbehaviors.layout.test.LayoutFixtures.Fixture;

import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * Tests that table/outline mode is chosen correctly across widths.
 */
@ExtendWith(ApplicationExtension.class)
class LayoutModeSelectionTest {

    private Stage testStage;

    @Start
    void start(Stage stage) {
        this.testStage = stage;
        stage.setScene(new Scene(new Pane(), 1200, 600));
        stage.show();
    }

    @Test
    void nestedSchemaUsesOutlineAtNarrowWidth() {
        Fixture f = LayoutFixtures.nested();
        var harness = new LayoutTestHarness(testStage, f.schema(), f.data(), f.fieldNames());
        LayoutTestResult r = harness.run(400, 600);
        assertFalse(r.tableMode(),
            "Nested schema at 400px should use outline. " + r.dump());
    }

    @Test
    void nestedSchemaUsesTableAtWideWidth() {
        Fixture f = LayoutFixtures.nested();
        var harness = new LayoutTestHarness(testStage, f.schema(), f.data(), f.fieldNames());
        LayoutTestResult r = harness.run(1200, 600);
        assertTrue(r.tableMode(),
            "Nested schema at 1200px should use table. " + r.dump());
    }

    @Test
    void flatSchemaUsesTableAtReasonableWidth() {
        Fixture f = LayoutFixtures.flat();
        var harness = new LayoutTestHarness(testStage, f.schema(), f.data(), f.fieldNames());
        LayoutTestResult r = harness.run(400, 600);
        assertTrue(r.tableMode(),
            "Flat 3-column schema at 400px should use table. " + r.dump());
    }

    @Test
    void readableWidthExceedsRawTableWidth() {
        Fixture f = LayoutFixtures.nested();
        var harness = new LayoutTestHarness(testStage, f.schema(), f.data(), f.fieldNames());
        // Run at wide width to ensure table mode + populated layout
        harness.run(1200, 600);
        var layout = harness.getLayout();
        double readable = layout.readableTableWidth();
        double raw = layout.calculateTableColumnWidth();
        assertTrue(readable > raw,
            String.format("readableTableWidth (%.0f) must exceed raw (%.0f)", readable, raw));
    }

    /**
     * Monotonic transition: once table mode is chosen at width W,
     * it must remain table for all widths > W. No oscillation.
     */
    @Test
    void transitionIsMonotonic() {
        Fixture f = LayoutFixtures.nested();
        var harness = new LayoutTestHarness(testStage, f.schema(), f.data(), f.fieldNames());

        boolean seenTable = false;
        double transitionWidth = -1;
        for (double w = 300; w <= 1500; w += 50) {
            LayoutTestResult r = harness.run(w, 600);
            if (!seenTable && r.tableMode()) {
                seenTable = true;
                transitionWidth = w;
            }
            if (seenTable) {
                assertTrue(r.tableMode(),
                    String.format("Once table at %.0f, must stay table at %.0f", transitionWidth, w));
            }
        }
        assertTrue(seenTable, "Table mode must be chosen at some width <= 1500px");
    }

    /**
     * Threshold boundary: outline just below, table just above.
     */
    @Test
    void thresholdBoundary() {
        Fixture f = LayoutFixtures.nested();
        var harness = new LayoutTestHarness(testStage, f.schema(), f.data(), f.fieldNames());
        // Find the readable threshold
        harness.run(1200, 600);
        double threshold = harness.getLayout().readableTableWidth();

        LayoutTestResult below = harness.run(threshold * 0.8, 600);
        LayoutTestResult above = harness.run(threshold * 1.3, 600);
        assertFalse(below.tableMode(),
            String.format("At 80%% of threshold (%.0f), should be outline", threshold));
        assertTrue(above.tableMode(),
            String.format("At 130%% of threshold (%.0f), should be table", threshold));
    }
}
