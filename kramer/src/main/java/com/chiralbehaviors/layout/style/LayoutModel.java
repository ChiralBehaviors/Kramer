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

package com.chiralbehaviors.layout.style;

import java.util.Collections;
import java.util.List;

import com.chiralbehaviors.layout.PrimitiveLayout;
import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.SchemaNodeLayout;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.PrimitiveList;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.outline.Outline;
import com.chiralbehaviors.layout.outline.OutlineCell;
import com.chiralbehaviors.layout.outline.OutlineColumn;
import com.chiralbehaviors.layout.outline.OutlineElement;
import com.chiralbehaviors.layout.outline.Span;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.PrimitiveStyle.PrimitiveLabelStyle;
import com.chiralbehaviors.layout.table.NestedCell;
import com.chiralbehaviors.layout.table.NestedRow;
import com.chiralbehaviors.layout.table.NestedTable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.javafx.scene.text.TextLayout;
import com.sun.javafx.tk.FontLoader;
import com.sun.javafx.tk.Toolkit;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.TextBoundsType;

@SuppressWarnings({ "deprecation", "restriction" })
public interface LayoutModel {
    static FontLoader       FONT_LOADER = Toolkit.getToolkit()
                                                 .getFontLoader();
    static final TextLayout LAYOUT      = Toolkit.getToolkit()
                                                 .getTextLayoutFactory()
                                                 .createLayout();

    static Insets add(Insets a, Insets b) {
        return new Insets(a.getTop() + b.getTop(), a.getRight() + b.getRight(),
                          a.getBottom() + b.getBottom(),
                          a.getLeft() + b.getLeft());
    }

    static double getLineHeight(Font font, TextBoundsType boundsType) {
        LAYOUT.setContent("W", font.impl_getNativeFont());
        LAYOUT.setWrapWidth(0);
        LAYOUT.setLineSpacing(0);
        if (boundsType == TextBoundsType.LOGICAL_VERTICAL_CENTER) {
            LAYOUT.setBoundsType(TextLayout.BOUNDS_CENTER);
        } else {
            LAYOUT.setBoundsType(0);
        }

        // RT-37092: Use the line bounds specifically, to include font leading.
        return LAYOUT.getLines()[0].getBounds()
                                   .getHeight();
    }

    static double relax(double value) {
        return Math.max(0, Math.floor(value) - 1);
    }

    static double snap(double value) {
        return Math.ceil(value);
    }

    static double textWidth(String text, Font textFont) {
        return FONT_LOADER.computeStringWidth(text, textFont);
    }

    static String toString(JsonNode value) {
        if (value == null) {
            return "";
        }
        if (value instanceof ArrayNode) {
            StringBuilder builder = new StringBuilder();
            boolean first = true;
            for (JsonNode e : value) {
                if (first) {
                    first = false;
                    builder.append('[');
                } else {
                    builder.append(", ");
                }
                builder.append(e.asText());
            }
            builder.append(']');
            return builder.toString();
        } else {
            return value.asText();
        }
    }

    default <T extends LayoutCell<?>> void apply(T cell, Primitive p) {
    }

    default <T extends LayoutCell<?>> void apply(VirtualFlow<T> list,
                                                 Relation relation) {
    }

    default LabelStyle labelStyle(Label label) {
        return new LabelStyle(label.getInsets(),
                              getLineHeight(label.getFont(),
                                            TextBoundsType.LOGICAL_VERTICAL_CENTER),
                              label.getFont());
    }

    default PrimitiveLayout layout(Primitive p) {
        return new PrimitiveLayout(p, style(p));
    }

    default RelationLayout layout(Relation r) {
        return new RelationLayout(r, style(r));
    }

    default SchemaNodeLayout layout(SchemaNode n) {
        return n instanceof Primitive ? layout((Primitive) n)
                                      : layout((Relation) n);
    }

    default PrimitiveStyle style(Primitive p) {
        VBox root = new VBox();

        PrimitiveList list = new PrimitiveList(p.getField());

        Label label = new Label("Lorem Ipsum");

        root.getChildren()
            .addAll(list, label);
        Scene scene = new Scene(root, 800, 600);
        List<String> styleSheets = styleSheets();
        if (styleSheets != null) {
            scene.getStylesheets()
                 .addAll(styleSheets);
        }

        list.applyCss();
        list.layout();
        return new PrimitiveLabelStyle(labelStyle(label), list.getInsets());
    }

    default RelationStyle style(Relation r) {

        VBox root = new VBox();

        NestedTable table = new NestedTable(r.getField());
        NestedRow row = new NestedRow(r.getField());
        NestedCell rowCell = new NestedCell(r.getField());

        Outline outline = new Outline(r.getField());
        OutlineCell outlineCell = new OutlineCell(r.getField());
        OutlineColumn column = new OutlineColumn(r.getField());
        OutlineElement element = new OutlineElement(r.getField());
        Span span = new Span(r.getField());

        Label label = new Label("Lorem Ipsum");

        root.getChildren()
            .addAll(table, row, rowCell, outline, outlineCell, column, element,
                    span, label);
        Scene scene = new Scene(root, 800, 600);
        List<String> styleSheets = styleSheets();
        if (styleSheets != null) {
            scene.getStylesheets()
                 .addAll(styleSheets);
        }
        table.applyCss();
        table.layout();

        row.applyCss();
        row.layout();

        rowCell.applyCss();
        rowCell.layout();

        outline.applyCss();
        outline.layout();

        outlineCell.applyCss();
        outlineCell.layout();

        column.applyCss();
        column.layout();

        element.applyCss();
        element.layout();

        span.applyCss();
        span.layout();

        label.applyCss();
        label.layout();

        return new RelationStyle(labelStyle(label), table.getInsets(),
                                 row.getInsets(), rowCell.getInsets(),
                                 outline.getInsets(), outlineCell.getInsets(),
                                 column.getInsets(), span.getInsets(),
                                 element.getInsets());

    }

    default List<String> styleSheets() {
        return Collections.emptyList();
    }
}