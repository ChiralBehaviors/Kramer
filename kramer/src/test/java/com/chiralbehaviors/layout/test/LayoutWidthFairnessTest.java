// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.SchemaNodeLayout;
import com.chiralbehaviors.layout.test.LayoutFixtures.Fixture;

import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * Tests that table-mode width distribution is fair: no primitive starvation,
 * no single child dominates.
 */
@ExtendWith(ApplicationExtension.class)
class LayoutWidthFairnessTest {

    private Stage testStage;

    @Start
    void start(Stage stage) {
        this.testStage = stage;
        stage.setScene(new Scene(new Pane(), 1200, 600));
        stage.show();
    }

    /**
     * Every child must get at least its label width in table mode.
     */
    @Test
    void everyChildGetsAtLeastLabelWidth() {
        Fixture f = LayoutFixtures.nested();
        var harness = new LayoutTestHarness(testStage, f.schema(), f.data(), f.fieldNames());
        LayoutTestResult r = harness.run(1200, 600);

        assertTrue(r.tableMode(), "Should be table at 1200px");

        StringBuilder failures = new StringBuilder();
        for (SchemaNodeLayout child : harness.getLayout().getChildren()) {
            double justified = child.getJustifiedWidth();
            double label = child.getLabelWidth();
            if (justified < label) {
                failures.append(String.format("'%s' justified=%.1f < label=%.1f; ",
                    child.getField(), justified, label));
            }
        }
        assertTrue(failures.isEmpty(),
            "All children must get >= labelWidth: " + failures);
    }

    /**
     * No single child should take more than 80% of total justified width.
     */
    @Test
    void noChildDominatesWidth() {
        Fixture f = LayoutFixtures.nested();
        var harness = new LayoutTestHarness(testStage, f.schema(), f.data(), f.fieldNames());
        LayoutTestResult r = harness.run(1200, 600);

        assertTrue(r.tableMode(), "Should be table at 1200px");

        Map<String, Double> widths = r.fieldWidths();
        double total = widths.values().stream().mapToDouble(d -> d).sum();
        for (var entry : widths.entrySet()) {
            double ratio = entry.getValue() / total;
            assertTrue(ratio < 0.80,
                String.format("'%s' takes %.0f%% of width — max 80%%",
                    entry.getKey(), ratio * 100));
        }
    }

    /**
     * All children must have positive justified width in table mode.
     */
    @Test
    void allChildrenPositiveWidth() {
        Fixture f = LayoutFixtures.nested();
        var harness = new LayoutTestHarness(testStage, f.schema(), f.data(), f.fieldNames());
        LayoutTestResult r = harness.run(1200, 600);

        for (var entry : r.fieldWidths().entrySet()) {
            assertTrue(entry.getValue() > 0,
                "'" + entry.getKey() + "' must have positive width");
        }
    }

    /**
     * Width sweep in table mode: all 4 invariants checked at every width.
     * Absorbs the 3 resize sweep tests from LayoutTransitionTest.
     */
    @Test
    void widthSweepInvariants() {
        Fixture f = LayoutFixtures.nested();
        var harness = new LayoutTestHarness(testStage, f.schema(), f.data(), f.fieldNames());

        StringBuilder failures = new StringBuilder();
        for (double w = 400; w <= 1500; w += 50) {
            LayoutTestResult r = harness.run(w, 600);
            if (!r.tableMode()) continue; // only test table mode invariants

            RelationLayout layout = harness.getLayout();
            double total = 0;

            for (SchemaNodeLayout child : layout.getChildren()) {
                double justified = child.getJustifiedWidth();
                double label = child.getLabelWidth();

                if (justified <= 0) {
                    failures.append(String.format("w=%.0f '%s' justified=%.1f<=0; ",
                        w, child.getField(), justified));
                }
                if (justified < label - 0.5) {
                    failures.append(String.format("w=%.0f '%s' justified=%.1f<label=%.1f; ",
                        w, child.getField(), justified, label));
                }
                total += justified;
            }

            if (total > 0) {
                for (SchemaNodeLayout child : layout.getChildren()) {
                    if (child.getJustifiedWidth() / total > 0.80) {
                        failures.append(String.format("w=%.0f '%s' dominates at %.0f%%; ",
                            w, child.getField(), child.getJustifiedWidth() / total * 100));
                    }
                }
            }
        }

        assertTrue(failures.isEmpty(),
            "Width sweep invariants:\n" + failures.toString().replace("; ", ";\n"));
    }

    /**
     * Wide schema (12 columns): fair distribution under stress.
     */
    @Test
    void wideSchemaFairDistribution() {
        Fixture f = LayoutFixtures.wide();
        var harness = new LayoutTestHarness(testStage, f.schema(), f.data(), f.fieldNames());
        LayoutTestResult r = harness.run(1500, 600);

        if (!r.tableMode()) {
            // Outline mode at this width — still verify data is visible
            assertFalse(r.getDataTexts().isEmpty(),
                "Wide schema outline at 1500px must still show data.\n" + r.dump());
            return;
        }

        Map<String, Double> widths = r.fieldWidths();
        double total = widths.values().stream().mapToDouble(d -> d).sum();
        for (var entry : widths.entrySet()) {
            assertTrue(entry.getValue() > 0,
                "'" + entry.getKey() + "' must have positive width in wide schema");
            double ratio = entry.getValue() / total;
            assertTrue(ratio < 0.50,
                String.format("Wide schema: '%s' takes %.0f%% — max 50%% for 12 cols",
                    entry.getKey(), ratio * 100));
        }
    }
}
