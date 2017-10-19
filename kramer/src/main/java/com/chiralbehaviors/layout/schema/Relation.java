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
import java.util.function.Consumer;
import java.util.function.Function;

import com.chiralbehaviors.layout.Layout;
import com.chiralbehaviors.layout.Layout.RelationLayout;
import com.chiralbehaviors.layout.control.JsonControl;
import com.chiralbehaviors.layout.control.NestedTable;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.scene.Parent;
import javafx.scene.layout.Region;
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
    private RelationLayout         rLayout;
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
        layout(cardinality, snapped);
        compress(snapped);
        cellHeight(cardinality, width);
    }

    @Override
    public Pair<Consumer<JsonNode>, Region> buildColumn(NestedTable table,
                                                        double rendered) {
        return table.buildRelation(rendered, this);
    }

    public JsonControl buildControl(int cardinality, Layout layout,
                                    double width) {
        if (isFold()) {
            return fold.buildControl(averageCardinality * cardinality, layout,
                                     width);
        }
        return useTable ? buildNestedTable(n -> n, cardinality, width)
                        : buildOutline(n -> n, cardinality);
    }

    public JsonControl buildNestedTable(Function<JsonNode, JsonNode> extractor,
                                        int cardinality, double justified) {
        if (isFold()) {
            return fold.buildNestedTable(extract(extractor),
                                         averageCardinality * cardinality,
                                         justified);
        }
        return rLayout.buildNestedTable(cardinality);
    }

    public JsonControl buildOutline(Function<JsonNode, JsonNode> extractor,
                                    int cardinality) {
        if (isFold()) {
            return fold.buildOutline(extract(extractor),
                                     averageCardinality * cardinality);
        }
        return rLayout.buildOutline(height, columnSets, extractor, cardinality);
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
    public Double getHeight() {
        return isFold() ? fold.getHeight() : height;
    }

    @Override
    public double getLabelWidth() {
        if (isFold()) {
            return fold.getLabelWidth();
        }
        return rLayout.labelWidth(label);
    }

    @Override
    public RelationLayout getLayout() {
        return rLayout;
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

    public boolean isSingular() {
        if (isFold()) {
            return fold.isSingular();
        }
        return singular;
    }

    @Override
    public boolean isUseTable() {
        if (isFold()) {
            return fold.isUseTable();
        }
        return useTable;
    }

    public void measure(JsonNode jsonNode, Layout layout) {
        measure(null, jsonNode, !jsonNode.isArray(), layout);
    }

    public void setAverageCardinality(int averageCardinality) {
        this.averageCardinality = averageCardinality;
    }

    public void setFold(boolean fold) {
        this.fold = (fold && children.size() == 1 && children.get(0)
                                                             .isRelation()) ? (Relation) children.get(0)
                                                                            : null;
    }

    public void setItem(JsonControl control, JsonNode data) {
        if (data == null) {
            data = JsonNodeFactory.instance.arrayNode();
        }
        if (isFold()) {
            fold.setItem(control, flatten(data));
        } else {
            control.setItem(data);
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
    double cellHeight(int card, double width) {
        if (isFold()) {
            return fold.cellHeight(averageCardinality * card, width);
        }
        if (height != null) {
            return height;
        }

        int cardinality = singular ? 1 : card;
        if (!useTable) {
            height = rLayout.outlineHeight(cardinality, (columnSets.stream()
                                                                   .mapToDouble(cs -> cs.getCellHeight())
                                                                   .sum()));
        } else {
            double elementHeight = elementHeight();
            rowHeight = rLayout.rowHeight(elementHeight);
            height = rLayout.tableHeight(cardinality, elementHeight);
        }
        return height;
    }

    @Override
    void compress(double justified) {
        if (isFold()) {
            fold.compress(justified);
            return;
        }
        if (useTable) {
            justify(rLayout.baseOutlineWidth(justified));
            return;
        }
        justifiedWidth = rLayout.baseOutlineWidth(justified);
        double labelWidth = Layout.snap(children.stream()
                                                .mapToDouble(n -> n.getLabelWidth())
                                                .max()
                                                .orElse(0));
        columnSets.clear();
        ColumnSet current = null;
        double halfWidth = justifiedWidth / 2d;
        for (SchemaNode child : children) {
            double childWidth = labelWidth + child.layoutWidth();
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
        columnSets.forEach(cs -> cs.compress(averageCardinality,
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
    void justify(double width) {
        if (isFold()) {
            fold.justify(width);
            return;
        }
        assert useTable : "Not a nested table";
        justifiedWidth = rLayout.baseTableColumnWidth(width);
        double slack = Layout.snap(Math.max(0,
                                            justifiedWidth - tableColumnWidth));
        double total = Layout.snap(children.stream()
                                           .map(child -> child.tableColumnWidth())
                                           .reduce((a, b) -> a + b)
                                           .orElse(0.0d));
        children.forEach(child -> {
            double childWidth = child.tableColumnWidth();
            double additional = Layout.snap(slack * (childWidth / total));
            double childJustified = additional + childWidth;
            child.justify(childJustified);
        });
    }

    @Override
    double layout(int cardinality, double width) {
        height = null;
        useTable = false;
        rowHeight = 0;
        if (isFold()) {
            return fold.layout(cardinality * averageCardinality, width);
        }
        double labelWidth = Layout.snap(children.stream()
                                                .mapToDouble(child -> child.getLabelWidth())
                                                .max()
                                                .getAsDouble());
        double available = rLayout.baseOutlineWidth(width - labelWidth);
        outlineWidth = Layout.snap(children.stream()
                                           .mapToDouble(child -> {
                                               return child.layout(cardinality,
                                                                   available);
                                           })
                                           .max()
                                           .orElse(0d)
                                   + labelWidth);
        double extended = rLayout.outlineWidth(outlineWidth);
        double tableWidth = tableColumnWidth();
        if (tableWidth <= extended) {
            nestTable();
            return tableWidth;
        }
        return extended;
    }

    /* (non-Javadoc)
     * @see com.chiralbehaviors.layout.schema.SchemaNode#layoutWidth(com.chiralbehaviors.layout.Layout)
     */
    @Override
    double layoutWidth() {
        return useTable ? rLayout.tableColumnWidth(tableColumnWidth)
                        : rLayout.outlineWidth(outlineWidth);
    }

    @Override
    double measure(Relation parent, JsonNode data, boolean isSingular,
                   Layout layout) {
        rLayout = layout.layout(this);
        if (isAutoFoldable()) {
            fold = ((Relation) children.get(children.size() - 1));
        }
        justifiedWidth = null;
        if (data.isNull() || children.size() == 0) {
            return 0;
        }

        singular = isSingular;
        double labelWidth = rLayout.labelWidth(label);
        double sum = 0;
        tableColumnWidth = 0;
        int singularChildren = 0;
        for (SchemaNode child : children) {
            ArrayNode aggregate = JsonNodeFactory.instance.arrayNode();
            int cardSum = 0;
            boolean childSingular = false;
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
            tableColumnWidth += child.measure(null, aggregate, childSingular,
                                              layout);
        }
        int effectiveChildren = children.size() - singularChildren;
        averageCardinality = Math.max(1,
                                      Math.min(4,
                                               effectiveChildren == 0 ? 1
                                                                      : (int) Math.ceil(sum
                                                                                        / effectiveChildren)));
        tableColumnWidth = Layout.snap(Math.max(labelWidth, tableColumnWidth));
        return rLayout.tableColumnWidth(isFold() ? fold.tableColumnWidth
                                                 : tableColumnWidth);
    }

    @Override
    Pair<Consumer<JsonNode>, Parent> outlineElement(int cardinality,
                                                    double labelWidth,
                                                    Function<JsonNode, JsonNode> extractor,
                                                    double justified) {
        if (isFold()) {
            return fold.outlineElement(averageCardinality * cardinality,
                                       labelWidth, extract(extractor),
                                       justified);
        }

        return rLayout.outlineElement(field, cardinality, label, labelWidth,
                                      extractor, height, useTable, justified);
    }

    @Override
    double outlineWidth() {
        return rLayout.outlineWidth(outlineWidth);
    }

    @Override
    double rowHeight(int cardinality, double justified) {
        if (isFold()) {
            return fold.rowHeight(cardinality * averageCardinality,
                                  justifiedWidth);
        }
        double elementHeight = elementHeight();
        rowHeight = rLayout.rowHeight(elementHeight);
        height = rLayout.tableHeight(cardinality, elementHeight);
        return height;
    }

    @Override
    double tableColumnWidth() {
        if (isFold()) {
            return fold.tableColumnWidth();
        }
        return rLayout.tableColumnWidth(tableColumnWidth);
    }

    private double elementHeight() {
        return children.stream()
                       .mapToDouble(child -> Layout.snap(child.rowHeight(averageCardinality,
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
}
