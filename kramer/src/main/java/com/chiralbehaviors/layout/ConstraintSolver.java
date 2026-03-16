// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.Map;

/**
 * Determines the optimal {@link RelationRenderMode} for every Relation node
 * in a schema tree before layout() is called.
 *
 * <p>Implementations receive the root {@link RelationConstraint} (which embeds
 * the complete tree of child constraints) and return a map from
 * {@link SchemaPath} to resolved render mode. The map contains an entry for
 * every Relation node reachable from the root.
 *
 * <p>The solver runs after measure() but before layout(), allowing a global
 * view of the available-width constraints without touching any JavaFX nodes.
 */
public interface ConstraintSolver {

    /**
     * Solve the render-mode assignment for the constraint tree rooted at {@code root}.
     *
     * @param root the root constraint node; must not be null
     * @return an immutable map from schema path to resolved render mode;
     *         never null, never contains {@link RelationRenderMode#AUTO}
     */
    Map<SchemaPath, RelationRenderMode> solve(RelationConstraint root);
}
