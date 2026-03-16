// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Base implementation of {@link LayoutRenderer} that walks the
 * {@link LayoutDecisionNode} tree recursively.
 * <p>
 * Leaf nodes (no children) are dispatched to {@link #renderPrimitive}.
 * Interior nodes collect rendered children and are dispatched to
 * {@link #renderRelation}.
 *
 * @param <T> the render output type
 */
public abstract class AbstractLayoutRenderer<T> implements LayoutRenderer<T> {

    @Override
    public T render(LayoutDecisionNode node, JsonNode data) {
        if (node.childNodes().isEmpty()) {
            return renderPrimitive(node, data);
        }
        var childResults = new ArrayList<T>(node.childNodes().size());
        for (var child : node.childNodes()) {
            JsonNode childData = data != null ? data.get(child.fieldName()) : null;
            childResults.add(render(child, childData));
        }
        return renderRelation(node, data, List.copyOf(childResults));
    }

    /**
     * Render a leaf node (a node with no children).
     *
     * @param node the leaf decision node
     * @param data the JSON value for this node; may be {@code null}
     * @return the rendered result
     */
    protected abstract T renderPrimitive(LayoutDecisionNode node, JsonNode data);

    /**
     * Render an interior node after all children have been rendered.
     *
     * @param node     the interior decision node
     * @param data     the JSON object for this node; may be {@code null}
     * @param children rendered results for each child, in child order
     * @return the rendered result
     */
    protected abstract T renderRelation(LayoutDecisionNode node, JsonNode data, List<T> children);
}
