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
import com.chiralbehaviors.layout.SchemaNodeLayout.INDENT;
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
    private boolean                useTable = false;

    public Relation(String label) {
        super(label);
    }

    public void addChild(SchemaNode child) {
        children.add(child);
    }

    public void autoLayout(double width) {
        double justified = LayoutProvider.snap(width);
        layout(justified);
        compress(justified);
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
        return useTable ? buildNestedTable(extractor, cardinality)
                        : buildOutline(extractor, cardinality);
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
    public void compress(double justified) {
        if (isFold()) {
            fold.compress(justified);
        } else {
            layout.compress(justified);
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

    public boolean isUseTable() {
        if (isFold()) {
            return fold.isUseTable();
        }
        return useTable;
    }

    @Override
    public void justify(double width) {
        if (isFold()) {
            fold.justify(width);
        } else {
            assert useTable : "Not a nested table";
            layout.justify(width);
        }
    }

    public void justifyTable(double width) {
        if (isFold()) {
            fold.justifyTable(width);
        } else {
            layout.justifyTable(width);
        }
    }

    @Override
    public double layout(double width) {
        useTable = false;
        return isFold() ? fold.layout(width) : layout.layout(width);
    }

    @Override
    public double layoutWidth() {
        return isFold() ? fold.layoutWidth() : layout.getLayoutWidth();
    }

    @Override
    public double measure(JsonNode data, boolean isSingular,
                          LayoutProvider provider) {
        layout = provider.layout(this);

        if (isAutoFoldable()) {
            fold = ((Relation) children.get(children.size() - 1));
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
        useTable = true;
        return layout.nestTable();
    }

    public double nestTable(INDENT indent) {
        useTable = true;
        return isFold() ? fold.nestTable(indent) : layout.nestTable(indent);
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

        return layout.outlineElement(field, cardinality, label, labelWidth,
                                     extractor, useTable, justified);
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
    public double tableColumnWidth() {
        return isFold() ? fold.tableColumnWidth() : layout.tableColumnWidth();
    }

    @Override
    public double justifiedTableColumnWidth() {
        return isFold() ? fold.justifiedTableColumnWidth()
                        : layout.justifiedTableColumnWidth();
    }

    @Override
    public double tableColumnWidth(INDENT indent) {
        return isFold() ? fold.tableColumnWidth(indent)
                        : layout.tableColumnWidth(indent);
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

    protected JsonControl buildNestedTable(Function<JsonNode, JsonNode> extractor,
                                           int cardinality) {
        if (isFold()) {
            return fold.buildNestedTable(extract(extractor),
                                         layout.getAverageCardinality()
                                                             * cardinality);
        }
        return layout.buildNestedTable(cardinality);
    }

    protected JsonControl buildOutline(Function<JsonNode, JsonNode> extractor,
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
