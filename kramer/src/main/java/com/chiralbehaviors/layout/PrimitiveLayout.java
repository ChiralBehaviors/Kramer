/**
 * Copyright (c) 2017 Chiral Behaviors, LLC, all rights reserved.
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

package com.chiralbehaviors.layout;

import static com.chiralbehaviors.layout.LayoutProvider.snap;

import java.util.List;
import java.util.function.Function;

import com.chiralbehaviors.layout.flowless.Cell;
import com.chiralbehaviors.layout.impl.ColumnHeader;
import com.chiralbehaviors.layout.impl.LayoutCell;
import com.chiralbehaviors.layout.impl.OutlineElement;
import com.chiralbehaviors.layout.impl.PrimitiveCell;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.control.Control;
import javafx.scene.layout.Region;

/**
 *
 * @author halhildebrand
 *
 */
public class PrimitiveLayout extends SchemaNodeLayout {
    protected double        scroll = 0.0;
    private double          maxWidth;
    private final Primitive p;
    @SuppressWarnings("unused")
    private boolean         variableLength;

    public PrimitiveLayout(LayoutProvider layout, Primitive p) {
        super(layout);
        this.p = p;
    }

    public void apply(Cell<JsonNode, ?> list) {
        layout.getModel()
              .apply(list, p);
    }

    @Override
    public LayoutCell<? extends Region> buildColumn(double rendered) {
        LayoutCell<? extends Region> control = buildControl(1);
        control.getNode()
               .setMinSize(justifiedWidth, rendered);
        control.getNode()
               .setPrefSize(justifiedWidth, rendered);
        control.getNode()
               .setMaxSize(justifiedWidth, rendered);
        return new LayoutCell<Region>() {
            @Override
            public Region getNode() {
                return control.getNode();
            }

            @Override
            public boolean isReusable() {
                return true;
            }

            @Override
            public void updateItem(JsonNode item) {
                control.updateItem(extractFrom(item));
            }
        };
    }

    public LayoutCell<? extends Region> buildControl(int cardinality) {
        return new PrimitiveCell();
    }

    public double calculateTableColumnWidth() {
        return columnWidth();
    }

    public double cellHeight(double justified) {
        if (height > 0) {
            return height;
        }
        double rows = Math.ceil((maxWidth / justified) + 0.5);
        height = snap((layout.getTextLineHeight() * rows)
                      + layout.getTextVerticalInset());
        return height;
    }

    @Override
    public Function<Double, ColumnHeader> columnHeader() {
        return rendered -> new ColumnHeader(snap(justifiedWidth
                                                 + columnHeaderIndentation),
                                            rendered, this);
    }

    @Override
    public double columnWidth() {
        return Math.max(columnWidth, labelWidth);
    }

    @Override
    public void compress(double available) {
        justifiedWidth = snap(available);
    }

    @Override
    public JsonNode extractFrom(JsonNode node) {
        return p.extractFrom(node);
    }

    @Override
    public String getField() {
        return p.getField();
    }

    @Override
    public double getJustifiedColumnWidth() {
        return snap(justifiedWidth);
    }

    @Override
    public String getLabel() {
        return p.getLabel();
    }

    @Override
    public double justify(double justified) {
        justifiedWidth = snap(justified);
        return justifiedWidth;
    }

    @Override
    public Control label(double labelWidth) {
        return label(labelWidth, p.getLabel());
    }

    @Override
    public double layout(double width) {
        clear();
        return width;
    }

    @Override
    public double layoutWidth() {
        return columnWidth();
    }

    @Override
    public double measure(JsonNode data, boolean singular) {
        clear();
        labelWidth = labelWidth(p.getLabel());
        double summedDataWidth = 0;
        maxWidth = 0;
        columnWidth = 0;
        for (JsonNode prim : SchemaNode.asList(data)) {
            List<JsonNode> rows = SchemaNode.asList(prim);
            double summedWidth = 0;
            for (JsonNode row : rows) {
                double w = width(row);
                summedWidth += w;
                maxWidth = Math.max(maxWidth, w);
            }
            summedDataWidth += rows.isEmpty() ? 1 : summedWidth / rows.size();
        }
        double averageWidth = data.size() == 0 ? 0
                                               : (summedDataWidth
                                                  / data.size());

        columnWidth = Math.max(labelWidth,
                               LayoutProvider.snap(Math.max(p.getDefaultWidth(),
                                                            averageWidth)));
        if (maxWidth > averageWidth) {
            variableLength = true;
        }
        return columnWidth;

    }

    @Override
    public double nestTableColumn(Indent indent, double indentation) {
        this.columnHeaderIndentation = indentation;
        return tableColumnWidth();
    }

    @Override
    public OutlineElement outlineElement(int cardinality, double labelWidth,
                                         Function<JsonNode, JsonNode> extractor,
                                         double justified) {
        return new OutlineElement(this, cardinality, labelWidth, extractor,
                                  justified);
    }

    public double tableColumnWidth() {
        return columnWidth();
    }

    @Override
    protected void clear() {
        super.clear();
        columnHeaderIndentation = 0.0;
    }

    protected double getColumnHeaderWidth() {
        return snap(justifiedWidth + columnHeaderIndentation);
    }

    protected double width(JsonNode row) {
        return layout.totalTextWidth(layout.textWidth(LayoutProvider.toString(row)));
    }
}