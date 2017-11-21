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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.chiralbehaviors.layout.LayoutCell;
import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.flowless.FlyAwayScrollPane;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * @author halhildebrand
 *
 */
public class NestedTable extends VBox implements LayoutCell<NestedTable> {
    private static final String DEFAULT_STYLE = "nested-table";

    public static List<JsonNode> itemsAsArray(JsonNode items) {
        List<JsonNode> itemArray = new ArrayList<>();
        SchemaNode.asArray(items)
                  .forEach(n -> itemArray.add(n));
        return itemArray;
    }

    private final ObservableList<JsonNode> items = FXCollections.observableArrayList();

    public NestedTable(int cardinality, RelationLayout layout) {
        setDefaultStyles(DEFAULT_STYLE);
        Region header = layout.buildColumnHeader();
        getChildren().addAll(header, buildRows(cardinality, layout));
        setMinWidth(layout.getJustifiedColumnWidth());
        setPrefWidth(layout.getJustifiedColumnWidth());
        setMaxWidth(layout.getJustifiedColumnWidth());
    }

    @Override
    public void updateItem(JsonNode item) {
        items.setAll(SchemaNode.asList(item));
    }

    protected Region buildRows(int card, RelationLayout layout) {

        double cellHeight = layout.baseRowCellHeight(layout.getRowHeight());
        VirtualFlow<JsonNode, NestedCell> rows = VirtualFlow.createVertical(layout.getJustifiedColumnWidth(),
                                                                            cellHeight,
                                                                            items,
                                                                            cell(cellHeight,
                                                                                 layout));

        double width = layout.getJustifiedColumnWidth();
        double height = snap(layout.getHeight()
                             - layout.getColumnHeaderHeight());

        rows.setMinSize(width, height);
        rows.setPrefSize(width, height);
        rows.setMaxSize(width, height);

        Region scroll = new FlyAwayScrollPane<>(rows);
        scroll.setMinSize(width, height);
        scroll.setPrefSize(width, height);
        scroll.setMaxSize(width, height);
        return scroll;
    }

    protected Function<? super JsonNode, ? extends NestedCell> cell(double cellHeight,
                                                                    RelationLayout layout) {
        return item -> {
            NestedCell cell = new NestedCell(cellHeight, layout);
            cell.updateItem(item);
            return cell;
        };
    }
}
