// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;
import com.chiralbehaviors.layout.table.TableHeader;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.control.Label;
import javafx.scene.layout.Region;

/**
 * Read-only view of a relation's post-compute layout state.
 * Captures everything renderers ({@link com.chiralbehaviors.layout.table.NestedTable},
 * {@link com.chiralbehaviors.layout.outline.Outline}, etc.) need to build
 * the control tree.
 *
 * <p>{@link RelationLayout} implements this interface. The JS port (RDR-032)
 * will implement it from a portable render-context record, enabling pluggable
 * renderers without depending on the Java layout engine.
 */
public interface LayoutView {

    // --- Mode decisions ---
    boolean isUseTable();
    boolean isCrosstab();

    // --- Geometry ---
    double getJustifiedWidth();
    double getJustifiedTableColumnWidth();
    double getCellHeight();
    double getHeight();
    double getLabelWidth();
    double columnHeaderHeight();
    double baseRowCellHeight(double extended);

    // --- Structure ---
    int getResolvedCardinality();
    Collection<ColumnSet> getColumnSets();
    List<SchemaNodeLayout> getChildren();
    void forEach(Consumer<? super SchemaNodeLayout> action);

    // --- Schema identity ---
    Relation getNode();
    String getField();
    String getLabel();
    String getCssClass();
    SchemaPath getSchemaPath();

    // --- Style ---
    RelationStyle getStyle();

    // --- Data extraction ---
    JsonNode extractFrom(JsonNode datum);

    // --- Aggregate ---
    String getAggregatePosition();
    Map<String, Object> getAggregateResults();

    // --- Label ---
    double getLabelHeight();
    Label label(double width, double height);
    double labelWidth(String text);

    // --- Rendering ---
    TableHeader buildColumnHeader();
    LayoutCell<? extends Region> buildControl(FocusTraversal<?> parentTraversal,
                                               Style model);
}
