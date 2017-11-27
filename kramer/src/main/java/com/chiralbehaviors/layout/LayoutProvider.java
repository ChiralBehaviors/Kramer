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

import com.chiralbehaviors.layout.flowless.Cell;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.outline.Outline;
import com.chiralbehaviors.layout.outline.OutlineCell;
import com.chiralbehaviors.layout.outline.OutlineColumn;
import com.chiralbehaviors.layout.outline.OutlineElement;
import com.chiralbehaviors.layout.outline.Span;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.table.NestedCell;
import com.chiralbehaviors.layout.table.NestedTable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.javafx.scene.text.TextLayout;
import com.sun.javafx.tk.FontLoader;
import com.sun.javafx.tk.Toolkit;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.TextBoundsType;
import javafx.util.Pair;

@SuppressWarnings("restriction")
public class LayoutProvider implements StyleProvider {

    public interface LayoutModel {

        default void apply(Cell<JsonNode, ?> list, Primitive p) {
        }

        default void apply(VirtualFlow<JsonNode, Cell<JsonNode, ?>> list,
                           Relation relation) {
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

    public static double relax(double value) {
        return Math.max(0, Math.floor(value) - 1);
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

    private Insets                                cellInsets     = ZERO_INSETS;
    private Insets                                insets         = ZERO_INSETS;
    private final LayoutModel                     model;
    private final Map<Primitive, PrimitiveLayout> primitives     = new HashMap<>();
    private final Map<Relation, RelationLayout>   relations      = new HashMap<>();
    private List<String>                          styleSheets;
    private Font                                  textFont       = Font.getDefault();
    private Insets                                textInsets     = ZERO_INSETS;
    private double                                textLineHeight = 0;

    public LayoutProvider(LayoutModel model) {
        this(Collections.emptyList(), model, true);
    }

    public LayoutProvider(LayoutModel model, boolean initialize) {
        this(Collections.emptyList(), model, initialize);
    }

    public LayoutProvider(List<String> styleSheets, LayoutModel model) {
        this(styleSheets, model, true);
    }

    public LayoutProvider(List<String> styleSheets, LayoutModel model,
                          boolean initialize) {
        this.model = model;
        if (initialize) {
            initialize(styleSheets);
        }
    }

    /* (non-Javadoc)
     * @see com.chiralbehaviors.layout.StyleProvider#getModel()
     */
    @Override
    public LayoutModel getModel() {
        return model;
    }

    public Pair<StyledInsets, StyledInsets> insets(RelationLayout layout) {
        VBox root = new VBox();

        NestedTable nestedTable = new NestedTable();
        NestedCell nestedCell = new NestedCell();

        Outline outline = new Outline();
        OutlineCell outlineCell = new OutlineCell();
        OutlineColumn outlineColumn = new OutlineColumn();
        OutlineElement element = new OutlineElement();
        Span span = new Span();

        root.getChildren()
            .addAll(nestedTable, nestedCell, outline, outlineCell,
                    outlineColumn, element, span);
        Scene scene = new Scene(root, 800, 600);
        if (styleSheets != null) {
            scene.getStylesheets()
                 .addAll(styleSheets);
        }
        nestedTable.applyCss();
        nestedTable.layout();

        nestedCell.applyCss();
        nestedCell.layout();

        outline.applyCss();
        outline.layout();

        outlineCell.applyCss();
        outlineCell.layout();

        outlineColumn.applyCss();
        outlineColumn.layout();

        element.applyCss();
        element.layout();

        span.applyCss();
        span.layout();

        return new Pair<>(new StyledInsets(insets(nestedTable),
                                           insets(nestedCell)),
                          new StyledInsets(insets(outline),
                                           insets(outlineCell)));
    }

    private Insets insets(Region region) {
        return new Insets(region.snappedTopInset(), region.snappedRightInset(),
                          region.snappedBottomInset(),
                          region.snappedLeftInset());
    }

    @Override
    public void initialize(List<String> styleSheets) {
        this.styleSheets = styleSheets;
        Label text = new Label("Lorem Ipsum");
        text.setStyle("-fx-background-color: " + "         rgba(0,0,0,0.08),"
                      + "        linear-gradient(#9a9a9a, #909090),"
                      + "        white 0%;"
                      + "    -fx-background-insets: 0 0 -1 0,0,1;"
                      + "    -fx-background-radius: 5,5,4;"
                      + "    -fx-padding: 3 30 3 30;"
                      + "    -fx-text-fill: #242d35;"
                      + "    -fx-font-size: 14px;");
        Label labelText = new Label("Lorem Ipsum");

        ObservableList<String> items = FXCollections.observableArrayList();

        VirtualFlow<String, ?> flow = VirtualFlow.createVertical(100, 100,
                                                                 items,
                                                                 color -> {
                                                                     return Cell.wrapNode(new Label(color));
                                                                 });

        VBox root = new VBox();
        root.getChildren()
            .addAll(flow, text, labelText);
        Scene scene = new Scene(root, 800, 600);
        if (styleSheets != null) {
            scene.getStylesheets()
                 .addAll(styleSheets);
        }
        text.applyCss();
        text.layout();

        labelText.applyCss();
        labelText.layout();
        for (int i = 0; i < 100; i++) {
            items.add("Lorem ipsum");
        }
        flow.requestLayout();

        root.applyCss();
        root.layout();

        flow.applyCss();
        flow.layout();

        insets = new Insets(flow.snappedTopInset(), flow.snappedRightInset(),
                            flow.snappedBottomInset(), flow.snappedLeftInset());

        textFont = text.getFont();
        textLineHeight = snap(getLineHeight(textFont,
                                            TextBoundsType.LOGICAL_VERTICAL_CENTER))
                         + 1;
        textInsets = text.getInsets();
    }

    /* (non-Javadoc)
     * @see com.chiralbehaviors.layout.StyleProvider#layout(com.chiralbehaviors.layout.schema.Primitive)
     */
    @Override
    public PrimitiveLayout layout(Primitive primitive) {
        return primitives.computeIfAbsent(primitive,
                                          p -> new PrimitiveLayout(this,
                                                                   primitive));
    }

    /* (non-Javadoc)
     * @see com.chiralbehaviors.layout.StyleProvider#layout(com.chiralbehaviors.layout.schema.Relation)
     */
    @Override
    public RelationLayout layout(Relation relation) {
        return relations.computeIfAbsent(relation,
                                         r -> new RelationLayout(this,
                                                                 relation));
    }

    /* (non-Javadoc)
     * @see com.chiralbehaviors.layout.StyleProvider#layout(com.chiralbehaviors.layout.schema.SchemaNode)
     */
    @Override
    public SchemaNodeLayout layout(SchemaNode node) {
        if (node instanceof Relation) {
            return layout((Relation) node);
        }
        return layout((Primitive) node);
    }

    @Override
    public String toString() {
        return String.format("Layout [model=%s\n listCellInsets=%s\n listInsets=%s\n styleSheets=%s\n textFont=%s\n textInsets=%s\n textLineHeight=%s]",
                             model, cellInsets, insets, styleSheets, textFont,
                             textInsets, textLineHeight);
    }

    double getTextHorizontalInset() {
        return textInsets.getLeft() + textInsets.getRight();
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
        labelText.setMaxWidth(labelWidth);
        labelText.setMinHeight(height);
        labelText.setMaxHeight(height);
        labelText.setStyle("-fx-background-color: -fx-inner-border, -fx-body-color;\n"
                           + "    -fx-background-insets: 0, 1;");
        return labelText;
    }

    double labelWidth(String label) {
        return textWidth(label) + 20;
    }

    double textWidth(String text) {
        return snap(FONT_LOADER.computeStringWidth(text, textFont));
    }

    double totalTextWidth(double justifiedWidth) {
        return LayoutProvider.snap(justifiedWidth + getTextHorizontalInset());
    }
}
