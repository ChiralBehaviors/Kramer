// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

public record LayoutDecisionKey(SchemaPath path, int widthBucket, int dataCardinality, long stylesheetVersion) {

    /**
     * Creates a key from a schema path, a raw pixel width, an item count, and a stylesheet version.
     * Width is bucketed by truncating {@code width / 10} to an integer.
     */
    public static LayoutDecisionKey of(SchemaPath path, double width, int itemCount, long stylesheetVersion) {
        assert width >= 0 : "width must be non-negative";
        return new LayoutDecisionKey(path, (int) (width / 10), itemCount, stylesheetVersion);
    }

    /**
     * Convenience overload that uses stylesheet version 0 (no stylesheet discrimination).
     * Preserved for callers that pre-date stylesheet versioning.
     */
    public static LayoutDecisionKey of(SchemaPath path, double width, int itemCount) {
        return of(path, width, itemCount, 0L);
    }
}
