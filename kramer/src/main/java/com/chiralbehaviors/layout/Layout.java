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

package com.chiralbehaviors.layout;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import com.chiralbehaviors.layout.control.JsonControl;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.javafx.scene.text.TextLayout;
import com.sun.javafx.tk.FontLoader;
import com.sun.javafx.tk.Toolkit;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.TextBoundsType;
import javafx.util.Pair;

@SuppressWarnings("restriction")
public class Layout {

    public interface LayoutModel {

        default void apply(ListCell<JsonNode> cell, Relation relation) {
        }

        default void apply(ListView<JsonNode> list, Relation relation) {
        }

        default void apply(TableRow<JsonNode> row, Relation relation) {
        }

        default void apply(TableView<JsonNode> table, Relation relation) {
        }

        default void apply(TextArea control, Primitive primitive) {
        }
    }

    public interface PrimitiveLayout extends SchemaNodeLayout {

        JsonControl buildControl(int cardinality);

        Double cellHeight(double maxWidth, double justified);

        Pair<Consumer<JsonNode>, Parent> outlineElement(String field,
                                                        int cardinality,
                                                        Double height,
                                                        String label,
                                                        double labelWidth,
                                                        Function<JsonNode, JsonNode> extractor,
                                                        double justified);

        double width(JsonNode row);

    }

    public interface RelationLayout extends SchemaNodeLayout {

        void adjustHeight(double delta);

        void apply(ListCell<JsonNode> cell);

        void apply(ListView<JsonNode> list);

        double baseOutlineCellHeight(double cellHeight);

        double baseRowCellHeight(double extended);

        JsonControl buildNestedTable(int cardinality);

        JsonControl buildOutline(Double height,
                                 Function<JsonNode, JsonNode> extractor,
                                 int cardinality);

        double compress(double justified, int averageCardinality);

        double justify(double width, double tableColumnWidth);

        double outlineCellHeight(double baseHeight);

        Pair<Consumer<JsonNode>, Parent> outlineElement(String field,
                                                        int cardinality,
                                                        String label,
                                                        double labelWidth,
                                                        Function<JsonNode, JsonNode> extractor,
                                                        double height,
                                                        boolean useTable,
                                                        double justified);

        double outlineHeight(int cardinality);

        double outlineWidth(double outlineWidth);

        double rowHeight(double elementHeight);

        double tableHeight(int cardinality, double elementHeight);
    }

    public interface SchemaNodeLayout {

        double baseOutlineWidth(double available);

        double baseTableColumnWidth(double available);

        Control label(double labelWidth, String label, double height);

        double labelWidth(String label);

        double tableColumnWidth(double columnWidth);
    }

    private static final FontLoader FONT_LOADER = Toolkit.getToolkit()
                                                         .getFontLoader();

    private static final TextLayout LAYOUT      = Toolkit.getToolkit()
                                                         .getTextLayoutFactory()
                                                         .createLayout();

    private static final Insets     ZERO_INSETS = new Insets(0);

    public static Insets add(Insets a, Insets b) {
        return new Insets(a.getTop() + b.getTop(), a.getRight() + b.getRight(),
                          a.getBottom() + b.getBottom(),
                          a.getLeft() + b.getLeft());
    }

    public static double snap(double value) {
        return Math.ceil(value);
    }

    @SuppressWarnings("deprecation")
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

    private Insets                                listCellInsets = ZERO_INSETS;
    private Insets                                listInsets     = ZERO_INSETS;
    private final LayoutModel                     model;
    @SuppressWarnings("unused")
    private final Map<Primitive, PrimitiveLayout> primitives     = new HashMap<>();
    @SuppressWarnings("unused")
    private final Map<Relation, RelationLayout>   relations      = new HashMap<>();
    private List<String>                          styleSheets;

    private Font                                  textFont       = Font.getDefault();

    private Insets                                textInsets     = ZERO_INSETS;

    private double                                textLineHeight = 0;

    public Layout(LayoutModel model) {
        this(Collections.emptyList(), model, true);
    }

    public Layout(LayoutModel model, boolean initialize) {
        this(Collections.emptyList(), model, initialize);
    }

    public Layout(List<String> styleSheets, LayoutModel model) {
        this(styleSheets, model, true);
    }

    public Layout(List<String> styleSheets, LayoutModel model,
                  boolean initialize) {
        this.model = model;
        if (initialize) {
            initialize(styleSheets);
        }
    }

    public LayoutModel getModel() {
        return model;
    }

    public void initialize(List<String> styleSheets) {
        this.styleSheets = styleSheets;
        Label text = new Label("Lorem Ipsum");
        Label labelText = new Label("Lorem Ipsum");

        ListView<String> outlineList = new ListView<>();

        ListCell<String> outlineListCell = new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(item);
            };
        };
        outlineList.setCellFactory(s -> outlineListCell);

        VBox root = new VBox();
        root.getChildren()
            .addAll(outlineList, text, labelText);
        Scene scene = new Scene(root, 800, 600);
        if (styleSheets != null) {
            scene.getStylesheets()
                 .addAll(styleSheets);
        }
        text.applyCss();
        text.layout();

        labelText.applyCss();
        labelText.layout();

        ObservableList<String> listItems = outlineList.getItems();
        listItems.add("Lorem ipsum");
        outlineList.setItems(null);
        outlineList.setItems(listItems);
        outlineList.requestLayout();

        root.applyCss();
        root.layout();

        outlineList.applyCss();
        outlineList.layout();
        outlineList.refresh();
        outlineListCell.applyCss();
        outlineListCell.layout();

        listCellInsets = new Insets(outlineListCell.snappedTopInset(),
                                    outlineListCell.snappedRightInset(),
                                    outlineListCell.snappedBottomInset(),
                                    outlineListCell.snappedLeftInset());

        listInsets = new Insets(outlineList.snappedTopInset(),
                                outlineList.snappedRightInset(),
                                outlineList.snappedBottomInset(),
                                outlineList.snappedLeftInset());

        textFont = text.getFont();
        textLineHeight = snap(getLineHeight(textFont,
                                            TextBoundsType.LOGICAL_VERTICAL_CENTER))
                         + 1;
        textInsets = new Insets(3, 20, 3, 20);
    }

    public PrimitiveLayout layout(Primitive primitive) {
        return primitives.computeIfAbsent(primitive,
                                          p -> new PrimitiveLayoutImpl(this,
                                                                       p));
    }

    public RelationLayout layout(Relation relation) {
        return relations.computeIfAbsent(relation,
                                         r -> new RelationLayoutImpl(this, r));
    }

    @Override
    public String toString() {
        return String.format("Layout [model=%s\n listCellInsets=%s\n listInsets=%s\n styleSheets=%s\n textFont=%s\n textInsets=%s\n textLineHeight=%s]",
                             model, listCellInsets, listInsets, styleSheets,
                             textFont, textInsets, textLineHeight);
    }

    double baseTextWidth(double width) {
        return width - getTextHorizontalInset();
    }

    double getListCellVerticalInset() {
        return listCellInsets.getTop() + listCellInsets.getBottom();
    }

    double getListVerticalInset() {
        return listInsets.getTop() + listInsets.getBottom();
    }

    double getNestedInset() {
        return getNestedLeftInset() + getNestedRightInset();
    }

    double getTextLineHeight() {
        return textLineHeight;
    }

    double getTextVerticalInset() {
        return textInsets.getTop() + textInsets.getBottom();
    }

    Control label(double labelWidth, String label, double height) {
        Label labelText = new Label(label);
        labelText.setAlignment(Pos.CENTER);
        labelText.setMinWidth(labelWidth);
        labelText.setPrefHeight(height);
        labelText.setStyle("-fx-background-color: -fx-inner-border, -fx-body-color;\n"
                           + "    -fx-background-insets: 0, 1;");
        return labelText;
    }

    double labelWidth(String label) {
        return textWidth(label) + 20;
    }

    double textWidth(String text) {
        return snap(FONT_LOADER.computeStringWidth(String.format("%s", text),
                                                   textFont));
    }

    double totalTextWidth(double justifiedWidth) {
        return justifiedWidth + getTextHorizontalInset();
    }

    private double getNestedLeftInset() {
        return listInsets.getLeft() + listCellInsets.getLeft();
    }

    private double getNestedRightInset() {
        return listInsets.getRight() + listCellInsets.getRight();
    }

    private double getTextHorizontalInset() {
        return textInsets.getLeft() + textInsets.getRight();
    }
}
