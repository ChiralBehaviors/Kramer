// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Renders a {@link LayoutDecisionNode} tree into a target type {@code T}.
 *
 * @param <T> the render output type
 */
public interface LayoutRenderer<T> {

    /**
     * Render the given {@code node} using {@code data} as the JSON context.
     *
     * @param node the layout decision node to render
     * @param data the JSON data for this node; may be {@code null}
     * @return the rendered result
     */
    T render(LayoutDecisionNode node, JsonNode data);
}
