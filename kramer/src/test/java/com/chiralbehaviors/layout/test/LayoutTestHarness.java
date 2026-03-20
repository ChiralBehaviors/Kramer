// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.testfx.util.WaitForAsyncUtils;

import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.SchemaNodeLayout;
import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.control.FocusController;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.Style;
import com.chiralbehaviors.layout.test.LayoutTestResult.LabelEntry;
import com.fasterxml.jackson.databind.node.ArrayNode;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/**
 * Reusable test harness that runs the full layout pipeline synchronously
 * and captures the rendered scene graph as a {@link LayoutTestResult}.
 * <p>
 * Pipeline: measure → autoLayout (layout + compress + buildControl) → updateItem.
 * Uses {@link SchemaNodeLayout#autoLayout(double, double, com.chiralbehaviors.layout.cell.control.FocusTraversal, Style)}
 * which is fully synchronous — no Platform.runLater chains.
 * <p>
 * The harness can be called multiple times with different widths on the
 * same schema+data to simulate resize.
 *
 * <h3>Usage</h3>
 * <pre>
 * var harness = new LayoutTestHarness(stage, schema, data, fieldNames);
 * LayoutTestResult r = harness.run(800, 600);
 * assertTrue(r.containsData("Frank"));
 * </pre>
 */
public final class LayoutTestHarness {

    private final Stage stage;
    private final Relation schema;
    private final ArrayNode data;
    private final Set<String> fieldNames;
    private final Style model;
    private final RelationLayout layout;
    private LayoutCell<? extends Region> currentControl;

    /**
     * @param stage      a visible TestFX Stage (for scene attachment)
     * @param schema     the schema to lay out
     * @param data       the data to render
     * @param fieldNames all field names in the schema (for header filtering)
     */
    public LayoutTestHarness(Stage stage, Relation schema, ArrayNode data,
                              Set<String> fieldNames) {
        this.stage = stage;
        this.schema = schema;
        this.data = data;
        this.fieldNames = fieldNames;

        // Style + RelationLayout + measure must all run on FXAT
        var modelRef = new AtomicReference<Style>();
        var layoutRef = new AtomicReference<RelationLayout>();
        Platform.runLater(() -> {
            Style m = new Style();
            RelationLayout l = new RelationLayout(schema, m.style(schema));
            l.buildPaths(new SchemaPath(schema.getField()), m);
            l.measure(data, n -> n, m);
            modelRef.set(m);
            layoutRef.set(l);
        });
        WaitForAsyncUtils.waitForFxEvents();
        this.model = modelRef.get();
        this.layout = layoutRef.get();
    }

    /**
     * Run the full pipeline at the given width and return a snapshot of
     * the rendered scene graph.
     * <p>
     * Can be called multiple times to simulate resize.
     */
    public LayoutTestResult run(double width, double height) {
        var ref = new AtomicReference<LayoutTestResult>();

        Platform.runLater(() -> {
            // Focus controller on dummy pane — needed by VirtualFlow
            Pane focusRoot = new Pane();
            var controller = new FocusController<>(focusRoot);

            // Synchronous full pipeline
            LayoutCell<? extends Region> control =
                layout.autoLayout(width, height, controller, model);

            // Snapshot mode BEFORE attaching to scene
            boolean tableMode = layout.isUseTable();

            // Snapshot field widths
            Map<String, Double> fieldWidths = new LinkedHashMap<>();
            for (SchemaNodeLayout child : layout.getChildren()) {
                fieldWidths.put(child.getField(), child.getJustifiedWidth());
            }

            // Dispose previous control to avoid leaked timelines/listeners
            if (currentControl != null) {
                currentControl.dispose();
            }
            currentControl = control;

            // Attach to scene for VirtualFlow cell materialization
            Region node = control.getNode();
            node.setMinSize(width, height);
            node.setPrefSize(width, height);
            node.setMaxSize(width, height);
            Pane root = (Pane) stage.getScene().getRoot();
            root.getChildren().setAll(node);

            // Populate data
            control.updateItem(data);

            // Force CSS + layout pass
            root.applyCss();
            root.layout();

            // Collect labels from rendered scene graph
            List<LabelEntry> labels = new ArrayList<>();
            collectLabels(node, labels);

            ref.set(new LayoutTestResult(width, tableMode, labels,
                                          fieldWidths, fieldNames));
        });

        WaitForAsyncUtils.waitForFxEvents();
        return ref.get();
    }

    /** Access the underlying RelationLayout for direct inspection. */
    public RelationLayout getLayout() {
        return layout;
    }

    private static void collectLabels(Node node, List<LabelEntry> out) {
        if (node instanceof Labeled labeled) {
            String text = labeled.getText();
            if (text != null && !text.isBlank()) {
                out.add(new LabelEntry(text, labeled.getWidth()));
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectLabels(child, out);
            }
        }
    }
}
