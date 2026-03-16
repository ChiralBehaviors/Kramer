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
import java.util.IdentityHashMap;
import java.util.List;

import javafx.application.Platform;

import com.chiralbehaviors.layout.DefaultLayoutStylesheet;
import com.chiralbehaviors.layout.MeasurementStrategy;
import com.chiralbehaviors.layout.LayoutLabel;
import com.chiralbehaviors.layout.LayoutStylesheet;
import com.chiralbehaviors.layout.SchemaPath;
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
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * Factory for layout styles. Caches computed {@link PrimitiveStyle} and
 * {@link RelationStyle} per schema node. All measurement methods must run
 * on the JavaFX Application Thread (JAT); assertions guard this invariant.
 */
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

    public static double textWidth(String string, Font textFont) {
        Text text = new Text(string);
        text.setFont(textFont);
        Bounds tb = text.getBoundsInLocal();
        return Shape.intersect(text,
                               new Rectangle(tb.getMinX(), tb.getMinY(),
                                             tb.getWidth(), tb.getHeight()))
                    .getBoundsInLocal()
                    .getWidth();
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

    /** Logical pixels for measurement scene — device-independent, not physical screen pixels. */
    static final int MEASUREMENT_SCENE_WIDTH  = 800;
    static final int MEASUREMENT_SCENE_HEIGHT = 600;

    private final LayoutObserver              observer;
    private Object                            owner;
    private LayoutStylesheet                  stylesheet;
    private MeasurementStrategy               measurementStrategy;

    private final List<String>                styleSheets          = new ArrayList<>();
    private final IdentityHashMap<Primitive, PrimitiveStyle>  primitiveStyleCache  = new IdentityHashMap<>();
    private final IdentityHashMap<Relation, RelationStyle>   relationStyleCache   = new IdentityHashMap<>();
    private final IdentityHashMap<SchemaNode, SchemaNodeLayout> layoutCache = new IdentityHashMap<>();

    public Style() {
        this(new LayoutObserver() {
        });
    }

    public Style(LayoutObserver observer) {
        this.observer = observer;
        this.stylesheet = new DefaultLayoutStylesheet(this);
    }

    public Style(LayoutObserver observer, LayoutStylesheet stylesheet) {
        this.observer = observer;
        this.stylesheet = stylesheet;
    }

    public Style(LayoutStylesheet stylesheet) {
        this(new LayoutObserver() {
        }, stylesheet);
    }

    /**
     * Constructs a headless Style backed by a {@link MeasurementStrategy}.
     * CSS measurement is skipped; all style metrics come from the strategy.
     *
     * @param observer            layout observer
     * @param measurementStrategy strategy to supply style metrics
     */
    public Style(LayoutObserver observer, MeasurementStrategy measurementStrategy) {
        this.observer = observer;
        this.measurementStrategy = measurementStrategy;
        this.stylesheet = new DefaultLayoutStylesheet(this);
    }

    /**
     * Constructs a headless Style with default observer and supplied strategy.
     *
     * @param measurementStrategy strategy to supply style metrics
     */
    public Style(MeasurementStrategy measurementStrategy) {
        this(new LayoutObserver() {
        }, measurementStrategy);
    }

    public LayoutStylesheet getStylesheet() {
        return stylesheet;
    }

    public void setStylesheet(LayoutStylesheet stylesheet) {
        if (stylesheet == null) {
            throw new NullPointerException("stylesheet must not be null");
        }
        this.stylesheet = stylesheet;
    }

    public <T extends LayoutCell<?>> void apply(T cell, Primitive p) {
        observer.apply(cell, p);
    }

    public <T extends LayoutCell<?>> void apply(VirtualFlow<T> list,
                                                Relation relation) {
        observer.apply(list, relation);
    }

    public PrimitiveLayout layout(Primitive p) {
        return (PrimitiveLayout) layoutCache.computeIfAbsent(p,
            k -> new PrimitiveLayout(p, style(p)));
    }

    public RelationLayout layout(Relation r) {
        return (RelationLayout) layoutCache.computeIfAbsent(r,
            k -> new RelationLayout(r, style(r)));
    }

    public SchemaNodeLayout layout(SchemaNode n) {
        return n instanceof Primitive p ? layout(p)
                                       : layout((Relation) n);
    }

    void setStyleSheets(List<String> stylesheets) {
        this.styleSheets.clear();
        this.styleSheets.addAll(stylesheets);
        primitiveStyleCache.clear();
        relationStyleCache.clear();
        layoutCache.clear();
    }

    public void setStyleSheets(List<String> stylesheets, Object owner) {
        if (this.owner != null && this.owner != owner) {
            throw new IllegalStateException(
                "Style already owned by " + this.owner);
        }
        this.owner = owner;
        setStyleSheets(stylesheets);
    }

    public PrimitiveStyle style(Primitive p) {
        return primitiveStyleCache.computeIfAbsent(p,
                                                    k -> computePrimitiveStyle(p));
    }

    protected PrimitiveStyle computePrimitiveStyle(Primitive p) {
        if (measurementStrategy != null) {
            return measurementStrategy.measurePrimitiveStyle(p, styleSheets);
        }
        assert Platform.isFxApplicationThread() : "computePrimitiveStyle must run on JAT";
        VBox root = new VBox();

        PrimitiveList list = new PrimitiveList(SchemaPath.sanitize(p.getField()));

        LayoutLabel label = new LayoutLabel("Lorem Ipsum");

        Label primitiveText = new Label("Lorem Ipsum");
        primitiveText.getStyleClass()
                     .clear();
        primitiveText.getStyleClass()
                     .addAll(PrimitiveLayoutCell.DEFAULT_STYLE,
                             PrimitiveTextStyle.PRIMITIVE_TEXT_CLASS,
                             SchemaPath.sanitize(p.getField()));

        root.getChildren()
            .addAll(list, label, primitiveText);

        Scene scene = new Scene(root, MEASUREMENT_SCENE_WIDTH, MEASUREMENT_SCENE_HEIGHT);
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
        return relationStyleCache.computeIfAbsent(r,
                                                   k -> computeRelationStyle(r));
    }

    protected RelationStyle computeRelationStyle(Relation r) {
        if (measurementStrategy != null) {
            return measurementStrategy.measureRelationStyle(r, styleSheets);
        }
        assert Platform.isFxApplicationThread() : "computeRelationStyle must run on JAT";
        VBox root = new VBox();

        String cssClass = SchemaPath.sanitize(r.getField());
        NestedTable table = new NestedTable(cssClass);
        NestedRow row = new NestedRow(cssClass);
        NestedCell rowCell = new NestedCell(cssClass);

        Outline outline = new Outline(cssClass);
        OutlineCell outlineCell = new OutlineCell(cssClass);
        OutlineColumn column = new OutlineColumn(cssClass);
        OutlineElement element = new OutlineElement(cssClass);
        Span span = new Span(cssClass);

        LayoutLabel label = new LayoutLabel("Lorem Ipsum");

        root.getChildren()
            .addAll(table, row, rowCell, outline, outlineCell, column, element,
                    span, label);
        Scene scene = new Scene(root, MEASUREMENT_SCENE_WIDTH, MEASUREMENT_SCENE_HEIGHT);
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

    // Visible for testing
    int primitiveStyleCacheSize() {
        return primitiveStyleCache.size();
    }

    // Visible for testing
    int relationStyleCacheSize() {
        return relationStyleCache.size();
    }

    // Visible for testing
    int layoutCacheSize() {
        return layoutCache.size();
    }

    /**
     * Clears all cached styles and layouts. Call when discarding a schema tree
     * or when stylesheets change outside the normal listener path.
     */
    public void clearCaches() {
        primitiveStyleCache.clear();
        relationStyleCache.clear();
        layoutCache.clear();
    }
}