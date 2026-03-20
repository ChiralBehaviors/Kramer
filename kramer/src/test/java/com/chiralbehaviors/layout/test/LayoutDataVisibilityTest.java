// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import com.chiralbehaviors.layout.test.LayoutFixtures.Fixture;
import com.chiralbehaviors.layout.test.LayoutTestResult.LabelEntry;

import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * Tests that data values are visible in the rendered scene graph —
 * not just field name headers.
 */
@ExtendWith(ApplicationExtension.class)
class LayoutDataVisibilityTest {

    private Stage testStage;

    @Start
    void start(Stage stage) {
        this.testStage = stage;
        stage.setScene(new Scene(new Pane(), 1200, 600));
        stage.show();
    }

    /**
     * At every fixture × width combination, data texts must be present.
     * This is the core falsifiable assertion: if the layout shows only
     * headers but no data, this test fails.
     */
    @Test
    void dataTextsPresent() {
        for (Fixture f : LayoutFixtures.all()) {
            var harness = new LayoutTestHarness(testStage, f.schema(),
                f.data(), f.fieldNames());
            for (double w : new double[] {400, 600, 800, 1000, 1200}) {
                LayoutTestResult r = harness.run(w, 600);
                assertFalse(r.getDataTexts().isEmpty(),
                    f.name() + " at " + w + "px: no data texts.\n" + r.dump());
            }
        }
    }

    /**
     * No data label should have zero rendered width.
     */
    @Test
    void noZeroWidthDataLabels() {
        for (Fixture f : LayoutFixtures.all()) {
            var harness = new LayoutTestHarness(testStage, f.schema(),
                f.data(), f.fieldNames());
            for (double w : new double[] {400, 800, 1200}) {
                LayoutTestResult r = harness.run(w, 600);
                List<LabelEntry> zeroWidth = r.getZeroWidthDataLabels();
                assertTrue(zeroWidth.isEmpty(),
                    f.name() + " at " + w + "px: zero-width data labels: "
                    + zeroWidth + "\n" + r.dump());
            }
        }
    }

    /**
     * Width sweep: data survives at every width from 400 to 1200 step 100.
     */
    @Test
    void widthSweepDataSurvives() {
        Fixture f = LayoutFixtures.nested();
        var harness = new LayoutTestHarness(testStage, f.schema(),
            f.data(), f.fieldNames());
        for (double w = 400; w <= 1200; w += 100) {
            LayoutTestResult r = harness.run(w, 600);
            assertFalse(r.getDataTexts().isEmpty(),
                "nested at " + w + "px: no data.\n" + r.dump());
        }
    }

    /**
     * Specific data values from the nested fixture must appear.
     */
    @Test
    void specificNestedDataValuesVisible() {
        Fixture f = LayoutFixtures.nested();
        var harness = new LayoutTestHarness(testStage, f.schema(),
            f.data(), f.fieldNames());

        for (double w : new double[] {500, 1000}) {
            LayoutTestResult r = harness.run(w, 600);
            // Substring matches: "Eva" in "Eva Johansson", "Backend" in "Backend Engineer"
            for (String value : new String[] {"Eva", "Frank", "Backend", "Frontend"}) {
                assertTrue(r.containsData(value),
                    "'" + value + "' missing at " + w + "px.\n" + r.dump());
            }
        }
    }
}
