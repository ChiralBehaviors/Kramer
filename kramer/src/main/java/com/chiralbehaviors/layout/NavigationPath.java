// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;

/**
 * Represents a navigable path through the layout, used for selection and
 * cursor positioning. Each step identifies a field and the row within it.
 */
public record NavigationPath(List<NavigationStep> steps) {

    public NavigationPath {
        steps = List.copyOf(steps);
    }

    /**
     * A single step: the schema field and the row index within that field's data.
     */
    public record NavigationStep(SchemaPath field, int rowIndex) {
    }
}
