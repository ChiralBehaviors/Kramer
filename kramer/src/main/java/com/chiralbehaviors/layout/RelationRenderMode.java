// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

/**
 * Controls how a Relation node is rendered.
 *
 * <ul>
 *   <li>AUTO — let the layout engine decide (outline or nested-table) based on width</li>
 *   <li>TABLE — force nested-table rendering</li>
 *   <li>OUTLINE — force outline rendering</li>
 *   <li>CROSSTAB — cross-tabulation rendering (reserved for future use)</li>
 * </ul>
 */
public enum RelationRenderMode {
    AUTO, TABLE, OUTLINE, CROSSTAB
}
