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

import java.util.ArrayList;
import java.util.List;

import com.chiralbehaviors.layout.LayoutLabel;
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
import com.chiralbehaviors.layout.style.PrimitiveStyle.PrimitiveLayoutCell;
import com.chiralbehaviors.layout.style.PrimitiveStyle.PrimitiveTextStyle;
import com.chiralbehaviors.layout.table.NestedCell;
import com.chiralbehaviors.layout.table.NestedRow;
import com.chiralbehaviors.layout.table.NestedTable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

public class Style {

    public interface LayoutObserver {
        default <T extends LayoutCell<?>> void apply(T cell, Primitive p) {
        }

        default <T extends LayoutCell<?>> void apply(VirtualFlow<T> list,
                                                     Relation relation) {
        }
    }

    public static Insets add(Insets a, Insets b) {
        return new Insets(a.getTop() + b.getTop(), a.getRight() + b.getRight(),
                          a.getBottom() + b.getBottom(),
                          a.getLeft() + b.getLeft());
    }

    public static LabelStyle labelStyle(Label label) {
        return new LabelStyle(label);
    }

    public static double relax(double value) {
        return Math.max(0, Math.floor(value));
    }

    public static double snap(double value) {
        return Math.ceil(value);
    }

    public static double textWidth(String string, Font textFont,
                                   Orientation orientation) {
        Text text = new Text(string);
        text.setFont(textFont);
        Bounds tb = text.getBoundsInLocal();
        Bounds bounds = Shape.intersect(text,
                                        new Rectangle(tb.getMinX(),
                                                      tb.getMinY(),
                                                      tb.getWidth(),
                                                      tb.getHeight()))
                             .getBoundsInLocal();
        return orientation == Orientation.HORIZONTAL ? bounds.getWidth()
                                                     : bounds.getHeight();
    }

    public static String toString(JsonNode value) {
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

    private final LayoutObserver observer;

    private final List<String>   styleSheets = new ArrayList<>();

    public Style() {
        this(new LayoutObserver() {
        });
    }

    public Style(LayoutObserver observer) {
        this.observer = observer;
    }

    public <T extends LayoutCell<?>> void apply(T cell, Primitive p) {
        observer.apply(cell, p);
    }

    public <T extends LayoutCell<?>> void apply(VirtualFlow<T> list,
                                                Relation relation) {
        observer.apply(list, relation);
    }

    public PrimitiveLayout layout(Primitive p) {
        return new PrimitiveLayout(p, style(p));
    }

    public RelationLayout layout(Relation r) {
        return new RelationLayout(r, style(r));
    }

    public SchemaNodeLayout layout(SchemaNode n) {
        return n instanceof Primitive ? layout((Primitive) n)
                                      : layout((Relation) n);
    }

    public void setStyleSheets(List<String> stylesheets) {
        this.styleSheets.clear();
        this.styleSheets.addAll(stylesheets);
    }

    public PrimitiveStyle style(Primitive p) {
        VBox root = new VBox();

        PrimitiveList list = new PrimitiveList(p.getField());

        LayoutLabel label = new LayoutLabel("Lorem Ipsum");

        Label primitiveText = new Label("Lorem Ipsum");
        primitiveText.getStyleClass()
                     .clear();
        primitiveText.getStyleClass()
                     .addAll(PrimitiveLayoutCell.DEFAULT_STYLE,
                             PrimitiveTextStyle.PRIMITIVE_TEXT_CLASS,
                             p.getField());

        root.getChildren()
            .addAll(list, label, primitiveText);

        Scene scene = new Scene(root, 800, 600);
        scene.getStylesheets()
             .addAll(styleSheets);

        root.applyCss();
        root.layout();

        list.applyCss();
        list.layout();

        label.applyCss();
        label.layout();

        primitiveText.applyCss();
        primitiveText.layout();

        return new PrimitiveTextStyle(labelStyle(label), list.getInsets(),
                                      labelStyle(primitiveText));
    }

    public RelationStyle style(Relation r) {

        VBox root = new VBox();

        NestedTable table = new NestedTable(r.getField());
        NestedRow row = new NestedRow(r.getField());
        NestedCell rowCell = new NestedCell(r.getField());

        Outline outline = new Outline(r.getField());
        OutlineCell outlineCell = new OutlineCell(r.getField());
        OutlineColumn column = new OutlineColumn(r.getField());
        OutlineElement element = new OutlineElement(r.getField());
        Span span = new Span(r.getField());

        LayoutLabel label = new LayoutLabel("Lorem Ipsum");

        root.getChildren()
            .addAll(table, row, rowCell, outline, outlineCell, column, element,
                    span, label);
        Scene scene = new Scene(root, 800, 600);
        scene.getStylesheets()
             .addAll(styleSheets);

        root.applyCss();
        root.layout();

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

        return new RelationStyle(labelStyle(label), table, row, rowCell,
                                 outline, outlineCell, column, span, element);

    }

    public List<String> styleSheets() {
        return styleSheets;
    }
}