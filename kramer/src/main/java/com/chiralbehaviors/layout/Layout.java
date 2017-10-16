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

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sun.javafx.scene.text.TextLayout;
import com.sun.javafx.tk.FontLoader;
import com.sun.javafx.tk.Toolkit;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
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

        double measure(JsonNode content);
    }

    public interface SchemaNodeLayout {
        double getElementHeightInset();

        double getOutlineInset();

        double getRowHeightInset();

        double getTableInset();
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

    private Insets                                listCellInsets = ZERO_INSETS;
    private Insets                                listInsets     = ZERO_INSETS;
    private final LayoutModel                     model;
    @SuppressWarnings("unused")
    private final Map<Primitive, PrimitiveLayout> primitves      = new HashMap<>();
    @SuppressWarnings("unused")
    private final Map<Relation, SchemaNodeLayout> relations      = new HashMap<>();
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

    public double baseOutlineCellHeight(double cellHeight) {
        return cellHeight - getListCellVerticalInset();
    }

    public double baseOutlineWidth(double width) {
        return width - getNestedInset();
    }

    public double baseRowCellHeight(double extended) {
        return extended - getListCellVerticalInset();
    }

    public Double baseTableColumnWidth(double width) {
        return width - getNestedInset();
    }

    public Double baseTextWidth(double width) {
        return width - getTextHorizontalInset();
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

    public double outlineCellHeight(double baseHeight) {
        return baseHeight + getListCellVerticalInset();
    }

    public Double outlineHeight(int cardinality, double elementHeight) {
        return (cardinality * (elementHeight + getListCellVerticalInset()))
               + getListVerticalInset();
    }

    public double rowCellWidth(double width) {
        return width - getListCellVerticalInset();
    }

    public double rowHeight(double elementHeight) {
        return elementHeight + getListCellVerticalInset();
    }

    public void setItemsOf(Control control, JsonNode data) {
        if (data == null) {
            data = JsonNodeFactory.instance.arrayNode();
        }
        List<JsonNode> dataList = SchemaNode.asList(data);
        if (control instanceof ListView) {
            @SuppressWarnings("unchecked")
            ListView<JsonNode> listView = (ListView<JsonNode>) control;
            listView.getItems()
                    .setAll(dataList);
        } else if (control instanceof Label) {
            Label label = (Label) control;
            label.setText(SchemaNode.asText(data));
        } else if (control instanceof TextArea) {
            TextArea label = (TextArea) control;
            label.setText(SchemaNode.asText(data));
        } else if (control instanceof NestedTable) {
            ((NestedTable) control).setItems(data);
        } else {
            throw new IllegalArgumentException(String.format("Unknown control %s",
                                                             control));
        }
    }

    public Double tableHeight(int cardinality, double elementHeight) {
        return (cardinality * (elementHeight + getListCellVerticalInset()))
               + getListVerticalInset();
    }

    public Double textHeight(double rows) {
        return (getTextLineHeight() * rows) + getTextVerticalInset();
    }

    public double textWidth(String text) {
        return snap(FONT_LOADER.computeStringWidth(String.format("W%sW\n",
                                                                 text),
                                                   textFont));
    }

    @Override
    public String toString() {
        return String.format("Layout [model=%s\n listCellInsets=%s\n listInsets=%s\n styleSheets=%s\n textFont=%s\n textInsets=%s\n textLineHeight=%s]",
                             model, listCellInsets, listInsets, styleSheets,
                             textFont, textInsets, textLineHeight);
    }

    public double totalOutlineWidth(double outlineWidth) {
        return outlineWidth + getNestedInset();
    }

    public double totalTableColumnWidth(double tableColumnWidth) {
        return tableColumnWidth + getNestedInset();
    }

    public double totalTextWidth(double justifiedWidth) {
        return justifiedWidth + getTextHorizontalInset();
    }

    private double getListCellVerticalInset() {
        return listCellInsets.getTop() + listCellInsets.getBottom();
    }

    private double getListVerticalInset() {
        return listInsets.getTop() + listInsets.getBottom();
    }

    private double getNestedInset() {
        return getNestedLeftInset() + getNestedRightInset();
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

    private double getTextLineHeight() {
        return textLineHeight;
    }

    private double getTextVerticalInset() {
        return textInsets.getTop() + textInsets.getBottom();
    }
}
