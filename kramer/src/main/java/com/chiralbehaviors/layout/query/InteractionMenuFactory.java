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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

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
    private PaginationContext paginationContext = PaginationContext.NONE;

    public InteractionMenuFactory(InteractionHandler handler,
                                   LayoutQueryState queryState) {
        this.handler = handler;
        this.queryState = queryState;
    }

    /** Set the pagination context. Affects sort menu item labels. */
    public void setPaginationContext(PaginationContext ctx) {
        this.paginationContext = ctx != null ? ctx : PaginationContext.NONE;
    }

    public PaginationContext getPaginationContext() {
        return paginationContext;
    }

    /**
     * Build a context menu for a Primitive field (leaf node).
     */
    public ContextMenu buildPrimitiveMenu(SchemaPath path) {
        var menu = new ContextMenu();
        String fieldName = path.leaf();
        String sortSuffix = paginationContext.isPageLocal() ? " (page only)" : "";
        FieldState fs = queryState.getFieldState(path);
        boolean frozen = Boolean.TRUE.equals(fs.frozen());

        // Sort
        var sortAsc = menuItem("Sort ascending" + sortSuffix + "  \u2318\u2191", () ->
            handler.apply(new LayoutInteraction.SortBy(path, false)));
        var sortDesc = menuItem("Sort descending" + sortSuffix + "  \u2318\u2193", () ->
            handler.apply(new LayoutInteraction.SortBy(path, true)));
        sortAsc.setDisable(frozen);
        sortDesc.setDisable(frozen);
        menu.getItems().addAll(sortAsc, sortDesc);
        if (fs.sortFields() != null && !fs.sortFields().isEmpty()) {
            var clearSort = menuItem("Clear sort", () ->
                handler.apply(new LayoutInteraction.ClearSort(path)));
            clearSort.setDisable(frozen);
            menu.getItems().add(clearSort);
        }

        menu.getItems().add(new SeparatorMenuItem());

        // Visibility
        boolean visible = queryState.getVisibleOrDefault(path);
        var visItem = menuItem(visible ? "Hide" : "Show", () ->
            handler.apply(new LayoutInteraction.ToggleVisible(path)));
        visItem.setDisable(frozen);
        menu.getItems().add(visItem);

        menu.getItems().add(new SeparatorMenuItem());

        // Filter
        if (fs.filterExpression() != null) {
            var clearFilter = menuItem("Clear filter", () ->
                handler.apply(new LayoutInteraction.ClearFilter(path)));
            clearFilter.setDisable(frozen);
            menu.getItems().add(clearFilter);
        }
        var filterItem = menuItem("Filter...", () -> {
            promptExpression("Filter expression for " + fieldName,
                fs.filterExpression())
                .ifPresent(expr -> {
                    if (expr.isBlank()) {
                        handler.apply(new LayoutInteraction.ClearFilter(path));
                    } else {
                        handler.apply(new LayoutInteraction.SetFilter(path, expr));
                    }
                });
        });
        filterItem.setDisable(frozen);
        menu.getItems().add(filterItem);

        // Formula
        if (fs.formulaExpression() != null) {
            var clearFormula = menuItem("Clear formula", () ->
                handler.apply(new LayoutInteraction.ClearFormula(path)));
            clearFormula.setDisable(frozen);
            menu.getItems().add(clearFormula);
        }
        var formulaItem = menuItem("Formula...", () -> {
            promptExpression("Formula for " + fieldName,
                fs.formulaExpression())
                .ifPresent(expr -> {
                    if (expr.isBlank()) {
                        handler.apply(new LayoutInteraction.ClearFormula(path));
                    } else {
                        handler.apply(new LayoutInteraction.SetFormula(path, expr));
                    }
                });
        });
        formulaItem.setDisable(frozen);
        menu.getItems().add(formulaItem);

        // Aggregate
        if (fs.aggregateExpression() != null) {
            var clearAggregate = menuItem("Clear aggregate", () ->
                handler.apply(new LayoutInteraction.ClearAggregate(path)));
            clearAggregate.setDisable(frozen);
            menu.getItems().add(clearAggregate);
        }
        var aggregateItem = menuItem("Aggregate...", () -> {
            promptExpression("Aggregate for " + fieldName,
                fs.aggregateExpression())
                .ifPresent(expr -> {
                    if (expr.isBlank()) {
                        handler.apply(new LayoutInteraction.ClearAggregate(path));
                    } else {
                        handler.apply(new LayoutInteraction.SetAggregate(path, expr));
                    }
                });
        });
        aggregateItem.setDisable(frozen);
        menu.getItems().add(aggregateItem);

        // Quick aggregate shortcuts
        var sumItem = menuItem("Add SUM", () ->
            handler.apply(new LayoutInteraction.SetAggregate(path, "sum($" + fieldName + ")")));
        var countItem = menuItem("Add COUNT", () ->
            handler.apply(new LayoutInteraction.SetAggregate(path, "count($" + fieldName + ")")));
        sumItem.setDisable(frozen);
        countItem.setDisable(frozen);
        menu.getItems().addAll(sumItem, countItem);

        menu.getItems().add(new SeparatorMenuItem());

        // Reset
        menu.getItems().add(menuItem("Reset all", () ->
            handler.apply(new LayoutInteraction.ResetAll())));

        return menu;
    }

    /**
     * Build a primitive context menu with cell-specific items. When
     * {@code cellValue} is non-null, adds "Filter by this value" and
     * "Copy value" items.
     */
    public ContextMenu buildPrimitiveMenu(SchemaPath path, String cellValue) {
        var menu = buildPrimitiveMenu(path);
        if (cellValue == null || cellValue.isEmpty()) return menu;

        boolean frozen = Boolean.TRUE.equals(queryState.getFieldState(path).frozen());
        var items = menu.getItems();

        // Insert cell-specific items before "Reset all"
        int resetIdx = -1;
        for (int i = 0; i < items.size(); i++) {
            if ("Reset all".equals(items.get(i).getText())) {
                resetIdx = i;
                break;
            }
        }
        if (resetIdx < 0) resetIdx = items.size();

        String displayValue = cellValue.length() > 30
            ? cellValue.substring(0, 27) + "..."
            : cellValue;

        var filterByValue = menuItem("Filter by \"" + displayValue + "\"", () ->
            handler.apply(new LayoutInteraction.SetFilter(path, cellValue)));
        filterByValue.setDisable(frozen);

        var copyValue = menuItem("Copy value", () -> {
            var content = new ClipboardContent();
            content.putString(cellValue);
            Clipboard.getSystemClipboard().setContent(content);
        });

        items.add(resetIdx, new SeparatorMenuItem());
        items.add(resetIdx, copyValue);
        items.add(resetIdx, filterByValue);

        return menu;
    }

    /**
     * Build a context menu for a Relation node (interior node).
     */
    public ContextMenu buildRelationMenu(SchemaPath path) {
        var menu = new ContextMenu();
        FieldState fs = queryState.getFieldState(path);
        boolean frozen = Boolean.TRUE.equals(fs.frozen());

        // Render mode
        var tableItem = menuItem("Show as table", () ->
            handler.apply(new LayoutInteraction.SetRenderMode(path, "TABLE")));
        var outlineItem = menuItem("Show as outline", () ->
            handler.apply(new LayoutInteraction.SetRenderMode(path, "OUTLINE")));
        tableItem.setDisable(frozen);
        outlineItem.setDisable(frozen);
        menu.getItems().addAll(tableItem, outlineItem);

        menu.getItems().add(new SeparatorMenuItem());

        // Visibility
        boolean visible = queryState.getVisibleOrDefault(path);
        var visItem = menuItem(visible ? "Hide" : "Show", () ->
            handler.apply(new LayoutInteraction.ToggleVisible(path)));
        visItem.setDisable(frozen);
        menu.getItems().add(visItem);

        // Filter
        if (fs.filterExpression() != null) {
            var clearFilter = menuItem("Clear filter", () ->
                handler.apply(new LayoutInteraction.ClearFilter(path)));
            clearFilter.setDisable(frozen);
            menu.getItems().add(clearFilter);
        }
        var filterItem = menuItem("Filter...", () -> {
            promptExpression("Filter expression",
                fs.filterExpression())
                .ifPresent(expr -> {
                    if (expr.isBlank()) {
                        handler.apply(new LayoutInteraction.ClearFilter(path));
                    } else {
                        handler.apply(new LayoutInteraction.SetFilter(path, expr));
                    }
                });
        });
        filterItem.setDisable(frozen);
        menu.getItems().add(filterItem);

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
        return ExpressionEditor.showDialog(title, currentValue);
    }
}
