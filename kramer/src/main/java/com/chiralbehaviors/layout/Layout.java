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

import java.lang.reflect.Field;
import java.util.List;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sun.javafx.scene.control.skin.TableHeaderRow;
import com.sun.javafx.scene.control.skin.TableViewSkinBase;
import com.sun.javafx.scene.control.skin.TextAreaSkin;
import com.sun.javafx.scene.text.TextLayout;
import com.sun.javafx.tk.FontLoader;
import com.sun.javafx.tk.Toolkit;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
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

    private Insets            listCellInsets = ZERO_INSETS;

    private Insets            listInsets     = ZERO_INSETS;
    private final LayoutModel model;
    private List<String>      styleSheets;
    private Insets            tableInsets    = ZERO_INSETS;
    private Insets            tableRowInsets;
    private Font              textFont       = Font.getDefault();
    private Insets            textInsets     = ZERO_INSETS;
    private double            textLineHeight = 0;

    public Layout(LayoutModel model) {
        this.model = model;
    }

    public Layout(List<String> styleSheets, LayoutModel model) {
        this(model);
        initialize(styleSheets);
    }

    public double getListCellHorizontalInset() {
        return listCellInsets.getLeft() + listCellInsets.getRight();
    }

    public double getListCellVerticalInset() {
        return listCellInsets.getTop() + listCellInsets.getBottom();
    }

    public double getListHorizontalInset() {
        return listInsets.getLeft() + listInsets.getRight();
    }

    public double getListVerticalInset() {
        return listInsets.getTop() + listInsets.getBottom();
    }

    public LayoutModel getModel() {
        return model;
    }

    public double getNestedInset() {
        return getNestedLeftInset() + getNestedRightInset();
    }

    public double getNestedLeftInset() {
        return listInsets.getLeft() + listCellInsets.getLeft();
    }

    public double getNestedRightInset() {
        return listInsets.getRight() + listCellInsets.getRight();
    }

    public double getTableHorizontalInset() {
        return tableInsets.getLeft() + tableInsets.getRight();
    }

    public double getTableRowHorizontalInset() {
        return tableRowInsets.getLeft() + tableRowInsets.getRight();
    }

    public double getTableRowVerticalInset() {
        return tableRowInsets.getTop() + tableRowInsets.getBottom();
    }

    public double getTableVerticalInset() {
        return tableInsets.getTop() + tableInsets.getBottom();
    }

    public double getTextHorizontalInset() {
        return textInsets.getLeft() + textInsets.getRight();
    }

    public double getTextLineHeight() {
        return textLineHeight;
    }

    public double getTextVerticalInset() {
        return textInsets.getTop() + textInsets.getBottom();
    }

    public void initialize(List<String> styleSheets) {
        this.styleSheets = styleSheets;
        TextArea text = new TextArea("Lorem Ipsum");
        TextArea labelText = new TextArea("Lorem Ipsum");

        ListView<String> outlineList = new ListView<>();

        ListCell<String> outlineListCell = new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(item);
            };
        };
        outlineList.setCellFactory(s -> outlineListCell);

        TableCell<String, String> tableCell = new TableCell<String, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(item);
            }
        };

        TableView<String> table = new TableView<>();

        TableRow<String> tableRow = new TableRow<>();
        table.setRowFactory(v -> tableRow);

        TableColumn<String, String> column = new TableColumn<>("");
        column.setCellFactory(c -> tableCell);
        column.setCellValueFactory(s -> new SimpleStringProperty(s.getValue()));

        table.getColumns()
             .add(column);

        VBox root = new VBox();
        root.getChildren()
            .addAll(table, outlineList, text, labelText);
        Scene scene = new Scene(root, 800, 600);
        if (styleSheets != null) {
            scene.getStylesheets()
                 .addAll(styleSheets);
        }
        text.applyCss();
        text.layout();

        labelText.applyCss();
        labelText.layout();

        ObservableList<String> tableItems = table.getItems();
        tableItems.add("Lorem ipsum");
        table.setItems(null);
        table.setItems(tableItems);

        ObservableList<String> listItems = outlineList.getItems();
        listItems.add("Lorem ipsum");
        outlineList.setItems(null);
        outlineList.setItems(listItems);
        outlineList.requestLayout();

        root.applyCss();
        root.layout();
        table.applyCss();
        table.layout();
        table.refresh();
        tableRow.applyCss();
        tableRow.layout();

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

        tableInsets = new Insets(table.snappedTopInset(),
                                 table.snappedRightInset(),
                                 table.snappedBottomInset(),
                                 table.snappedLeftInset());

        tableRowInsets = new Insets(tableRow.snappedTopInset(),
                                    tableRow.snappedRightInset(),
                                    tableRow.snappedBottomInset(),
                                    tableRow.snappedLeftInset());

        textFont = text.getFont();
        textLineHeight = snap(getLineHeight(textFont,
                                            TextBoundsType.LOGICAL_VERTICAL_CENTER))
                         + 1;
        Field contentField;
        try {
            contentField = TextAreaSkin.class.getDeclaredField("contentView");
        } catch (NoSuchFieldException | SecurityException e) {
            throw new IllegalStateException(e);
        }
        contentField.setAccessible(true);
        Region content;
        try {
            content = (Region) contentField.get(text.getSkin());
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        textInsets = new Insets(content.snappedTopInset(),
                                content.snappedRightInset(),
                                content.snappedBottomInset(),
                                content.snappedLeftInset());
    }

    public double measureHeader(TableView<?> table) {
        Group root = new Group(table);
        Scene scene = new Scene(root);
        if (styleSheets != null) {
            scene.getStylesheets()
                 .addAll(styleSheets);
        }
        root.applyCss();
        root.layout();
        table.applyCss();
        table.layout();
        @SuppressWarnings("rawtypes")
        TableHeaderRow headerRow = ((TableViewSkinBase) table.getSkin()).getTableHeaderRow();
        root.getChildren()
            .clear();
        return headerRow.getHeight();
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
        } else if (control instanceof TableView) {
            @SuppressWarnings("unchecked")
            TableView<JsonNode> tableView = (TableView<JsonNode>) control;
            tableView.getItems()
                     .setAll(dataList);
        } else if (control instanceof Label) {
            Label label = (Label) control;
            label.setText(SchemaNode.asText(data));
        } else if (control instanceof TextArea) {
            TextArea label = (TextArea) control;
            label.setText(SchemaNode.asText(data));
        } else {
            throw new IllegalArgumentException(String.format("Unknown control %s",
                                                             control));
        }
    }

    public void setListCellInsets(Insets listCellInsets) {
        this.listCellInsets = listCellInsets;
    }

    public void setListInsets(Insets listInsets) {
        this.listInsets = listInsets;
    }

    public void setTableInsets(Insets tableInsets) {
        this.tableInsets = tableInsets;
    }

    public void setTableRowInsets(Insets tableRowInsets) {
        this.tableRowInsets = tableRowInsets;
    }

    public void setTextFont(Font textFont) {
        this.textFont = textFont;
    }

    public void setTextInsets(Insets textInsets) {
        this.textInsets = textInsets;
    }

    public void setTextLineHeight(double textLineHeight) {
        this.textLineHeight = textLineHeight;
    }

    public double snap(double value) {
        return Math.ceil(value);
    }

    public double textDoubleSpaceWidth() {
        return FONT_LOADER.computeStringWidth("WW", textFont);
    }

    public double textWidth(String text) {
        return snap(FONT_LOADER.computeStringWidth(String.format("W%sW\n",
                                                                 text),
                                                   textFont));
    }

    @Override
    public String toString() {
        return String.format("Layout [model=%s\n listCellInsets=%s\n listInsets=%s\n styleSheets=%s\n tableInsets=%s\n tableRowInsets=%s\n textFont=%s\n textInsets=%s\n textLineHeight=%s]",
                             model, listCellInsets, listInsets, styleSheets,
                             tableInsets, tableRowInsets, textFont, textInsets,
                             textLineHeight);
    }
}
