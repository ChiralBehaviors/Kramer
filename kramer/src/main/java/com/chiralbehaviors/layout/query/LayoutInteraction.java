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

import com.chiralbehaviors.layout.SchemaPath;

/**
 * Sealed event hierarchy representing user interactions with a layout.
 * Each variant carries a {@link SchemaPath} identifying the target field
 * (may be {@code null} for events that apply globally, e.g. {@link ResetAll}).
 * <p>
 * Dispatch via {@link InteractionHandler#apply(LayoutInteraction)}.
 *
 * @author hhildebrand
 */
public sealed interface LayoutInteraction permits
    LayoutInteraction.SortBy, LayoutInteraction.ClearSort,
    LayoutInteraction.ToggleVisible,
    LayoutInteraction.SetFilter, LayoutInteraction.ClearFilter,
    LayoutInteraction.SetRenderMode, LayoutInteraction.SetFormula,
    LayoutInteraction.ClearFormula, LayoutInteraction.SetAggregate,
    LayoutInteraction.ClearAggregate, LayoutInteraction.ResetAll {

    SchemaPath path();

    /** Sort the field at {@code path} ascending ({@code descending=false}) or descending. */
    record SortBy(SchemaPath path, boolean descending) implements LayoutInteraction {}

    /** Clear any sort on the field at {@code path}. */
    record ClearSort(SchemaPath path) implements LayoutInteraction {}

    /** Toggle the visibility of the field at {@code path}. */
    record ToggleVisible(SchemaPath path) implements LayoutInteraction {}

    /** Apply a filter predicate expression to the field at {@code path}. */
    record SetFilter(SchemaPath path, String expression) implements LayoutInteraction {}

    /** Remove the filter predicate from the field at {@code path}. */
    record ClearFilter(SchemaPath path) implements LayoutInteraction {}

    /** Set the rendering mode for the field at {@code path}. */
    record SetRenderMode(SchemaPath path, String mode) implements LayoutInteraction {}

    /** Set a computed formula expression on the field at {@code path}. */
    record SetFormula(SchemaPath path, String expression) implements LayoutInteraction {}

    /** Remove the formula expression from the field at {@code path}. */
    record ClearFormula(SchemaPath path) implements LayoutInteraction {}

    /** Set an aggregate expression on the field at {@code path}. */
    record SetAggregate(SchemaPath path, String expression) implements LayoutInteraction {}

    /** Remove the aggregate expression from the field at {@code path}. */
    record ClearAggregate(SchemaPath path) implements LayoutInteraction {}

    /**
     * Reset all overrides in the layout state. The {@code path} is ignored;
     * pass {@code null} or use the no-arg constructor.
     */
    record ResetAll(SchemaPath path) implements LayoutInteraction {
        public ResetAll() { this(null); }
    }
}
