// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.cell.control;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;

/**
 * Cursor state for physical keyboard navigation. Stores enough information
 * to survive layout rebuilds (via dataIdentity + fieldPath) while caching
 * scene-relative position for hit-testing.
 *
 * <p>scenePosition and cellBounds are ephemeral — re-derived after each
 * layout rebuild. dataIdentity and fieldPath are stable across rebuilds.</p>
 */
public record CursorState(
    Object dataIdentity,    // JSON node identity or array index
    String fieldPath,       // schema path to focused field
    int cellIndex,          // index in the VirtualFlow's item list
    Point2D scenePosition,  // cached scene coords of cursor, re-derived after relayout
    Bounds cellBounds       // cached cell bounds, used for step size
) {}
