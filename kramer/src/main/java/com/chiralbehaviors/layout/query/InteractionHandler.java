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

/**
 * Applies {@link LayoutInteraction} events to a {@link LayoutQueryState}.
 * <p>
 * Each call to {@link #apply(LayoutInteraction)} dispatches via a pattern-matching
 * switch and delegates to the appropriate typed setter on the query state.
 * Exactly one change notification fires per call (delegated to the state).
 *
 * @author hhildebrand
 */
public final class InteractionHandler {

    private final LayoutQueryState queryState;

    public InteractionHandler(LayoutQueryState queryState) {
        this.queryState = queryState;
    }

    /**
     * Dispatch the given interaction event to the underlying {@link LayoutQueryState}.
     *
     * @param event the event to apply; must not be {@code null}
     */
    public void apply(LayoutInteraction event) {
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

            case LayoutInteraction.ResetAll ignored ->
                queryState.reset();
        }
    }
}
