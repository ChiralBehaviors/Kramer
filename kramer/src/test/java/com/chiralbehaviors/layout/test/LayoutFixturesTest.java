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
 * Verifies that each fixture produces valid schema, non-empty data,
 * and that the harness can run with each fixture.
 */
@ExtendWith(ApplicationExtension.class)
class LayoutFixturesTest {

    private Stage testStage;

    @Start
    void start(Stage stage) {
        this.testStage = stage;
        stage.setScene(new Scene(new Pane(), 1200, 600));
        stage.show();
    }

    @Test
    void flatFixtureValid() {
        assertFixtureValid(LayoutFixtures.flat());
    }

    @Test
    void nestedFixtureValid() {
        assertFixtureValid(LayoutFixtures.nested());
    }

    @Test
    void deepFixtureValid() {
        assertFixtureValid(LayoutFixtures.deep());
    }

    @Test
    void wideFixtureValid() {
        assertFixtureValid(LayoutFixtures.wide());
    }

    @Test
    void allFixturesRunThroughHarness() {
        for (Fixture f : LayoutFixtures.all()) {
            var harness = new LayoutTestHarness(testStage, f.schema(),
                f.data(), f.fieldNames());
            LayoutTestResult r = harness.run(800, 400);
            assertNotNull(r, f.name() + ": run() returned null");
            assertFalse(r.getRenderedTexts().isEmpty(),
                f.name() + ": no rendered texts at 800px. Dump:\n" + r.dump());
            assertFalse(r.getDataTexts().isEmpty(),
                f.name() + ": no data texts (only headers?). Dump:\n" + r.dump());
        }
    }

    @Test
    void allFixturesProduceDataAtNarrowWidth() {
        for (Fixture f : LayoutFixtures.all()) {
            var harness = new LayoutTestHarness(testStage, f.schema(),
                f.data(), f.fieldNames());
            LayoutTestResult r = harness.run(400, 400);
            assertNotNull(r, f.name() + ": null at 400px");
            assertFalse(r.getRenderedTexts().isEmpty(),
                f.name() + ": no labels at 400px. Dump:\n" + r.dump());
        }
    }

    private void assertFixtureValid(Fixture f) {
        assertNotNull(f.schema(), f.name() + ": schema is null");
        assertNotNull(f.data(), f.name() + ": data is null");
        assertTrue(f.data().size() > 0, f.name() + ": data is empty");
        assertNotNull(f.fieldNames(), f.name() + ": fieldNames is null");
        assertFalse(f.fieldNames().isEmpty(), f.name() + ": fieldNames empty");
        // Schema root field name must be in fieldNames
        assertTrue(f.fieldNames().contains(f.schema().getField()),
            f.name() + ": fieldNames missing root '" + f.schema().getField() + "'");
    }
}
