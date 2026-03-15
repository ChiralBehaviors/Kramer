// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Immutable result of the measure phase. Captures data-dependent state that
 * persists across resize cycles. Produced by measure(), cached by AutoLayout.
 *
 * @see SchemaNodeLayout#measure(JsonNode, Function, com.chiralbehaviors.layout.style.Style)
 */
public record MeasureResult(
    double labelWidth,
    double columnWidth,
    double dataWidth,
    double maxWidth,
    int averageCardinality,
    boolean isVariableLength,
    int averageChildCardinality,
    int maxCardinality,
    Function<JsonNode, JsonNode> extractor,
    List<MeasureResult> childResults
) {
    public MeasureResult {
        childResults = childResults == null ? List.of() : List.copyOf(childResults);
    }
}
