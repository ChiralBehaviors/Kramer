// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

/**
 * Controls how a Primitive leaf node is rendered.
 *
 * <ul>
 *   <li>TEXT — plain text label (default)</li>
 *   <li>BAR — horizontal bar chart representation</li>
 *   <li>BADGE — badge / chip representation</li>
 *   <li>SPARKLINE — mini time-series sparkline chart</li>
 * </ul>
 */
public enum PrimitiveRenderMode {
    TEXT, BAR, BADGE, SPARKLINE
}
