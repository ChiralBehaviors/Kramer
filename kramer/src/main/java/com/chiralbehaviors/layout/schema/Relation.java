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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import com.chiralbehaviors.layout.Layout;
import com.chiralbehaviors.layout.RelationTableRow;
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
import javafx.scene.control.TableView;
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
        return averageCardinality;
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
        return children;
    }

    @Override
    public double getLabelWidth(Layout layout) {
        if (isFold()) {
            return fold.getLabelWidth(layout);
        }
        return layout.textWidth(label);
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
        if (useTable) {
            return;
        }
        super.adjustHeight(delta);
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
        double elementHeight = elementHeight(layout);
        double cellHeight = elementHeight + layout.getListCellVerticalInset();
        double calculatedHeight = (cellHeight * cardinality)
                                  + layout.getListVerticalInset();

        Function<JsonNode, JsonNode> extract = extractor == null ? n -> n
                                                                 : extract(extractor);
        TableColumn<JsonNode, ?> column = columnMap.get(this);
        return rendered -> {
            double deficit = Math.max(0, rendered - calculatedHeight);
            assert deficit >= 0 : String.format("negative deficit %s", deficit);
            double childDeficit = Math.max(0, deficit / cardinality);
            double extended = Layout.snap(cellHeight + childDeficit);

            ListView<JsonNode> row = new ListView<JsonNode>();
            layout.getModel()
                  .apply(row, this);
            row.setFixedCellSize(extended);
            row.setPrefHeight(rendered);
            HBox.setHgrow(row, Priority.ALWAYS);
            if (column != null) {
                row.setMinWidth(column.getColumns()
                                      .stream()
                                      .mapToDouble(c -> Layout.snap(c.getWidth()))
                                      .sum());
            }
            row.setCellFactory(control -> {
                ListCell<JsonNode> cell = rowCell(column, fields,
                                                  extended - layout.getListCellVerticalInset(),
                                                  layout);
                cell.setPrefHeight(extended);
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
        double available = width - layout.getTableHorizontalInset()
                           - layout.getTableRowHorizontalInset()
                           - layout.getListCellHorizontalInset()
                           - layout.getListHorizontalInset();
        double cellHeight = rowHeight(cardinality, layout, available)
                            + layout.getTableRowVerticalInset();
        TableView<JsonNode> table = tableBase();
        children.forEach(child -> {
            INDENT indent = indent(child);
            table.getColumns()
                 .add(child.buildColumn(layout, inset(layout, 0, child, indent),
                                        indent));
        });
        height = cellHeight + layout.measureHeader(table)
                 + layout.getTableVerticalInset();
        return height;
    }

    @Override
    Double getCalculatedHeight() {
        if (isFold()) {
            return fold.getCalculatedHeight();
        }
        return super.getCalculatedHeight();
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
        double slack = justifiedWidth - tableColumnWidth;
        assert slack >= 0 : String.format("Negative slack: %.2f (%.2f) \n%s",
                                          slack, width, this);
        double total = Layout.snap(children.stream()
                                           .map(child -> rawWidth(layout))
                                           .reduce((a, b) -> a + b)
                                           .orElse(0.0d));
        children.forEach(child -> {
            double childWidth = child.tableColumnWidth(layout);
            child.justify(slack * (childWidth / total) + childWidth, layout);
        });
    }

    @Override
    double layout(int cardinality, Layout layout, double width) {
        height = null;
        useTable = false;
        if (isFold()) {
            return fold.layout(cardinality, layout, width);
        }
        double labelWidth = children.stream()
                                    .mapToDouble(child -> child.getLabelWidth(layout))
                                    .max()
                                    .getAsDouble();
        double available = width - labelWidth - layout.getNestedInset();
        outlineWidth = children.stream()
                               .mapToDouble(child -> {
                                   return child.layout(cardinality, layout,
                                                       available);
                               })
                               .max()
                               .orElse(0d)
                       + labelWidth;
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
        for (SchemaNode child : children) {
            ArrayNode aggregate = JsonNodeFactory.instance.arrayNode();
            int cardSum = 0;
            boolean childSingular = false;
            for (JsonNode node : data) {
                JsonNode sub = node.get(child.field);
                if (sub instanceof ArrayNode) {
                    childSingular = false;
                    aggregate.addAll((ArrayNode) sub);
                    cardSum += sub.size();
                } else {
                    childSingular = true;
                    aggregate.add(sub);
                    cardSum += 1;
                }
            }
            sum += data.size() == 0 ? 1 : Math.round(cardSum / data.size());
            tableColumnWidth += child.measure(aggregate, childSingular, layout,
                                              indent(child));
        }
        averageCardinality = (int) Math.ceil(sum / children.size());
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

    private TableView<JsonNode> buildNestedTable(Function<JsonNode, JsonNode> extractor,
                                                 int cardinality, Layout layout,
                                                 double justified) {
        if (isFold()) {
            return fold.buildNestedTable(extract(extractor),
                                         averageCardinality * cardinality,
                                         layout, justified);
        }
        TableView<JsonNode> table = tableBase();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        children.forEach(child -> {
            INDENT indent = indent(child);
            table.getColumns()
                 .add(child.buildColumn(layout, inset(layout, 0, child, indent),
                                        indent));
        });

        Map<SchemaNode, TableColumn<JsonNode, ?>> columnMap = new HashMap<>();
        List<TableColumn<JsonNode, ?>> columns = table.getColumns();
        while (!columns.isEmpty()) {
            List<TableColumn<JsonNode, ?>> leaves = new ArrayList<>();
            columns.forEach(c -> {
                columnMap.put((SchemaNode) c.getUserData(), c);
                leaves.addAll(c.getColumns());
            });
            columns = leaves;
        }

        Function<Double, Pair<Consumer<JsonNode>, Control>> topLevel = buildColumn(cardinality,
                                                                                   null,
                                                                                   columnMap,
                                                                                   layout,
                                                                                   0,
                                                                                   INDENT.NONE);
        double available = justified - layout.getTableHorizontalInset()
                           - layout.getTableRowHorizontalInset()
                           - layout.getListCellHorizontalInset()
                           - layout.getListHorizontalInset();
        double rowHeight = rowHeight(averageCardinality, layout, available);
        table.setRowFactory(tableView -> {
            Pair<Consumer<JsonNode>, Control> relationRow = topLevel.apply(rowHeight);
            RelationTableRow row = new RelationTableRow(relationRow.getKey(),
                                                        relationRow.getValue());
            layout.getModel()
                  .apply(row, Relation.this);
            return row;
        });

        layout.getModel()
              .apply(table, this);
        table.setFixedCellSize(rowHeight);
        double contentHeight = (cardinality
                                * (rowHeight + layout.getListCellVerticalInset()
                                   + layout.getListVerticalInset()
                                   + layout.getTableRowVerticalInset()))
                               + layout.measureHeader(table)
                               + layout.getTableVerticalInset();
        table.setPrefHeight(contentHeight);
        //        if (cardinality == 1) {
        //            table.setMinHeight(contentHeight);
        //        }
        return table;
    }

    private ListView<JsonNode> buildOutline(Function<JsonNode, JsonNode> extractor,
                                            int cardinality, Layout layout) {
        if (isFold()) {
            return fold.buildOutline(extract(extractor),
                                     averageCardinality * cardinality, layout);
        }

        double cellHeight = columnSets.stream()
                                      .mapToDouble(cs -> cs.getCellHeight())
                                      .sum();
        ListView<JsonNode> list = new ListView<>();
        layout.getModel()
              .apply(list, this);
        list.setPrefHeight(cellHeight * cardinality);
        list.setFixedCellSize(cellHeight + layout.getListCellVerticalInset());
        list.setCellFactory(c -> {
            ListCell<JsonNode> cell = listCell(extractor, cellHeight, layout);
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
        if (child.equals(children.get(0))) {
            indent = INDENT.LEFT;
        } else if (child.equals(children.get(children.size() - 1))) {
            indent = INDENT.RIGHT;
        }
        return indent;
    }

    private double inset(Layout layout, double inset, SchemaNode child,
                         INDENT indent) {
        switch (indent) {
            case RIGHT:
                if (child.equals(children.get(children.size() - 1))) {
                    return inset + layout.getNestedRightInset();
                } else if (child.equals(children.get(0))) {
                    return layout.getNestedLeftInset();
                }
                break;
            case LEFT:
                if (child.equals(children.get(0))) {
                    return inset + layout.getNestedLeftInset();
                } else if (child.equals(children.get(children.size() - 1))) {
                    return layout.getNestedRightInset();
                }
                break;
            case NONE:
                if (child.equals(children.get(children.size() - 1))) {
                    return layout.getNestedRightInset();
                } else if (child.equals(children.get(0))) {
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

    private ListCell<JsonNode> rowCell(TableColumn<JsonNode, ?> column,
                                       List<Function<Double, Pair<Consumer<JsonNode>, Control>>> fields,
                                       double resolvedHeight, Layout layout) {

        return new ListCell<JsonNode>() {
            private Consumer<JsonNode> master;
            private HBox               row;
            {
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setAlignment(Pos.CENTER_LEFT);
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
                if (row == null) {
                    buildRow();
                }
                setGraphic(row);
                master.accept(item);
                row.requestLayout();
            }

            private void buildRow() {
                row = new HBox();
                row.setMinWidth(0);
                row.setPrefWidth(1);
                row.setPrefHeight(resolvedHeight);
                List<Consumer<JsonNode>> consumers = new ArrayList<>();
                fields.forEach(p -> {
                    Pair<Consumer<JsonNode>, Control> pair = p.apply(resolvedHeight);
                    Control control = pair.getValue();
                    control.setPrefHeight(resolvedHeight);
                    row.getChildren()
                       .add(control);
                    consumers.add(pair.getKey());
                });
                master = node -> consumers.forEach(c -> {
                    c.accept(node);
                });
            }
        };
    }

    private TableView<JsonNode> tableBase() {
        TableView<JsonNode> table = new TableView<>();
        table.setPlaceholder(new Text());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return table;
    }
}
