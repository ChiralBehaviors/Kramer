// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.query;

import com.chiralbehaviors.layout.SchemaPath;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Inspector panel showing all {@link FieldState} properties for a selected
 * field. Provides inline editing via buttons that dispatch
 * {@link LayoutInteraction} events through an {@link InteractionHandler}.
 *
 * @author hhildebrand
 */
public final class FieldInspectorPanel extends VBox {

    private final InteractionHandler handler;
    private final LayoutQueryState queryState;

    private SchemaPath currentPath;

    private final Label pathLabel = new Label();
    private final Label sortLabel = new Label();
    private final Label filterLabel = new Label();
    private final Label formulaLabel = new Label();
    private final Label aggregateLabel = new Label();
    private final Label visibleLabel = new Label();
    private final Label renderModeLabel = new Label();
    private final Label hideIfEmptyLabel = new Label();

    private final Button clearSortBtn = new Button("Clear");
    private final Button clearFilterBtn = new Button("Clear");
    private final Button clearFormulaBtn = new Button("Clear");
    private final Button clearAggregateBtn = new Button("Clear");
    private final Button toggleVisibleBtn = new Button("Toggle");
    private final Button resetBtn = new Button("Reset All");

    public FieldInspectorPanel(InteractionHandler handler,
                                LayoutQueryState queryState) {
        this.handler = handler;
        this.queryState = queryState;
        getStyleClass().add("field-inspector-panel");
        setPadding(new Insets(8));
        setSpacing(8);

        var title = new Label("Field Inspector");
        title.getStyleClass().add("inspector-title");

        var grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(4);

        int row = 0;
        grid.add(new Label("Path:"), 0, row);
        grid.add(pathLabel, 1, row);
        GridPane.setHgrow(pathLabel, Priority.ALWAYS);

        row++;
        grid.add(new Separator(), 0, row, 3, 1);

        row++;
        grid.add(new Label("Sort:"), 0, row);
        grid.add(sortLabel, 1, row);
        grid.add(clearSortBtn, 2, row);

        row++;
        grid.add(new Label("Filter:"), 0, row);
        grid.add(filterLabel, 1, row);
        grid.add(clearFilterBtn, 2, row);

        row++;
        grid.add(new Label("Formula:"), 0, row);
        grid.add(formulaLabel, 1, row);
        grid.add(clearFormulaBtn, 2, row);

        row++;
        grid.add(new Label("Aggregate:"), 0, row);
        grid.add(aggregateLabel, 1, row);
        grid.add(clearAggregateBtn, 2, row);

        row++;
        grid.add(new Separator(), 0, row, 3, 1);

        row++;
        grid.add(new Label("Visible:"), 0, row);
        grid.add(visibleLabel, 1, row);
        grid.add(toggleVisibleBtn, 2, row);

        row++;
        grid.add(new Label("Render:"), 0, row);
        grid.add(renderModeLabel, 1, row);

        row++;
        grid.add(new Label("Hide empty:"), 0, row);
        grid.add(hideIfEmptyLabel, 1, row);

        row++;
        grid.add(new Separator(), 0, row, 3, 1);

        row++;
        grid.add(resetBtn, 0, row, 3, 1);

        getChildren().addAll(title, grid);

        // Button actions
        clearSortBtn.setOnAction(e -> {
            if (currentPath != null) {
                handler.apply(new LayoutInteraction.ClearSort(currentPath));
                refresh();
            }
        });
        clearFilterBtn.setOnAction(e -> {
            if (currentPath != null) {
                handler.apply(new LayoutInteraction.ClearFilter(currentPath));
                refresh();
            }
        });
        clearFormulaBtn.setOnAction(e -> {
            if (currentPath != null) {
                handler.apply(new LayoutInteraction.ClearFormula(currentPath));
                refresh();
            }
        });
        clearAggregateBtn.setOnAction(e -> {
            if (currentPath != null) {
                handler.apply(new LayoutInteraction.ClearAggregate(currentPath));
                refresh();
            }
        });
        toggleVisibleBtn.setOnAction(e -> {
            if (currentPath != null) {
                handler.apply(new LayoutInteraction.ToggleVisible(currentPath));
                refresh();
            }
        });
        resetBtn.setOnAction(e -> {
            handler.apply(new LayoutInteraction.ResetAll());
            refresh();
        });

        inspect(null);
    }

    /**
     * Display properties for the given field path. Pass null to clear.
     */
    public void inspect(SchemaPath path) {
        this.currentPath = path;
        refresh();
    }

    /** Currently displayed path as string, or empty. */
    public String getDisplayedPath() {
        return pathLabel.getText();
    }

    /** Currently displayed sort state: "ascending", "descending", or "none". */
    public String getDisplayedSort() {
        return sortLabel.getText();
    }

    /** Currently displayed filter expression, or empty. */
    public String getDisplayedFilter() {
        return filterLabel.getText();
    }

    /** Clear sort on the currently inspected field. */
    public void clearSort() {
        if (currentPath != null) {
            handler.apply(new LayoutInteraction.ClearSort(currentPath));
            refresh();
        }
    }

    /** Clear filter on the currently inspected field. */
    public void clearFilter() {
        if (currentPath != null) {
            handler.apply(new LayoutInteraction.ClearFilter(currentPath));
            refresh();
        }
    }

    private void refresh() {
        if (currentPath == null) {
            pathLabel.setText("");
            sortLabel.setText("");
            filterLabel.setText("");
            formulaLabel.setText("");
            aggregateLabel.setText("");
            visibleLabel.setText("");
            renderModeLabel.setText("");
            hideIfEmptyLabel.setText("");
            setDisableButtons(true);
            return;
        }

        FieldState fs = queryState.getFieldState(currentPath);
        pathLabel.setText(currentPath.toString());

        // Sort
        String sortFields = fs.sortFields();
        if (sortFields == null || sortFields.isEmpty()) {
            sortLabel.setText("none");
        } else if (sortFields.startsWith("-")) {
            sortLabel.setText("descending");
        } else {
            sortLabel.setText("ascending");
        }

        filterLabel.setText(fs.filterExpression() != null ? fs.filterExpression() : "");
        formulaLabel.setText(fs.formulaExpression() != null ? fs.formulaExpression() : "");
        aggregateLabel.setText(fs.aggregateExpression() != null ? fs.aggregateExpression() : "");
        visibleLabel.setText(queryState.getVisibleOrDefault(currentPath) ? "yes" : "no");
        renderModeLabel.setText(fs.renderMode() != null ? fs.renderMode() : "auto");
        hideIfEmptyLabel.setText(Boolean.TRUE.equals(fs.hideIfEmpty()) ? "yes" : "no");

        setDisableButtons(false);
        clearSortBtn.setDisable(sortFields == null || sortFields.isEmpty());
        clearFilterBtn.setDisable(fs.filterExpression() == null);
        clearFormulaBtn.setDisable(fs.formulaExpression() == null);
        clearAggregateBtn.setDisable(fs.aggregateExpression() == null);
    }

    private void setDisableButtons(boolean disabled) {
        clearSortBtn.setDisable(disabled);
        clearFilterBtn.setDisable(disabled);
        clearFormulaBtn.setDisable(disabled);
        clearAggregateBtn.setDisable(disabled);
        toggleVisibleBtn.setDisable(disabled);
        resetBtn.setDisable(disabled);
    }
}
