// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;

/**
 * Static utility that detects whether a {@link Relation} matches the Relay
 * cursor-based connection pattern.
 *
 * <p>Detection is structural (no server introspection): a relation is considered
 * a connection when it has a child named {@code edges} which itself has a child
 * named {@code node}, and a sibling child named {@code pageInfo}.
 *
 * @author hhildebrand
 */
public final class RelayConnectionDetector {

    private RelayConnectionDetector() {}

    /**
     * Returns {@code true} if {@code relation} has children {@code edges}
     * (with grandchild {@code node}) and {@code pageInfo}.
     * Structural heuristic — no server introspection.
     */
    public static boolean isConnection(Relation relation) {
        boolean hasEdgesWithNode = false;
        boolean hasPageInfo = false;

        for (SchemaNode child : relation.getChildren()) {
            if ("edges".equals(child.getField()) && child instanceof Relation edges) {
                hasEdgesWithNode = edges.getChildren().stream()
                    .anyMatch(gc -> "node".equals(gc.getField()));
            }
            if ("pageInfo".equals(child.getField())) {
                hasPageInfo = true;
            }
        }

        return hasEdgesWithNode && hasPageInfo;
    }
}
