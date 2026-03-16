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
     * Returns a sanitized CSS class name derived from the leaf field name.
     * Sanitization rules:
     * - null or empty input → "_unknown"
     * - non-[a-zA-Z0-9_-] characters replaced with '_'
     * - leading digit gets '_' prepended
     * Different nodes with the same field name get the same CSS rules.
     */
    public String cssClass() {
        return sanitize(leaf());
    }

    /**
     * Sanitizes a raw field name into a valid CSS identifier token.
     */
    public static String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "_unknown";
        }
        String cleaned = raw.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (Character.isDigit(cleaned.charAt(0))) {
            cleaned = "_" + cleaned;
        }
        return cleaned;
    }

    @Override
    public String toString() {
        return String.join("/", segments);
    }
}
