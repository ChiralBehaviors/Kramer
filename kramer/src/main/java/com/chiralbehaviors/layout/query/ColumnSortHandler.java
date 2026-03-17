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

package com.chiralbehaviors.layout.query;

import java.util.List;

import com.chiralbehaviors.layout.SchemaNodeLayout;
import com.chiralbehaviors.layout.SchemaPath;

import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;

/**
 * Attaches click-to-sort behavior to a {@code TableHeader} (HBox of
 * ColumnHeader nodes). Clicking a column header cycles through:
 * no sort → ascending → descending → no sort.
 * <p>
 * The handler uses the mouse X coordinate to identify which column was
 * clicked by iterating child bounds. Sort state is read from and written
 * to the {@link LayoutQueryState} via {@link InteractionHandler}.
 *
 * @author hhildebrand
 */
public final class ColumnSortHandler {

    private final InteractionHandler handler;
    private final LayoutQueryState queryState;

    public ColumnSortHandler(InteractionHandler handler,
                              LayoutQueryState queryState) {
        this.handler = handler;
        this.queryState = queryState;
    }

    /**
     * Install click-to-sort on a TableHeader HBox. The {@code childPaths}
     * list must correspond 1:1 with the TableHeader's children (the
     * ColumnHeader nodes).
     *
     * @param header     the TableHeader HBox
     * @param childPaths schema paths for each column, in order
     */
    public void install(HBox header, List<SchemaPath> childPaths) {
        header.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;

            int columnIndex = hitColumn(header, event.getX());
            if (columnIndex < 0 || columnIndex >= childPaths.size()) return;

            SchemaPath path = childPaths.get(columnIndex);
            cycleSortState(path);
            event.consume();
        });
    }

    /**
     * Install click-to-sort using SchemaNodeLayout children to derive paths.
     * The parent path is prepended to each child's field name.
     *
     * @param header     the TableHeader HBox
     * @param parentPath the parent Relation's schema path
     * @param children   the layout children (in column order)
     */
    public void install(HBox header, SchemaPath parentPath,
                        List<SchemaNodeLayout> children) {
        var paths = children.stream()
            .map(c -> parentPath.child(c.getField()))
            .toList();
        install(header, paths);
    }

    /**
     * Cycle sort state: no sort → ascending → descending → no sort.
     */
    private void cycleSortState(SchemaPath path) {
        String current = queryState.getFieldState(path).sortFields();
        String fieldName = path.leaf();

        if (current == null || current.isEmpty()) {
            // No sort → ascending
            handler.apply(new LayoutInteraction.SortBy(path, false));
        } else if (current.equals(fieldName)) {
            // Ascending → descending
            handler.apply(new LayoutInteraction.SortBy(path, true));
        } else if (current.equals("-" + fieldName)) {
            // Descending → clear sort
            // Bypasses InteractionHandler — LayoutInteraction has no ClearSort variant.
            queryState.setSortFields(path, null);
        } else {
            // Unknown sort state → clear
            queryState.setSortFields(path, null);
        }
    }

    /**
     * Determine which column index was clicked by testing each child's bounds.
     */
    private static int hitColumn(HBox header, double mouseX) {
        List<Node> children = header.getChildren();
        for (int i = 0; i < children.size(); i++) {
            Node child = children.get(i);
            double left = child.getBoundsInParent().getMinX();
            double right = child.getBoundsInParent().getMaxX();
            if (mouseX >= left && mouseX <= right) {
                return i;
            }
        }
        return -1;
    }
}
