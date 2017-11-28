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

package com.chiralbehaviors.layout.table;

import static com.chiralbehaviors.layout.LayoutProvider.snap;
import static javafx.scene.input.KeyCode.PAGE_DOWN;
import static javafx.scene.input.KeyCode.PAGE_UP;
import static org.fxmisc.wellbehaved.event.EventPattern.keyPressed;
import static org.fxmisc.wellbehaved.event.EventPattern.mouseClicked;
import static org.fxmisc.wellbehaved.event.template.InputMapTemplate.consume;
import static org.fxmisc.wellbehaved.event.template.InputMapTemplate.sequence;
import static org.fxmisc.wellbehaved.event.template.InputMapTemplate.unless;

import java.util.ArrayList;
import java.util.List;

import org.fxmisc.wellbehaved.event.template.InputMapTemplate;

import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.cell.FocusTraversal;
import com.chiralbehaviors.layout.cell.VerticalCell;
import com.chiralbehaviors.layout.flowless.FlyAwayScrollPane;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.flowless.VirtualFlowHit;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.input.InputEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;

/**
 * @author halhildebrand
 *
 */
public class NestedTable extends VerticalCell<NestedTable> {
    private static final InputMapTemplate<NestedTable, InputEvent> DEFAULT_INPUT_MAP;
    private static final String                                    DEFAULT_STYLE         = "nested-table";
    private static final String                                    SCHEMA_CLASS_TEMPLATE = "%s-nested-table";
    private static final String                                    STYLE_SHEET           = "nested-table.css";

    static {
        DEFAULT_INPUT_MAP = unless(Node::isDisabled,
                                   sequence(consume(mouseClicked(MouseButton.PRIMARY),
                                                    (table,
                                                     evt) -> table.select(evt)),
                                            consume(keyPressed(PAGE_UP),
                                                    (table,
                                                     evt) -> table.scrollUp()),
                                            consume(keyPressed(PAGE_DOWN),
                                                    (table,
                                                     evt) -> table.scrollDown())));
    }

    public static List<JsonNode> itemsAsArray(JsonNode items) {
        List<JsonNode> itemArray = new ArrayList<>();
        SchemaNode.asArray(items)
                  .forEach(n -> itemArray.add(n));
        return itemArray;
    }

    private final FocusTraversal                    focus;
    private final VirtualFlow<JsonNode, NestedCell> rows;
    {
        focus = new FocusTraversal() {

            @Override
            protected Node getNode() {
                return NestedTable.this;
            }

        };
    }

    public NestedTable(int childCardinality, RelationLayout layout) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        getStyleClass().add(String.format(SCHEMA_CLASS_TEMPLATE,
                                          layout.getField()));
        InputMapTemplate.installFallback(DEFAULT_INPUT_MAP, this);
        Region header = layout.buildColumnHeader();
        double width = layout.getJustifiedColumnWidth();
        double height = snap(layout.getHeight()
                             - layout.getColumnHeaderHeight());

        rows = buildRows(width, height, childCardinality, layout);
        Region scroll = new FlyAwayScrollPane<>(rows);
        scroll.setMinSize(width, height);
        scroll.setPrefSize(width, height);
        scroll.setMaxSize(width, height);
        getChildren().addAll(header, scroll);
        setMinWidth(layout.getJustifiedColumnWidth());
        setPrefWidth(layout.getJustifiedColumnWidth());
        setMaxWidth(layout.getJustifiedColumnWidth());
    }

    public NestedTable(String field) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        getStyleClass().add(String.format(SCHEMA_CLASS_TEMPLATE, field));
        this.rows = null;
    }

    @Override
    public void dispose() {
        focus.unbind();
    }

    @Override
    public void reset() {
        focus.unbind();
    }

    @Override
    public void updateItem(JsonNode item) {
        rows.getItems()
            .setAll(SchemaNode.asList(item));
    }

    protected VirtualFlow<JsonNode, NestedCell> buildRows(double width,
                                                          double height,
                                                          int childCardinality,
                                                          RelationLayout layout) {
        VirtualFlow<JsonNode, NestedCell> rows = VirtualFlow.createVertical(layout.getJustifiedColumnWidth(),
                                                                            layout.getRowHeight(),
                                                                            FXCollections.observableArrayList(),
                                                                            item -> {
                                                                                NestedCell cell = new NestedCell(layout);
                                                                                cell.updateItem(item);
                                                                                return cell;
                                                                            });
        rows.setMinSize(width, height);
        rows.setPrefSize(width, height);
        rows.setMaxSize(width, height);
        return rows;
    }

    protected void scrollDown() {
        // TODO Auto-generated method stub
    }

    protected void scrollUp() {
        // TODO Auto-generated method stub
    }

    protected void select(MouseEvent evt) {
        VirtualFlowHit<NestedCell> hit = rows.hit(evt.getX(), evt.getY());
        if (hit.isCellHit()) {
            NestedCell node = hit.getCell()
                                 .getNode();
            node.setFocus(true);
            node.setExternalFocus(false);

        }
    }
}
