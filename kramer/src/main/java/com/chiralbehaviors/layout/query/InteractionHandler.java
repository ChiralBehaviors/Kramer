// SPDX-License-Identifier: Apache-2.0
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

import java.util.ArrayDeque;
import java.util.Deque;

import com.chiralbehaviors.layout.SchemaPath;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Applies {@link LayoutInteraction} events to a {@link LayoutQueryState}.
 * <p>
 * Each call to {@link #apply(LayoutInteraction)} dispatches via a pattern-matching
 * switch and delegates to the appropriate typed setter on the query state.
 * Exactly one change notification fires per call (delegated to the state).
 * <p>
 * Supports linear undo/redo via {@link LayoutQueryState#toJson()}/{@link LayoutQueryState#fromJson(ObjectNode)}
 * snapshots. See RDR-031.
 *
 * @author hhildebrand
 */
public final class InteractionHandler {

    private static final int MAX_UNDO_DEPTH = 50;

    private final LayoutQueryState queryState;
    private final Deque<ObjectNode> undoStack = new ArrayDeque<>();
    private final Deque<ObjectNode> redoStack = new ArrayDeque<>();

    public InteractionHandler(LayoutQueryState queryState) {
        this.queryState = queryState;
    }

    /**
     * Dispatch the given interaction event to the underlying {@link LayoutQueryState}.
     * Snapshots state before mutation for undo support.
     *
     * @param event the event to apply; must not be {@code null}
     */
    public void apply(LayoutInteraction event) {
        // Frozen guard: block all mutations on frozen fields (ResetAll exempt)
        if (!(event instanceof LayoutInteraction.ResetAll)) {
            SchemaPath path = eventPath(event);
            if (path != null) {
                Boolean frozen = queryState.getFieldState(path).frozen();
                if (Boolean.TRUE.equals(frozen)) return;
            }
        }

        // Snapshot before mutation (after frozen guard to avoid no-op snapshots)
        pushUndo();

        switch (event) {
            case LayoutInteraction.SortBy(var path, var desc) ->
                queryState.setSortFields(path, desc ? "-" + path.leaf() : path.leaf());

            case LayoutInteraction.ClearSort(var path) ->
                queryState.setSortFields(path, null);

            case LayoutInteraction.ToggleVisible(var path) ->
                queryState.setVisible(path, !queryState.getVisibleOrDefault(path));

            case LayoutInteraction.SetFilter(var path, var expression) ->
                queryState.setFilterExpression(path, expression);

            case LayoutInteraction.ClearFilter(var path) ->
                queryState.setFilterExpression(path, null);

            case LayoutInteraction.SetRenderMode(var path, var mode) ->
                queryState.setRenderMode(path, mode);

            case LayoutInteraction.SetFormula(var path, var expression) ->
                queryState.setFormulaExpression(path, expression);

            case LayoutInteraction.ClearFormula(var path) ->
                queryState.setFormulaExpression(path, null);

            case LayoutInteraction.SetAggregate(var path, var expression) ->
                queryState.setAggregateExpression(path, expression);

            case LayoutInteraction.ClearAggregate(var path) ->
                queryState.setAggregateExpression(path, null);

            case LayoutInteraction.SetHideIfEmpty(var path, var hide) ->
                queryState.setHideIfEmpty(path, hide ? Boolean.TRUE : null);

            case LayoutInteraction.ResetAll ignored ->
                queryState.reset();
        }
    }

    /** Restore the state before the last {@link #apply} call. */
    public void undo() {
        if (undoStack.isEmpty()) return;
        ObjectNode undoState = undoStack.peek();
        ObjectNode currentState = queryState.toJson();
        undoStack.pop();
        try {
            queryState.fromJson(undoState);
        } catch (RuntimeException e) {
            undoStack.push(undoState);
            throw e;
        }
        redoStack.push(currentState);
    }

    /** Re-apply the state that was undone by the last {@link #undo} call. */
    public void redo() {
        if (redoStack.isEmpty()) return;
        ObjectNode redoState = redoStack.peek();
        ObjectNode currentState = queryState.toJson();
        redoStack.pop();
        try {
            queryState.fromJson(redoState);
        } catch (RuntimeException e) {
            redoStack.push(redoState);
            throw e;
        }
        undoStack.push(currentState);
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /** Clear undo/redo history. Call on schema change. */
    public void clearHistory() {
        undoStack.clear();
        redoStack.clear();
    }

    private void pushUndo() {
        undoStack.push(queryState.toJson());
        redoStack.clear();
        // Cap stack size — discard oldest
        while (undoStack.size() > MAX_UNDO_DEPTH) {
            undoStack.removeLast();
        }
    }

    private static SchemaPath eventPath(LayoutInteraction event) {
        return switch (event) {
            case LayoutInteraction.SortBy(var p, var d) -> p;
            case LayoutInteraction.ClearSort(var p) -> p;
            case LayoutInteraction.ToggleVisible(var p) -> p;
            case LayoutInteraction.SetFilter(var p, var e) -> p;
            case LayoutInteraction.ClearFilter(var p) -> p;
            case LayoutInteraction.SetRenderMode(var p, var m) -> p;
            case LayoutInteraction.SetFormula(var p, var e) -> p;
            case LayoutInteraction.ClearFormula(var p) -> p;
            case LayoutInteraction.SetAggregate(var p, var e) -> p;
            case LayoutInteraction.ClearAggregate(var p) -> p;
            case LayoutInteraction.SetHideIfEmpty(var p, var h) -> p;
            case LayoutInteraction.ResetAll(var p) -> p;
        };
    }
}
