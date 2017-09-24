/**
 * Copyright (c) 2016 Chiral Behaviors, LLC, all rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chiralbehaviors.layout.schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import com.chiralbehaviors.layout.Layout;
import com.chiralbehaviors.layout.NestedTable;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Pair;

/**
 * @author hhildebrand
 *
 */
public class Relation extends SchemaNode {
    private boolean                autoFold           = true;
    private int                    averageCardinality = 1;
    private final List<SchemaNode> children           = new ArrayList<>();
    private final List<ColumnSet>  columnSets         = new ArrayList<>();
    private Relation               fold;
    private double                 outlineWidth       = 0;
    private double                 rowHeight;
    private boolean                singular           = false;
    private double                 tableColumnWidth   = 0;
    private boolean                useTable           = false;

    public Relation(String label) {
        super(label);
    }

    public void addChild(SchemaNode child) {
        children.add(child);
    }

    public void autoLayout(int cardinality, Layout layout, double width) {
        double snapped = Layout.snap(width);
        layout(cardinality, layout, snapped);
        compress(layout, snapped);
        cellHeight(cardinality, layout, width);
    }

    public Control buildControl(int cardinality, Layout layout, double width) {
        if (isFold()) {
            return fold.buildControl(averageCardinality * cardinality, layout,
                                     width);
        }
        return useTable ? buildNestedTable(n -> n, cardinality, layout, width)
                        : buildOutline(n -> n, cardinality, layout);
    }

    @Override
    public JsonNode extractFrom(JsonNode jsonNode) {
        if (isFold()) {
            return fold.extractFrom(super.extractFrom(jsonNode));
        }
        return super.extractFrom(jsonNode);
    }

    public int getAverageCardinality() {
        return isFold() ? averageCardinality * fold.getAverageCardinality()
                        : averageCardinality;
    }

    public SchemaNode getChild(String field) {
        for (SchemaNode child : children) {
            if (child.getField()
                     .equals(field)) {
                return child;
            }
        }
        return null;
    }

    public List<SchemaNode> getChildren() {
        return isFold() ? fold.getChildren() : children;
    }

    @Override
    public double getLabelWidth(Layout layout) {
        if (isFold()) {
            return fold.getLabelWidth(layout);
        }
        return layout.textWidth(label);
    }

    public double getRowHeight() {
        return isFold() ? fold.getRowHeight() : rowHeight;
    }

    @JsonProperty
    public boolean isFold() {
        return fold != null;
    }

    @Override
    public boolean isRelation() {
        return true;
    }

    @Override
    public boolean isUseTable() {
        if (isFold()) {
            return fold.isUseTable();
        }
        return useTable;
    }

    public void measure(JsonNode jsonNode, Layout layout) {
        measure(jsonNode, !jsonNode.isArray(), layout, INDENT.NONE);
    }

    public void setAverageCardinality(int averageCardinality) {
        this.averageCardinality = averageCardinality;
    }

    public void setFold(boolean fold) {
        this.fold = (fold && children.size() == 1 && children.get(0)
                                                             .isRelation()) ? (Relation) children.get(0)
                                                                            : null;
    }

    @Override
    public void setItems(Control control, JsonNode data, Layout layout) {
        if (data == null) {
            data = JsonNodeFactory.instance.arrayNode();
        }
        if (isFold()) {
            fold.setItems(control, flatten(data), layout);
        } else {
            super.setItems(control, data, layout);
        }
    }

    public void setUseTable(boolean useTable) {
        this.useTable = useTable;
    }

    @Override
    public String toString() {
        return toString(0);
    }

    @Override
    public String toString(int indent) {
        StringBuffer buf = new StringBuffer();
        buf.append(String.format("Relation [%s:%.2f:%.2f:%.2f x %s]", label,
                                 tableColumnWidth, outlineWidth, justifiedWidth,
                                 averageCardinality));
        buf.append('\n');
        children.forEach(c -> {
            for (int i = 0; i < indent; i++) {
                buf.append("    ");
            }
            buf.append("  - ");
            buf.append(c.toString(indent + 1));
            buf.append('\n');
        });
        return buf.toString();
    }

    @Override
    void adjustHeight(double delta) {
        if (isFold()) {
            fold.adjustHeight(delta);
            return;
        }
        super.adjustHeight(delta);
        if (useTable) {
            double subDelta = delta / children.size();
            if (delta >= 1.0) {
                children.forEach(f -> f.adjustHeight(subDelta));
            }
            return;
        }
        double subDelta = delta / columnSets.size();
        if (subDelta >= 1.0) {
            columnSets.forEach(c -> c.adjustHeight(subDelta));
        }
    }

    @Override
    Function<Double, Pair<Consumer<JsonNode>, Control>> buildColumn(int cardinality,
                                                                    Function<JsonNode, JsonNode> extractor,
                                                                    Map<SchemaNode, TableColumn<JsonNode, ?>> columnMap,
                                                                    Layout layout,
                                                                    double inset,
                                                                    INDENT indent) {
        if (isFold()) {
            return fold.buildColumn(averageCardinality * cardinality,
                                    extract(extractor), columnMap, layout,
                                    inset, indent);
        }

        List<Function<Double, Pair<Consumer<JsonNode>, Control>>> fields = new ArrayList<>();
        children.forEach(child -> fields.add(child.buildColumn(averageCardinality,
                                                               n -> n,
                                                               columnMap,
                                                               layout,
                                                               inset(layout,
                                                                     inset,
                                                                     child,
                                                                     indent),
                                                               indent(child))));
        double calculatedHeight = (rowHeight * cardinality)
                                  + layout.getListVerticalInset();

        Function<JsonNode, JsonNode> extract = extractor == null ? n -> n
                                                                 : extract(extractor);
        TableColumn<JsonNode, ?> column = columnMap.get(this);
        return rendered -> {
            double deficit = Math.max(0, rendered - calculatedHeight);
            double childDeficit = Layout.snap(Math.max(0,
                                                       deficit / cardinality));
            double extended = Layout.snap(rowHeight + childDeficit);

            ListView<JsonNode> row = new ListView<JsonNode>();
            layout.getModel()
                  .apply(row, this);
            row.setFixedCellSize(extended);
            HBox.setHgrow(row, Priority.ALWAYS);
            row.setPrefHeight(rendered);
            if (column != null) {
                double width = Layout.snap(column.getColumns()
                                                 .stream()
                                                 .mapToDouble(c -> Layout.snap(c.getWidth()))
                                                 .sum()
                                           - inset);
                row.setPrefWidth(width);
            }
            row.setCellFactory(control -> {
                ListCell<JsonNode> cell = rowCell(fields,
                                                  Layout.snap(extended
                                                              - layout.getListCellVerticalInset()),
                                                  layout, row);
                cell.setPrefHeight(extended);
                cell.setPrefWidth(row.getWidth()
                                  - layout.getListCellHorizontalInset()
                                  - layout.getListHorizontalInset());
                layout.getModel()
                      .apply(cell, Relation.this);
                return cell;
            });
            return new Pair<>(node -> {
                setItems(row, extract.apply(node), layout);
            }, row);
        };
    }

    @Override
    TableColumn<JsonNode, JsonNode> buildColumn(Layout layout, double inset,
                                                INDENT indent) {
        if (isFold()) {
            return fold.buildColumn(layout, inset, indent);
        }
        TableColumn<JsonNode, JsonNode> column = super.buildColumn(layout,
                                                                   inset,
                                                                   indent);
        column.setPrefWidth(justifiedWidth);
        ObservableList<TableColumn<JsonNode, ?>> columns = column.getColumns();
        children.forEach(child -> columns.add(child.buildColumn(layout,
                                                                inset(layout,
                                                                      inset,
                                                                      child,
                                                                      indent),
                                                                indent(child))));
        return column;
    }

    @Override
    double cellHeight(int card, Layout layout, double width) {
        if (isFold()) {
            return fold.cellHeight(averageCardinality * card, layout, width);
        }
        if (height != null) {
            return height;
        }

        int cardinality = singular ? 1 : card;
        if (!useTable) {
            height = Layout.snap((cardinality * (columnSets.stream()
                                                           .mapToDouble(cs -> cs.getCellHeight())
                                                           .sum()
                                                 + layout.getListCellVerticalInset()))
                                 + layout.getListVerticalInset());
            return height;
        }
        rowHeight = Layout.snap(elementHeight(layout)
                                + layout.getListCellVerticalInset());
        double calculatedHeight = (rowHeight * cardinality);
        height = calculatedHeight + layout.getListCellVerticalInset();
        return height;
    }

    @Override
    void compress(Layout layout, double justified) {
        if (isFold()) {
            fold.compress(layout, justified);
            return;
        }
        if (useTable) {
            justify(justified - layout.getTableRowHorizontalInset()
                    - layout.getTableHorizontalInset(), layout);
            return;
        }
        justifiedWidth = justified - layout.getNestedInset();
        double labelWidth = Layout.snap(children.stream()
                                                .mapToDouble(n -> n.getLabelWidth(layout))
                                                .max()
                                                .orElse(0));
        columnSets.clear();
        ColumnSet current = null;
        double halfWidth = justifiedWidth / 2d;
        for (SchemaNode child : children) {
            double childWidth = labelWidth + child.layoutWidth(layout);
            if (childWidth > halfWidth || current == null) {
                current = new ColumnSet(labelWidth);
                columnSets.add(current);
                current.add(child);
                if (childWidth > halfWidth) {
                    current = null;
                }
            } else {
                current.add(child);
            }
        }
        columnSets.forEach(cs -> cs.compress(averageCardinality, layout,
                                             justifiedWidth));
    }

    @Override
    Double getCalculatedHeight() {
        if (isFold()) {
            return fold.getCalculatedHeight();
        }
        return super.getCalculatedHeight();
    }

    // for testing
    List<ColumnSet> getColumnSets() {
        return columnSets;
    }

    @Override
    void justify(double width, Layout layout) {
        if (isFold()) {
            fold.justify(width, layout);
            return;
        }
        assert useTable : "Not a nested table";
        justifiedWidth = Layout.snap(width) - layout.getNestedInset();
        double slack = Layout.snap(Math.max(0,
                                            justifiedWidth - tableColumnWidth));
        double total = Layout.snap(children.stream()
                                           .map(child -> rawWidth(layout))
                                           .reduce((a, b) -> a + b)
                                           .orElse(0.0d));
        children.forEach(child -> {
            double childWidth = child.tableColumnWidth(layout);
            child.justify((slack * (childWidth / total)) + childWidth, layout);
        });
    }

    @Override
    double layout(int cardinality, Layout layout, double width) {
        height = null;
        useTable = false;
        rowHeight = 0;
        if (isFold()) {
            return fold.layout(cardinality * averageCardinality, layout, width);
        }
        double labelWidth = Layout.snap(children.stream()
                                                .mapToDouble(child -> child.getLabelWidth(layout))
                                                .max()
                                                .getAsDouble());
        double available = Layout.snap(width - labelWidth
                                       - layout.getNestedInset());
        outlineWidth = Layout.snap(children.stream()
                                           .mapToDouble(child -> {
                                               return child.layout(cardinality,
                                                                   layout,
                                                                   available);
                                           })
                                           .max()
                                           .orElse(0d)
                                   + labelWidth);
        double tableWidth = tableColumnWidth(layout)
                            + layout.getTableHorizontalInset()
                            + layout.getTableRowHorizontalInset();
        double oWidth = outlineWidth + layout.getNestedInset();
        if (tableWidth <= oWidth) {
            nestTable();
            return tableWidth;
        }
        return oWidth;
    }

    /* (non-Javadoc)
     * @see com.chiralbehaviors.layout.schema.SchemaNode#layoutWidth(com.chiralbehaviors.layout.Layout)
     */
    @Override
    double layoutWidth(Layout layout) {
        return useTable ? justifiedWidth + layout.getListCellHorizontalInset()
                          + layout.getListHorizontalInset()
                          + layout.getTableRowHorizontalInset()
                          + layout.getTableHorizontalInset()
                        : outlineWidth + layout.getListCellHorizontalInset()
                          + layout.getListHorizontalInset();
    }

    @Override
    double measure(JsonNode data, boolean isSingular, Layout layout,
                   INDENT indent) {
        if (isAutoFoldable()) {
            fold = ((Relation) children.get(children.size() - 1));
        }
        if (data.isNull() || children.size() == 0) {
            return 0;
        }

        singular = isSingular;
        double labelWidth = layout.textWidth(label);
        labelWidth += layout.getTextHorizontalInset();
        double sum = 0;
        tableColumnWidth = 0;
        int singularChildren = 0;
        for (SchemaNode child : children) {
            ArrayNode aggregate = JsonNodeFactory.instance.arrayNode();
            int cardSum = 0;
            boolean childSingular = false;
            if (singular) {

            }
            List<JsonNode> datas = data.isArray() ? new ArrayList<>(data.size())
                                                  : Arrays.asList(data);
            if (data.isArray()) {
                data.forEach(n -> datas.add(n));
            }
            for (JsonNode node : datas) {
                JsonNode sub = node.get(child.field);
                if (sub instanceof ArrayNode) {
                    childSingular = false;
                    aggregate.addAll((ArrayNode) sub);
                    cardSum += sub.size();
                } else {
                    childSingular = true;
                    aggregate.add(sub);
                }
            }
            if (childSingular) {
                singularChildren += 1;
            } else {
                sum += datas.size() == 0 ? 1
                                         : Math.round(cardSum / datas.size());
            }
            tableColumnWidth += child.measure(aggregate, childSingular, layout,
                                              indent(child));
        }
        int effectiveChildren = children.size() - singularChildren;
        averageCardinality = Math.max(1,
                                      Math.min(4,
                                               effectiveChildren == 0 ? 1
                                                                      : (int) Math.ceil(sum
                                                                                        / effectiveChildren)));
        tableColumnWidth = Layout.snap(Math.max(labelWidth, tableColumnWidth))
                           + layout.getNestedInset();
        justifiedWidth = tableColumnWidth;
        return isFold() ? fold.tableColumnWidth : tableColumnWidth;
    }

    @Override
    Pair<Consumer<JsonNode>, Parent> outlineElement(int cardinality,
                                                    double labelWidth,
                                                    Function<JsonNode, JsonNode> extractor,
                                                    Layout layout,
                                                    double justified) {
        if (isFold()) {
            return fold.outlineElement(averageCardinality * cardinality,
                                       labelWidth, extract(extractor), layout,
                                       justified);
        }
        double available = justified - labelWidth;

        Control control = useTable ? buildNestedTable(n -> n, cardinality,
                                                      layout, available)
                                   : buildOutline(n -> n, cardinality, layout);

        Label labelText = label(labelWidth);
        control.setPrefWidth(available);
        control.setPrefHeight(height);

        Pane box = new HBox();
        box.setPrefWidth(justified);
        box.setPrefHeight(height);
        box.getChildren()
           .add(labelText);
        box.getChildren()
           .add(control);

        return new Pair<>(item -> {
            if (item == null) {
                return;
            }
            JsonNode extracted = extractor.apply(item);
            JsonNode extractedField = extracted == null ? null
                                                        : extracted.get(field);
            setItems(control, extractedField, layout);
        }, box);
    }

    @Override
    double outlineWidth(Layout layout) {
        return outlineWidth + layout.getListCellHorizontalInset()
               + layout.getListHorizontalInset();
    }

    double rawWidth(Layout layout) {
        return useTable ? justifiedWidth + layout.getListCellHorizontalInset()
                          + layout.getListHorizontalInset()
                          + layout.getTableRowHorizontalInset()
                          + layout.getTableHorizontalInset()
                        : outlineWidth + layout.getListCellHorizontalInset()
                          + layout.getListHorizontalInset();
    }

    @Override
    double rowHeight(int cardinality, Layout layout, double justified) {
        if (isFold()) {
            return fold.rowHeight(cardinality * averageCardinality, layout,
                                  justifiedWidth);
        }

        return (cardinality
                * (elementHeight(layout) + layout.getListCellVerticalInset()))
               + layout.getListVerticalInset();
    }

    @Override
    double tableColumnWidth(Layout layout) {
        if (isFold()) {
            return fold.tableColumnWidth(layout);
        }
        return tableColumnWidth + layout.getNestedInset();
    }

    private Control buildNestedTable(Function<JsonNode, JsonNode> extractor,
                                     int cardinality, Layout layout,
                                     double justified) {
        if (isFold()) {
            return fold.buildNestedTable(extract(extractor),
                                         averageCardinality * cardinality,
                                         layout, justified);
        }
        return new NestedTable(cardinality, this, layout);
    }

    private ListView<JsonNode> buildOutline(Function<JsonNode, JsonNode> extractor,
                                            int cardinality, Layout layout) {
        if (isFold()) {
            return fold.buildOutline(extract(extractor),
                                     averageCardinality * cardinality, layout);
        }

        double cellHeight = columnSets.stream()
                                      .mapToDouble(cs -> cs.getCellHeight())
                                      .sum()
                            + layout.getListCellVerticalInset();
        ListView<JsonNode> list = new ListView<>();
        layout.getModel()
              .apply(list, this);
        list.setPrefHeight(height);
        list.setFixedCellSize(cellHeight);
        list.setCellFactory(c -> {
            ListCell<JsonNode> cell = listCell(extractor,
                                               cellHeight - layout.getListCellVerticalInset(),
                                               layout);
            layout.getModel()
                  .apply(cell, this);
            return cell;
        });
        list.setPlaceholder(new Text());
        return list;
    }

    private double elementHeight(Layout layout) {
        return children.stream()
                       .mapToDouble(child -> Layout.snap(child.rowHeight(averageCardinality,
                                                                         layout,
                                                                         justifiedWidth)))
                       .max()
                       .getAsDouble();
    }

    private ArrayNode flatten(JsonNode data) {
        ArrayNode flattened = JsonNodeFactory.instance.arrayNode();
        if (data != null) {
            if (data.isArray()) {
                data.forEach(item -> {
                    flattened.addAll(SchemaNode.asArray(item.get(fold.getField())));
                });
            } else {
                flattened.addAll(SchemaNode.asArray(data.get(fold.getField())));
            }
        }
        return flattened;
    }

    private INDENT indent(SchemaNode child) {
        INDENT indent = INDENT.NONE;
        if (children.size() == 1) {
            return indent;
        }
        if (child == children.get(0)) {
            indent = INDENT.LEFT;
        } else if (child == children.get(children.size() - 1)) {
            indent = INDENT.RIGHT;
        }
        return indent;
    }

    private double inset(Layout layout, double inset, SchemaNode child,
                         INDENT indent) {
        if (children.size() == 1) {
            return inset + layout.getNestedInset();
        }
        switch (indent) {
            case RIGHT:
                if (child == children.get(children.size() - 1)) {
                    return inset + layout.getNestedRightInset();
                } else if (child == children.get(0)) {
                    return layout.getNestedLeftInset();
                }
                break;
            case LEFT:
                if (child == children.get(0)) {
                    return inset + layout.getNestedLeftInset();
                } else if (child == children.get(children.size() - 1)) {
                    return layout.getNestedRightInset();
                }
                break;
            case NONE:
                if (child == children.get(children.size() - 1)) {
                    return layout.getNestedRightInset();
                } else if (child == children.get(0)) {
                    return layout.getNestedLeftInset();
                }
                break;
            default:
        }
        return 0;
    }

    private boolean isAutoFoldable() {
        return fold == null && autoFold && children.size() == 1
               && children.get(children.size() - 1) instanceof Relation;
    }

    private ListCell<JsonNode> listCell(Function<JsonNode, JsonNode> extractor,
                                        double cellHeight, Layout layout) {
        return new ListCell<JsonNode>() {
            VBox                     cell;
            List<Consumer<JsonNode>> controls = new ArrayList<>();
            {
                itemProperty().addListener((obs, oldItem, newItem) -> {
                    if (newItem != null) {
                        if (cell == null) {
                            initialize(extractor, layout);
                        }
                        setGraphic(cell);
                    }
                });
                emptyProperty().addListener((obs, wasEmpty, isEmpty) -> {
                    if (isEmpty) {
                        setGraphic(null);
                    } else {
                        setGraphic(cell);
                    }
                });
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(JsonNode item, boolean empty) {
                if (item == getItem()) {
                    return;
                }
                super.updateItem(item, empty);
                super.setText(null);
                if (empty) {
                    super.setGraphic(null);
                    return;
                }
                controls.forEach(child -> child.accept(item));
            }

            private void initialize(Function<JsonNode, JsonNode> extractor,
                                    Layout layout) {
                cell = new VBox();
                cell.setMinHeight(cellHeight);
                cell.setPrefHeight(cellHeight);
                cell.setMinWidth(0);
                cell.setPrefWidth(1);
                columnSets.forEach(cs -> {
                    Pair<Consumer<JsonNode>, Parent> master = cs.build(averageCardinality,
                                                                       extractor,
                                                                       layout);
                    controls.add(master.getKey());
                    Parent control = master.getValue();
                    VBox.setVgrow(control, Priority.ALWAYS);
                    cell.getChildren()
                        .add(control);
                });
            }
        };
    }

    private void nestTable() {
        useTable = true;
        children.forEach(child -> {
            if (child.isRelation()) {
                ((Relation) child).nestTable();
            }
        });
    }

    private ListCell<JsonNode> rowCell(List<Function<Double, Pair<Consumer<JsonNode>, Control>>> fields,
                                       double resolvedHeight, Layout layout,
                                       ListView<JsonNode> row) {

        return new ListCell<JsonNode>() {
            private final HBox         cell;
            private Consumer<JsonNode> master;
            {
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setAlignment(Pos.CENTER_LEFT);
                cell = buildCell();
            }

            @Override
            protected void updateItem(JsonNode item, boolean empty) {
                if (item == getItem()) {
                    return;
                }
                super.updateItem(item, empty);
                if (empty) {
                    return;
                }
                if (item == null) {
                    setGraphic(null);
                    return;
                }
                setGraphic(cell);
                master.accept(item);
                cell.requestLayout();
            }

            private HBox buildCell() {
                HBox r = new HBox();
                r.setMinWidth(0);
                r.setPrefWidth(1);
                r.setPrefHeight(resolvedHeight);
                List<Consumer<JsonNode>> consumers = new ArrayList<>();
                fields.forEach(p -> {
                    Pair<Consumer<JsonNode>, Control> pair = p.apply(resolvedHeight);
                    Control control = pair.getValue();
                    control.setPrefHeight(resolvedHeight);
                    r.getChildren()
                     .add(control);
                    consumers.add(pair.getKey());
                });
                master = node -> consumers.forEach(c -> {
                    c.accept(node);
                });
                return r;
            }
        };
    }
}
