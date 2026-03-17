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

import com.chiralbehaviors.layout.SchemaPath;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;

/**
 * Factory for building JavaFX context menus that dispatch
 * {@link LayoutInteraction} events via an {@link InteractionHandler}.
 * <p>
 * Context menus are built per-{@link SchemaPath} and include actions
 * for sort, filter, visibility, and render mode.
 *
 * @author hhildebrand
 */
public final class InteractionMenuFactory {

    private final InteractionHandler handler;
    private final LayoutQueryState queryState;

    public InteractionMenuFactory(InteractionHandler handler,
                                   LayoutQueryState queryState) {
        this.handler = handler;
        this.queryState = queryState;
    }

    /**
     * Build a context menu for a Primitive field (leaf node).
     */
    public ContextMenu buildPrimitiveMenu(SchemaPath path) {
        var menu = new ContextMenu();
        String fieldName = path.leaf();

        // Sort
        menu.getItems().add(menuItem("Sort ascending", () ->
            handler.apply(new LayoutInteraction.SortBy(path, false))));
        menu.getItems().add(menuItem("Sort descending", () ->
            handler.apply(new LayoutInteraction.SortBy(path, true))));

        menu.getItems().add(new SeparatorMenuItem());

        // Visibility
        boolean visible = queryState.getVisibleOrDefault(path);
        menu.getItems().add(menuItem(visible ? "Hide" : "Show", () ->
            handler.apply(new LayoutInteraction.ToggleVisible(path))));

        menu.getItems().add(new SeparatorMenuItem());

        // Filter
        FieldState fs = queryState.getFieldState(path);
        if (fs.filterExpression() != null) {
            menu.getItems().add(menuItem("Clear filter", () ->
                handler.apply(new LayoutInteraction.ClearFilter(path))));
        }
        menu.getItems().add(menuItem("Filter...", () -> {
            promptExpression("Filter expression for " + fieldName,
                fs.filterExpression())
                .ifPresent(expr ->
                    handler.apply(new LayoutInteraction.SetFilter(path, expr)));
        }));

        // Formula
        if (fs.formulaExpression() != null) {
            menu.getItems().add(menuItem("Clear formula", () ->
                handler.apply(new LayoutInteraction.ClearFormula(path))));
        }
        menu.getItems().add(menuItem("Formula...", () -> {
            promptExpression("Formula for " + fieldName,
                fs.formulaExpression())
                .ifPresent(expr ->
                    handler.apply(new LayoutInteraction.SetFormula(path, expr)));
        }));

        menu.getItems().add(new SeparatorMenuItem());

        // Reset
        menu.getItems().add(menuItem("Reset all", () ->
            handler.apply(new LayoutInteraction.ResetAll())));

        return menu;
    }

    /**
     * Build a context menu for a Relation node (interior node).
     */
    public ContextMenu buildRelationMenu(SchemaPath path) {
        var menu = new ContextMenu();

        // Render mode
        menu.getItems().add(menuItem("Show as table", () ->
            handler.apply(new LayoutInteraction.SetRenderMode(path, "TABLE"))));
        menu.getItems().add(menuItem("Show as outline", () ->
            handler.apply(new LayoutInteraction.SetRenderMode(path, "OUTLINE"))));

        menu.getItems().add(new SeparatorMenuItem());

        // Visibility
        boolean visible = queryState.getVisibleOrDefault(path);
        menu.getItems().add(menuItem(visible ? "Hide" : "Show", () ->
            handler.apply(new LayoutInteraction.ToggleVisible(path))));

        // Filter
        FieldState fs = queryState.getFieldState(path);
        if (fs.filterExpression() != null) {
            menu.getItems().add(menuItem("Clear filter", () ->
                handler.apply(new LayoutInteraction.ClearFilter(path))));
        }
        menu.getItems().add(menuItem("Filter...", () -> {
            promptExpression("Filter expression",
                fs.filterExpression())
                .ifPresent(expr ->
                    handler.apply(new LayoutInteraction.SetFilter(path, expr)));
        }));

        menu.getItems().add(new SeparatorMenuItem());

        // Reset
        menu.getItems().add(menuItem("Reset all", () ->
            handler.apply(new LayoutInteraction.ResetAll())));

        return menu;
    }

    private static MenuItem menuItem(String text, Runnable action) {
        var item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    private static java.util.Optional<String> promptExpression(
            String title, String currentValue) {
        var dialog = new TextInputDialog(currentValue != null ? currentValue : "");
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText("Expression:");
        return dialog.showAndWait();
    }
}
