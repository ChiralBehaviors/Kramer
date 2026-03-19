// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.query;

/**
 * Immutable snapshot of user intent for a single {@link com.chiralbehaviors.layout.SchemaPath}.
 * Each field is {@code null} when no override is set (use documented defaults).
 * <p>
 * Maps 1:1 to {@link com.chiralbehaviors.layout.LayoutPropertyKeys} constants.
 * See RDR-026 for the full property mapping table.
 *
 * @param visible            field visibility (default: true)
 * @param renderMode         rendering mode override (default: null = auto)
 * @param hideIfEmpty        hide when data is empty (default: false)
 * @param sortFields         comma-separated sort field names (default: null)
 * @param filterExpression   per-row boolean predicate (default: null)
 * @param formulaExpression  per-row computed value (default: null)
 * @param aggregateExpression cross-row reduction (default: null)
 * @param sortExpression     per-row sort key derivation (default: null)
 * @param pivotField         field name for crosstab pivoting (default: null)
 * @param frozen             prevents user mutation of this field (default: null = false)
 * @param aggregatePosition  where aggregate results render: "footer" or null (default: null = hidden)
 * @param cellFormat         printf-style format string for cell display (default: null = raw value)
 * @param columnWidth        user-set column width override in pixels (default: null = computed)
 * @param collapseDuplicates hide consecutive duplicate rows in a Relation (default: null = false)
 *
 * @author hhildebrand
 */
public record FieldState(
    Boolean visible,
    String renderMode,
    Boolean hideIfEmpty,
    String sortFields,
    String filterExpression,
    String formulaExpression,
    String aggregateExpression,
    String sortExpression,
    String pivotField,
    Boolean frozen,
    String aggregatePosition,
    String cellFormat,
    Double columnWidth,
    Boolean collapseDuplicates
) {

    /** A FieldState with all fields null (no overrides). */
    public static final FieldState EMPTY = new FieldState(
        null, null, null, null, null, null, null, null, null, null, null, null, null, null);
}
