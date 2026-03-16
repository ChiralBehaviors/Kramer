// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

/**
 * Cache key for the layout decision cache in {@link AutoLayout}.
 * Bucketing width to the nearest 10px prevents thrashing on sub-pixel resize
 * events while still producing distinct keys across meaningful width changes.
 *
 * <p>The record's generated {@code equals} and {@code hashCode} make it safe
 * to use directly as a {@link java.util.HashMap} key.
 */
public record LayoutDecisionKey(SchemaPath path, int widthBucket, int dataCardinality) {

    /**
     * Creates a key from a schema path, a raw pixel width, and an item count.
     * Width is bucketed by truncating {@code width / 10} to an integer.
     */
    public static LayoutDecisionKey of(SchemaPath path, double width, int itemCount) {
        return new LayoutDecisionKey(path, (int) (width / 10), itemCount);
    }
}
