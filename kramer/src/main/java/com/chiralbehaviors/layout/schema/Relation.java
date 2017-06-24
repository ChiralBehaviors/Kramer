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
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
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
    private double                 tableColumnWidth   = 0;
    private boolean                useTable           = false;

    public Relation(String label) {
        super(label);
    }

    public void addChild(SchemaNode child) {
        children.add(child);
    }

    public void autoLayout(int cardinality, Layout layout, double width) {
        layout(cardinality, layout, Layout.snap(width));
    }

    public Control buildControl(int cardinality, Layout layout, double width) {
        if (isFold()) {
            return fold.buildControl(averageCardinality * cardinality, layout,
                                     width);
        }
        return useTable ? buildNestedTable(n -> n, 1, layout, width)
                        : buildOutline(n -> n, 1, layout, width);
    }

    @Override
    public double elementHeight(int cardinality, Layout layout, double width) {
        if (isFold()) {
            return fold.elementHeight(cardinality, layout, width);
        }
        if (!useTable) {
            return columnSets.stream()
                             .mapToDouble(cs -> cs.getElementHeight())
                             .sum();
        }
        double slack = width - getTableColumnWidth(layout);
        assert slack >= 0 : String.format("Negative slack: %.2f (%.2f) \n%s",
                                          slack, width, this);
        TableView<JsonNode> table = tableBase(width);
        children.forEach(child -> {
            INDENT indent = indent(child);
            table.getColumns()
                 .add(child.buildColumn(layout, inset(layout, 0, child, indent),
                                        indent, width));
        });
        table.setPrefWidth(width);
        return (rowElement(cardinality, layout, width)
                + layout.getTableRowVerticalInset() * cardinality)
               + layout.measureHeader(table) + layout.getTableVerticalInset();
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

    @Override
    public double getTableColumnWidth(Layout layout) {
        if (isFold()) {
            return fold.getTableColumnWidth(layout);
        }
        return tableColumnWidth + layout.getNestedInset();
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
        if (jsonNode.isArray()) {
            ArrayNode array = (ArrayNode) jsonNode;
            measure(array, layout, INDENT.NONE);
        } else {
            ArrayNode singleton = JsonNodeFactory.instance.arrayNode();
            singleton.add(jsonNode);
            measure(singleton, layout, INDENT.NONE);
        }
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
        buf.append(String.format("Relation [%s:%.2f x %s]", label,
                                 tableColumnWidth, averageCardinality));
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
    Function<Double, Pair<Consumer<JsonNode>, Control>> buildColumn(int cardinality,
                                                                    Function<JsonNode, JsonNode> extractor,
                                                                    Map<SchemaNode, TableColumn<JsonNode, ?>> columnMap,
                                                                    Layout layout,
                                                                    double inset,
                                                                    INDENT indent) {
        return buildColumn(cardinality, extractor, columnMap, layout, inset,
                           indent);
    }

    @Override
    TableColumn<JsonNode, JsonNode> buildColumn(Layout layout, double inset,
                                                INDENT indent,
                                                double justified) {
        if (isFold()) {
            return fold.buildColumn(layout, inset, indent, justified);
        }
        TableColumn<JsonNode, JsonNode> column = super.buildColumn(layout,
                                                                   inset,
                                                                   indent,
                                                                   justified);
        column.setPrefWidth(justified);
        ObservableList<TableColumn<JsonNode, ?>> columns = column.getColumns();
        children.forEach(child -> columns.add(child.buildColumn(layout,
                                                                inset(layout,
                                                                      inset,
                                                                      child,
                                                                      indent),
                                                                indent(child),
                                                                justified)));
        return column;
    }

    // for testing
    List<ColumnSet> getColumnSets() {
        return columnSets;
    }

    @Override
    double layout(int cardinality, Layout layout, double width) {
        if (isFold()) {
            return fold.layout(cardinality, layout, width);
        }
        useTable = false;
        double listInset = layout.getListHorizontalInset();
        double tableInset = layout.getTableHorizontalInset();
        double available = width - children.stream()
                                           .mapToDouble(child -> child.getLabelWidth(layout))
                                           .max()
                                           .getAsDouble();
        double outlineWidth = children.stream()
                                      .mapToDouble(child -> child.layout(cardinality,
                                                                         layout,
                                                                         available))
                                      .max()
                                      .orElse(0d)
                              + listInset;
        double tableWidth = tableColumnWidth + tableInset;
        if (tableWidth <= outlineWidth) {
            nestTable();
            return tableWidth;
        }
        compress(cardinality, layout, width);
        return outlineWidth;
    }

    @Override
    double measure(ArrayNode data, Layout layout, INDENT indent) {
        if (isAutoFoldable()) {
            fold = ((Relation) children.get(children.size() - 1));
        }
        if (data.isNull() || children.size() == 0) {
            return 0;
        }
        double labelWidth = layout.textWidth(label);
        labelWidth += layout.getTextHorizontalInset();
        double sum = 0;
        tableColumnWidth = 0;
        for (SchemaNode child : children) {
            ArrayNode aggregate = JsonNodeFactory.instance.arrayNode();
            int cardSum = 0;
            for (JsonNode node : data) {
                JsonNode sub = node.get(child.field);
                if (sub instanceof ArrayNode) {
                    aggregate.addAll((ArrayNode) sub);
                    cardSum += sub.size();
                } else {
                    aggregate.add(sub);
                    cardSum += 1;
                }
            }
            sum += data.size() == 0 ? 1 : Math.round(cardSum / data.size());
            tableColumnWidth += child.measure(aggregate, layout, indent(child));
        }
        averageCardinality = (int) Math.ceil(sum / children.size());
        tableColumnWidth = Layout.snap(Math.max(labelWidth, tableColumnWidth));
        return isFold() ? fold.getTableColumnWidth(layout)
                        : getTableColumnWidth(layout);
    }

    @Override
    Pair<Consumer<JsonNode>, Parent> outlineElement(double labelWidth,
                                                    Function<JsonNode, JsonNode> extractor,
                                                    int cardinality,
                                                    Layout layout,
                                                    double justified) {
        if (isFold()) {
            return fold.outlineElement(labelWidth, extract(extractor),
                                       averageCardinality * cardinality, layout,
                                       justified);
        }
        Control control = useTable ? buildNestedTable(n -> n, cardinality,
                                                      layout, justified)
                                   : buildOutline(n -> n, cardinality, layout,
                                                  justified);
        Parent element;
        TextArea labelText = new TextArea(label);
        labelText.setWrapText(true);
        labelText.setPrefColumnCount(1);
        labelText.setMinWidth(labelWidth);
        labelText.setPrefWidth(labelWidth);
        Pane box;
        if (useTable) {
            box = new HBox();
            control.setPrefWidth(justified);
            double elementHeight = elementHeight(cardinality, layout,
                                                 justified);
            double contentHeight = Layout.snap(cardinality * (elementHeight
                                                              + layout.getListCellVerticalInset()))
                                   + layout.getListVerticalInset();
            box.setMinHeight(contentHeight);
            box.setPrefHeight(contentHeight);
        } else {
            box = new VBox();
            box.setPrefWidth(justified);
            labelText.setPrefHeight(labelHeight(layout));
            labelText.setMaxHeight(labelHeight(layout));
        }
        box.getChildren()
           .add(labelText);
        box.getChildren()
           .add(control);
        element = box;

        return new Pair<>(item -> {
            if (item == null) {
                return;
            }
            JsonNode extracted = extractor.apply(item);
            JsonNode extractedField = extracted == null ? null
                                                        : extracted.get(field);
            setItems(control, extractedField, layout);
        }, element);
    }

    @Override
    double rowElement(int cardinality, Layout layout, double justified) {
        if (isFold()) {
            return fold.rowHeight(averageCardinality * cardinality, layout,
                                  justified);
        }

        return extendedHeight(layout, cardinality, children.stream()
                                                           .mapToDouble(child -> Layout.snap(child.rowElement(cardinality,
                                                                                                              layout,
                                                                                                              justified)))
                                                           .max()
                                                           .getAsDouble());
    }

    @Override
    double rowHeight(int cardinality, Layout layout, double justified) {
        if (isFold()) {
            return fold.rowHeight(averageCardinality * cardinality, layout,
                                  justified);
        }

        double elementHeight = children.stream()
                                       .mapToDouble(child -> Layout.snap(child.rowHeight(averageCardinality,
                                                                                         layout,
                                                                                         justified)))
                                       .max()
                                       .getAsDouble();

        return extendedHeight(layout, cardinality, elementHeight);
    }

    private Function<Double, Pair<Consumer<JsonNode>, Control>> buildColumn(int cardinality,
                                                                            Function<JsonNode, JsonNode> extractor,
                                                                            Map<SchemaNode, TableColumn<JsonNode, ?>> columnMap,
                                                                            Layout layout,
                                                                            double inset,
                                                                            INDENT indent,
                                                                            boolean root,
                                                                            double justified) {
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
        double cellHeight = elementHeight(cardinality, layout, justified)
                            + layout.getListCellVerticalInset();
        double calculatedHeight = (cellHeight * cardinality)
                                  + layout.getListVerticalInset();

        Function<JsonNode, JsonNode> extract = root ? extractor
                                                    : extract(extractor);
        return rendered -> {
            double deficit = Math.max(0, rendered - calculatedHeight);
            double childDeficit = Math.max(0, deficit / cardinality);
            double extended = Layout.snap(cellHeight + childDeficit);

            ListView<JsonNode> row = new ListView<JsonNode>();
            layout.getModel()
                  .apply(row, this);
            HBox.setHgrow(row, Priority.ALWAYS);
            row.setMinWidth(0);
            row.setPrefWidth(1);
            row.setFixedCellSize(extended);
            row.setPrefHeight(rendered);
            row.setCellFactory(control -> {
                ListCell<JsonNode> cell = rowCell(fields,
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

    private TableView<JsonNode> buildNestedTable(Function<JsonNode, JsonNode> extractor,
                                                 int cardinality, Layout layout,
                                                 double justified) {
        if (isFold()) {
            return fold.buildNestedTable(extract(extractor),
                                         averageCardinality * cardinality,
                                         layout, justified);
        }
        TableView<JsonNode> table = tableBase(justified);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        children.forEach(child -> {
            INDENT indent = indent(child);
            table.getColumns()
                 .add(child.buildColumn(layout, inset(layout, 0, child, indent),
                                        indent, justified));
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
                                                                                   n -> n,
                                                                                   columnMap,
                                                                                   layout,
                                                                                   0,
                                                                                   INDENT.NONE,
                                                                                   true,
                                                                                   justified);

        double height = extendedHeight(layout, 1,
                                       elementHeight(cardinality, layout,
                                                     justified));

        table.setRowFactory(tableView -> {
            Pair<Consumer<JsonNode>, Control> relationRow = topLevel.apply(height);
            RelationTableRow row = new RelationTableRow(relationRow.getKey(),
                                                        relationRow.getValue());
            layout.getModel()
                  .apply(row, Relation.this);
            return row;
        });

        layout.getModel()
              .apply(table, this);
        table.setFixedCellSize(height);
        double rowHeight = rowHeight(averageCardinality, layout, justified)
                           + layout.getTableRowVerticalInset();
        double contentHeight = (rowHeight * cardinality)
                               + layout.measureHeader(table)
                               + layout.getTableVerticalInset();
        table.setPrefHeight(contentHeight);
        if (cardinality > 1) {
            table.setMinHeight(contentHeight);
        }
        return table;
    }

    private ListView<JsonNode> buildOutline(Function<JsonNode, JsonNode> extractor,
                                            int cardinality, Layout layout,
                                            double justified) {
        if (isFold()) {
            return fold.buildOutline(extract(extractor),
                                     averageCardinality * cardinality, layout,
                                     justified);
        }

        double outlineLabelWidth = children.stream()
                                           .mapToDouble(child -> child.getLabelWidth(layout))
                                           .max()
                                           .getAsDouble();
        ListView<JsonNode> list = new ListView<>();
        layout.getModel()
              .apply(list, this);

        double elementHeight = columnSets.stream()
                                         .mapToDouble(cs -> cs.getElementHeight())
                                         .sum();
        double contentHeight = Layout.snap(cardinality
                                           * (elementHeight
                                              + layout.getListCellVerticalInset()));
        list.setPrefHeight(contentHeight + layout.getListVerticalInset());
        list.setFixedCellSize(elementHeight
                              + layout.getListCellVerticalInset());
        list.setCellFactory(c -> {
            ListCell<JsonNode> cell = outlineListCell(outlineLabelWidth,
                                                      extractor, elementHeight,
                                                      layout, justified);
            layout.getModel()
                  .apply(cell, this);
            return cell;
        });
        list.setMinWidth(0);
        list.setPrefWidth(1);
        list.setPlaceholder(new Text());
        return list;
    }

    private void compress(int cardinality, Layout layout, double available) {
        ColumnSet current = null;
        double halfWidth = available / 2d;
        for (SchemaNode child : children) {
            if (child.getTableColumnWidth(layout) > halfWidth) {
                current = new ColumnSet();
                columnSets.add(current);
            } else if (current == null) {
                current = new ColumnSet();
                columnSets.add(current);
            }
            current.add(child);
        }
        columnSets.forEach(cs -> cs.compress(cardinality, layout, available));
    }

    private double extendedHeight(Layout layout, int cardinality,
                                  double elementHeight) {
        return Layout.snap(cardinality * (elementHeight
                                          + layout.getListCellVerticalInset()))
               + layout.getListVerticalInset();
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

    private void nestTable() {
        useTable = true;
        children.forEach(child -> {
            if (child.isRelation()) {
                ((Relation) child).nestTable();
            }
        });
    }

    private ListCell<JsonNode> outlineListCell(double outlineLabelWidth,
                                               Function<JsonNode, JsonNode> extractor,
                                               double elementHeight,
                                               Layout layout,
                                               double justified) {
        return new ListCell<JsonNode>() {
            VBox                                              cell;
            Map<SchemaNode, Pair<Consumer<JsonNode>, Parent>> controls = new HashMap<>();
            {
                itemProperty().addListener((obs, oldItem, newItem) -> {
                    if (newItem != null) {
                        if (cell == null) {
                            initialize(outlineLabelWidth, extractor, layout,
                                       justified);
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
                children.forEach(child -> {
                    controls.get(child)
                            .getKey()
                            .accept(item);
                });
            }

            private void initialize(double outlineLabelWidth,
                                    Function<JsonNode, JsonNode> extractor,
                                    Layout layout, double justified) {
                cell = new VBox();
                cell.setMinWidth(0);
                cell.setPrefWidth(1);
                cell.setMinHeight(elementHeight);
                cell.setPrefHeight(elementHeight);
                children.forEach(child -> {
                    Pair<Consumer<JsonNode>, Parent> master = child.outlineElement(outlineLabelWidth,
                                                                                   extractor,
                                                                                   averageCardinality,
                                                                                   layout,
                                                                                   justified);
                    controls.put(child, master);
                    cell.getChildren()
                        .add(master.getValue());
                });
            }
        };
    }

    private ListCell<JsonNode> rowCell(List<Function<Double, Pair<Consumer<JsonNode>, Control>>> fields,
                                       double resolvedHeight, Layout layout) {

        return new ListCell<JsonNode>() {
            private Consumer<JsonNode> master;
            private HBox               row;
            {
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setAlignment(Pos.CENTER);
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

    private TableView<JsonNode> tableBase(double justified) {
        TableView<JsonNode> table = new TableView<>();
        table.setPlaceholder(new Text());
        table.setPrefWidth(justified);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return table;
    }

}
