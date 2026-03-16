// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Constraint solver that exhaustively enumerates render-mode assignments when
 * the number of non-crosstab Relation nodes is at most 15, and falls back to
 * an independent greedy decision per node otherwise.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Collect all Relation nodes from the tree in depth-first order.
 *       Hard-crosstab nodes are pinned to {@link RelationRenderMode#CROSSTAB} and
 *       excluded from enumeration.</li>
 *   <li>If the non-crosstab count exceeds {@value #EXHAUSTIVE_THRESHOLD}, fall back
 *       to greedy: each node independently uses {@link RelationConstraint#fitsTable()}.</li>
 *   <li>Otherwise enumerate all 2<sup>N</sup> TABLE/OUTLINE assignments.
 *       For each assignment check global feasibility: every node assigned TABLE must
 *       satisfy {@link RelationConstraint#fitsTable()} against its own stored
 *       {@code availableWidth}.  TABLE assignments also impose a parent-column
 *       constraint: any child assigned TABLE must additionally fit within the
 *       parent's {@code tableWidth} (the actual column space the parent TABLE
 *       allocates to each child column).</li>
 *   <li>Among feasible assignments, maximise the TABLE count; tiebreak by
 *       DFS order (lower index = earlier node = prefers TABLE when equal count).</li>
 * </ol>
 */
public final class ExhaustiveConstraintSolver implements ConstraintSolver {

    private static final int    EXHAUSTIVE_THRESHOLD = 15;
    private static final Logger LOG                  =
            Logger.getLogger(ExhaustiveConstraintSolver.class.getName());

    @Override
    public Map<SchemaPath, RelationRenderMode> solve(RelationConstraint root) {
        // 1. Flatten tree in DFS order; separate fixed (CROSSTAB) from variable nodes
        List<RelationConstraint> allNodes = new ArrayList<>();
        collectDfs(root, allNodes);

        Map<SchemaPath, RelationRenderMode> fixed = new LinkedHashMap<>();
        List<RelationConstraint> variable = new ArrayList<>();

        for (RelationConstraint node : allNodes) {
            if (node.hardCrosstab()) {
                fixed.put(node.path(), RelationRenderMode.CROSSTAB);
            } else {
                variable.add(node);
            }
        }

        int n = variable.size();

        // Build index lookup once; reused by every feasibility check in the 2^N loop
        Map<SchemaPath, Integer> indexMap = new LinkedHashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            indexMap.put(variable.get(i).path(), i);
        }

        // 2. Greedy fallback when N > threshold (> EXHAUSTIVE_THRESHOLD nodes makes
        //    full enumeration prohibitively expensive: 2^N evaluations)
        if (n > EXHAUSTIVE_THRESHOLD) {
            LOG.info(() -> String.format(
                    "ExhaustiveConstraintSolver: %d nodes exceeds threshold %d; using greedy",
                    n, EXHAUSTIVE_THRESHOLD));
            Map<SchemaPath, RelationRenderMode> result = new LinkedHashMap<>(fixed);
            for (RelationConstraint node : variable) {
                result.put(node.path(),
                           node.fitsTable() ? RelationRenderMode.TABLE
                                            : RelationRenderMode.OUTLINE);
            }
            return Map.copyOf(result);
        }

        // 3. Enumerate all 2^N assignments (bit i=1 means TABLE for variable[i]).
        //    Prefer higher TABLE count; first feasible mask with the best count wins
        //    (DFS ordering: bit 0 = root, biasing TABLE toward shallower nodes).
        int total = 1 << n;
        int bestTableCount = -1;
        int bestMask = 0;

        // mask=0 is all-OUTLINE: always feasible since no TABLE constraints apply
        assert checkFeasible(root, 0, variable, indexMap, fixed, Double.MAX_VALUE)
                : "all-OUTLINE assignment must always be feasible";

        for (int mask = 0; mask < total; mask++) {
            if (checkFeasible(root, mask, variable, indexMap, fixed, Double.MAX_VALUE)) {
                int tableCount = Integer.bitCount(mask);
                if (tableCount > bestTableCount) {
                    bestTableCount = tableCount;
                    bestMask = mask;
                }
            }
        }

        // 4. Build result map from best assignment
        Map<SchemaPath, RelationRenderMode> result = new LinkedHashMap<>(fixed);
        for (int i = 0; i < n; i++) {
            boolean isTable = (bestMask & (1 << i)) != 0;
            result.put(variable.get(i).path(),
                       isTable ? RelationRenderMode.TABLE : RelationRenderMode.OUTLINE);
        }
        return Map.copyOf(result);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /** Appends every reachable node to {@code out} in depth-first order. */
    private static void collectDfs(RelationConstraint node, List<RelationConstraint> out) {
        out.add(node);
        for (RelationConstraint child : node.children()) {
            collectDfs(child, out);
        }
    }

    /**
     * Recursively checks feasibility for {@code node}.
     *
     * <p>Two constraints are checked for each TABLE node:
     * <ol>
     *   <li><em>Own fit</em>: {@link RelationConstraint#fitsTable()} against
     *       the node's stored {@code availableWidth}.</li>
     *   <li><em>Parent-column fit</em>: if the parent is also TABLE, the node
     *       must additionally fit within the parent's {@code tableWidth}, since
     *       TABLE-mode nesting allocates exactly that much column space.</li>
     * </ol>
     *
     * @param parentTableWidth the parent's {@code tableWidth} when the parent
     *                         was assigned TABLE; {@code Double.MAX_VALUE} at
     *                         the root or when the parent is OUTLINE/CROSSTAB.
     */
    private static boolean checkFeasible(RelationConstraint node,
                                          int mask,
                                          List<RelationConstraint> variable,
                                          Map<SchemaPath, Integer> indexMap,
                                          Map<SchemaPath, RelationRenderMode> fixed,
                                          double parentTableWidth) {
        boolean isTable;
        if (fixed.containsKey(node.path())) {
            // CROSSTAB is fixed; not a TABLE node for propagation purposes
            isTable = false;
        } else {
            Integer idx = indexMap.get(node.path());
            isTable = idx != null && (mask & (1 << idx)) != 0;
        }

        if (isTable) {
            // Own-fit check: use table-mode width when parent is TABLE, outline width otherwise
            boolean fits = (parentTableWidth < Double.MAX_VALUE) ? node.fitsTableInParentTable()
                                                                 : node.fitsTable();
            if (!fits) {
                return false;
            }
            // Parent-column fit: TABLE parent allocates tableWidth to this column
            if (node.tableWidth() + node.nestedHorizontalInset() > parentTableWidth) {
                return false;
            }
            // Recurse: children of TABLE parent see parentTableWidth = this node's tableWidth
            for (RelationConstraint child : node.children()) {
                if (!checkFeasible(child, mask, variable, indexMap, fixed,
                                   node.tableWidth())) {
                    return false;
                }
            }
        } else {
            // OUTLINE or CROSSTAB: children see unbounded parent width.
            // CROSSTAB column width is data-dependent and not modeled in Phase 1;
            // children see unbounded width as a safe approximation.
            for (RelationConstraint child : node.children()) {
                if (!checkFeasible(child, mask, variable, indexMap, fixed,
                                   Double.MAX_VALUE)) {
                    return false;
                }
            }
        }

        return true;
    }
}
