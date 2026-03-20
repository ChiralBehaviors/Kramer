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
 * Tests that a SINGLE harness instance produces correct results across
 * multiple run() calls at different widths — simulating window resize.
 */
@ExtendWith(ApplicationExtension.class)
class LayoutResizeTest {

    private Stage testStage;

    @Start
    void start(Stage stage) {
        this.testStage = stage;
        stage.setScene(new Scene(new Pane(), 1200, 600));
        stage.show();
    }

    /**
     * Wide → narrow: data must survive the transition from table to outline.
     */
    @Test
    void resizeWideToNarrowPreservesData() {
        Fixture f = LayoutFixtures.nested();
        var harness = new LayoutTestHarness(testStage, f.schema(), f.data(), f.fieldNames());

        LayoutTestResult wide = harness.run(1200, 600);
        assertTrue(wide.tableMode(), "Should be table at 1200px");
        assertFalse(wide.getDataTexts().isEmpty(),
            "Wide: must have data.\n" + wide.dump());

        LayoutTestResult narrow = harness.run(400, 600);
        assertFalse(narrow.getDataTexts().isEmpty(),
            "Narrow: data must survive resize.\n" + narrow.dump());
    }

    /**
     * Narrow → wide: data must appear when transitioning to table mode.
     */
    @Test
    void resizeNarrowToWidePreservesData() {
        Fixture f = LayoutFixtures.nested();
        var harness = new LayoutTestHarness(testStage, f.schema(), f.data(), f.fieldNames());

        LayoutTestResult narrow = harness.run(400, 600);
        assertFalse(narrow.getDataTexts().isEmpty(),
            "Narrow: must have data.\n" + narrow.dump());

        LayoutTestResult wide = harness.run(1200, 600);
        assertTrue(wide.tableMode(), "Should be table at 1200px");
        assertFalse(wide.getDataTexts().isEmpty(),
            "Wide: data must survive resize.\n" + wide.dump());
    }

    /**
     * Multiple resizes: data persists through repeated transitions.
     */
    @Test
    void multipleResizeCycles() {
        Fixture f = LayoutFixtures.nested();
        var harness = new LayoutTestHarness(testStage, f.schema(), f.data(), f.fieldNames());

        double[] widths = {1200, 400, 800, 300, 1000, 500};
        for (double w : widths) {
            LayoutTestResult r = harness.run(w, 600);
            assertFalse(r.getDataTexts().isEmpty(),
                "Data must survive resize to " + w + "px.\n" + r.dump());
        }
    }

    /**
     * Resize with all fixtures — data survives for every schema shape.
     */
    @Test
    void resizeAllFixtures() {
        for (Fixture f : LayoutFixtures.all()) {
            var harness = new LayoutTestHarness(testStage, f.schema(),
                f.data(), f.fieldNames());

            LayoutTestResult wide = harness.run(1200, 600);
            assertFalse(wide.getDataTexts().isEmpty(),
                f.name() + " wide: no data.\n" + wide.dump());

            LayoutTestResult narrow = harness.run(400, 600);
            assertFalse(narrow.getDataTexts().isEmpty(),
                f.name() + " narrow after resize: no data.\n" + narrow.dump());
        }
    }
}
