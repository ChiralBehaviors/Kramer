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
import com.fasterxml.jackson.databind.node.ArrayNode;
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
        Double cellHeight(double maxWidth, double justified);

        double width(JsonNode row);

    }

    public interface RelationLayout extends SchemaNodeLayout {

        double baseOutlineCellHeight(double cellHeight);

        double baseRowCellHeight(double extended);

        double outlineCellHeight(double baseHeight);

        double outlineHeight(int cardinality, double elementHeight);

        double outlineWidth(double outlineWidth);

        double rowHeight(double elementHeight);

        double tableHeight(int cardinality, double elementHeight);
    }

    public interface SchemaNodeLayout {

        double baseOutlineWidth(double available);

        double baseTableColumnWidth(double available);

        Control buildControl(int cardinality);

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

    private static String toString(JsonNode value) {
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

    public PrimitiveLayout layout(Primitive primitive) {
        return primitives.computeIfAbsent(primitive, p -> getLayout(p));
    }

    public RelationLayout layout(Relation relation) {
        return relations.computeIfAbsent(relation, r -> getLayout(r));
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

    @Override
    public String toString() {
        return String.format("Layout [model=%s\n listCellInsets=%s\n listInsets=%s\n styleSheets=%s\n textFont=%s\n textInsets=%s\n textLineHeight=%s]",
                             model, listCellInsets, listInsets, styleSheets,
                             textFont, textInsets, textLineHeight);
    }

    private PrimitiveLayout getLayout(Primitive p) {
        return new PrimitiveLayout() {

            @Override
            public double baseOutlineWidth(double available) {
                return baseTextWidth(available);
            }

            @Override
            public double baseTableColumnWidth(double width) {
                return baseTextWidth(width);
            }

            @Override
            public Label buildControl(int cardinality) {
                Label text = new Label();
                text.setWrapText(true);
                text.setStyle("-fx-background-color: "
                              + "         rgba(0,0,0,0.08),"
                              + "        linear-gradient(#9a9a9a, #909090),"
                              + "        white 0%;"
                              + "    -fx-background-insets: 0 0 -1 0,0,1;"
                              + "    -fx-background-radius: 5,5,4;"
                              + "    -fx-padding: 3 30 3 30;"
                              + "    -fx-text-fill: #242d35;"
                              + "    -fx-font-size: 14px;");
                return text;
            }

            @Override
            public Double cellHeight(double maxWidth, double justified) {
                double rows = Math.ceil((maxWidth / justified) + 0.5);
                return (getTextLineHeight() * rows) + getTextVerticalInset();
            }

            @Override
            public double labelWidth(String label) {
                return textWidth(label);
            }

            @Override
            public double tableColumnWidth(double width) {
                return totalTextWidth(width);
            }

            @Override
            public double width(JsonNode row) {
                return textWidth(Layout.toString(row));
            }
        };
    }

    private RelationLayout getLayout(Relation r) {
        return new RelationLayout() {

            @Override
            public double baseOutlineCellHeight(double cellHeight) {
                return cellHeight - getListCellVerticalInset();
            }

            @Override
            public double baseOutlineWidth(double width) {
                return width - getNestedInset();
            }

            @Override
            public double baseRowCellHeight(double extended) {
                return extended - getListCellVerticalInset();
            }

            @Override
            public double baseTableColumnWidth(double width) {
                return width - getNestedInset();
            }

            @Override
            public Control buildControl(int cardinality) {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public double labelWidth(String label) {
                return totalTextWidth(textWidth(label));
            }

            @Override
            public double outlineCellHeight(double baseHeight) {
                return baseHeight + getListCellVerticalInset();
            }

            @Override
            public double outlineHeight(int cardinality, double elementHeight) {
                return (cardinality
                        * (elementHeight + getListCellVerticalInset()))
                       + getListVerticalInset();
            }

            @Override
            public double outlineWidth(double outlineWidth) {
                return outlineWidth + getNestedInset();
            }

            @Override
            public double rowHeight(double elementHeight) {
                return elementHeight + getListCellVerticalInset();
            }

            @Override
            public double tableColumnWidth(double width) {
                return width + getNestedInset();
            }

            @Override
            public double tableHeight(int cardinality, double elementHeight) {
                return (cardinality
                        * (elementHeight + getListCellVerticalInset()))
                       + getListVerticalInset();
            }
        };
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

    private double textWidth(String text) {
        return snap(FONT_LOADER.computeStringWidth(String.format("W%sW\n",
                                                                 text),
                                                   textFont));
    }

    private double totalTextWidth(double justifiedWidth) {
        return justifiedWidth + getTextHorizontalInset();
    }
}
