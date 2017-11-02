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
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.chiralbehaviors.layout.LayoutProvider;
import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.SchemaNodeLayout.Indent;
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
    private boolean                autoFold = true;
    private final List<SchemaNode> children = new ArrayList<>();
    private Relation               fold;
    private RelationLayout         layout;

    public Relation(String label) {
        super(label);
    }

    public void addChild(SchemaNode child) {
        children.add(child);
    }

    @Override
    public void adjustHeight(double delta) {
        if (isFold()) {
            fold.adjustHeight(delta);
        } else {
            layout.adjustHeight(delta);
        }
    }

    public void autoLayout(double width) {
        double justified = LayoutProvider.snap(width);
        layout(justified);
        compress(justified, false);
    }

    @Override
    public Pair<Consumer<JsonNode>, Region> buildColumn(NestedTable table,
                                                        double rendered) {
        return table.buildRelation(rendered, layout);
    }

    @Override
    public Function<Double, Region> buildColumnHeader() {
        if (isFold()) {
            return fold.buildColumnHeader();
        }
        return layout.columnHeader();
    }

    public JsonControl buildControl(int cardinality, double width) {
        if (isFold()) {
            return fold.buildControl(layout.getAverageCardinality()
                                     * cardinality, width);
        }
        return buildControl(cardinality, n -> n);
    }

    public JsonControl buildControl(int cardinality,
                                    Function<JsonNode, JsonNode> extractor) {
        return layout.buildControl(cardinality, extractor);
    }

    @Override
    public double calculateTableColumnWidth() {
        return isFold() ? fold.calculateTableColumnWidth()
                        : layout.calculateTableColumnWidth();
    }

    @Override
    public double cellHeight(int card, double width) {
        if (isFold()) {
            return fold.cellHeight(layout.getAverageCardinality() * card,
                                   width);
        }
        return layout.cellHeight(card, width);
    }

    @Override
    public double columnHeaderHeight() {
        if (isFold()) {
            return fold.columnHeaderHeight();
        }
        return layout.getColumnHeaderHeight();
    }

    @Override
    public void compress(double justified, boolean scrolled) {
        if (isFold()) {
            fold.compress(justified, scrolled);
        } else {
            layout.compress(justified, scrolled);
        }
    }

    @Override
    public JsonNode extractFrom(JsonNode jsonNode) {
        if (isFold()) {
            return fold.extractFrom(super.extractFrom(jsonNode));
        }
        return super.extractFrom(jsonNode);
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

    public Relation getFold() {
        return fold;
    }

    @Override
    public double getLabelWidth() {
        if (isFold()) {
            return fold.getLabelWidth();
        }
        return layout.labelWidth(label);
    }

    @Override
    public RelationLayout getLayout() {
        return layout;
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
    public double justify(double width) {
        if (isFold()) {
            return fold.justify(width);
        } else {
            return layout.justify(width);
        }
    }

    @Override
    public double layout(double width) {
        return isFold() ? fold.layout(width) : layout.layout(width);
    }

    @Override
    public double layoutWidth() {
        return isFold() ? fold.layoutWidth() : layout.layoutWidth();
    }

    @Override
    public double measure(JsonNode data, boolean isSingular,
                          LayoutProvider provider) {
        layout = provider.layout(this);

        if (isAutoFoldable()) {
            fold = ((Relation) children.get(children.size() - 1));
            return fold.measure(flatten(data), isSingular, provider);
        }
        if (data.isNull() || children.size() == 0) {
            return 24;
        }

        return layout.measure(data, isSingular);
    }

    public void measure(JsonNode jsonNode, LayoutProvider layout) {
        measure(jsonNode, !jsonNode.isArray(), layout);
    }

    public double nestTable() {
        return layout.nestTable();
    }

    @Override
    public double nestTableColumn(Indent indent, double indentation) {
        return isFold() ? fold.nestTableColumn(indent, indentation)
                        : layout.nestTableColumn(indent, indentation);
    }

    @Override
    public Pair<Consumer<JsonNode>, Parent> outlineElement(int cardinality,
                                                           double labelWidth,
                                                           Function<JsonNode, JsonNode> extractor,
                                                           double justified) {
        if (isFold()) {
            return fold.outlineElement(layout.getAverageCardinality()
                                       * cardinality, labelWidth,
                                       extract(extractor), justified);
        }

        return layout.outlineElement(cardinality, labelWidth, extractor,
                                     justified);
    }

    public double outlineWidth() {
        return isFold() ? fold.outlineWidth() : layout.outlineWidth();
    }

    @Override
    public double rowHeight(int cardinality, double justified) {
        if (isFold()) {
            return fold.rowHeight(cardinality * layout.getAverageCardinality(),
                                  justified);
        }
        return layout.rowHeight(cardinality, justified);
    }

    public void setFold(boolean fold) {
        this.fold = (fold && children.size() == 1 && children.get(0)
                                                             .isRelation()) ? (Relation) children.get(0)
                                                                            : null;
    }

    @Override
    public double tableColumnWidth() {
        return isFold() ? fold.tableColumnWidth() : layout.tableColumnWidth();
    }

    @Override
    public String toString() {
        return toString(0);
    }

    @Override
    public String toString(int indent) {
        StringBuffer buf = new StringBuffer();
        buf.append(String.format("Relation [%s]", label));
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

    public JsonControl buildNestedTable(Function<JsonNode, JsonNode> extractor,
                                        int cardinality) {
        if (isFold()) {
            return fold.buildNestedTable(extract(extractor),
                                         layout.getAverageCardinality()
                                                             * cardinality);
        }
        return layout.buildNestedTable(cardinality);
    }

    public JsonControl buildOutline(Function<JsonNode, JsonNode> extractor,
                                    int cardinality) {
        if (isFold()) {
            return fold.buildOutline(extract(extractor),
                                     layout.getAverageCardinality()
                                                         * cardinality);
        }
        return layout.buildOutline(extractor, cardinality);
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
}
