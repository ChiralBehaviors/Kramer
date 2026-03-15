// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable path addressing a schema node within a schema tree.
 * Solves the field-name uniqueness problem (RF-3): the same field name
 * can appear at multiple nesting levels, but each SchemaPath is unique.
 *
 * @see com.chiralbehaviors.layout.schema.SchemaNode
 */
public record SchemaPath(List<String> segments) {

    public SchemaPath {
        segments = List.copyOf(Objects.requireNonNull(segments));
    }

    public SchemaPath(String root) {
        this(List.of(root));
    }

    public SchemaPath child(String field) {
        var extended = new ArrayList<>(segments);
        extended.add(field);
        return new SchemaPath(extended);
    }

    public String leaf() {
        return segments.getLast();
    }

    /**
     * CSS class uses the leaf field name for backward compatibility.
     * Different nodes with the same field name get the same CSS rules.
     */
    public String cssClass() {
        return leaf();
    }

    @Override
    public String toString() {
        return String.join("/", segments);
    }
}
