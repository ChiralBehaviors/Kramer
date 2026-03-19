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
 *       to greedy: CROSSTAB-eligible nodes prefer CROSSTAB when it fits, otherwise
 *       TABLE if it fits, otherwise OUTLINE.</li>
 *   <li>Otherwise enumerate all mixed-radix combinations: 3 choices (TABLE, OUTLINE,
 *       CROSSTAB) for crosstab-eligible nodes, 2 choices (TABLE, OUTLINE) for others.
 *       For each assignment check global feasibility using
 *       {@link #checkFeasible}.</li>
 *   <li>Among feasible assignments, maximise the count of column-oriented modes
 *       (TABLE or CROSSTAB).  Tie-break: prefer TABLE over CROSSTAB (TABLE gets a
 *       small bonus), and earlier DFS order.</li>
 * </ol>
 */
public final class ExhaustiveConstraintSolver implements ConstraintSolver {

    /** Assignment digit: node renders as TABLE. */
    private static final int    MODE_TABLE    = 0;
    /** Assignment digit: node renders as OUTLINE. */
    private static final int    MODE_OUTLINE  = 1;
    /** Assignment digit: node renders as CROSSTAB (only for eligible nodes). */
    private static final int    MODE_CROSSTAB = 2;

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

        // Build index lookup once; reused by every feasibility check in the enumeration loop
        Map<SchemaPath, Integer> indexMap = new LinkedHashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            indexMap.put(variable.get(i).path(), i);
        }

        // 2. Greedy fallback when N > threshold
        if (n > EXHAUSTIVE_THRESHOLD) {
            LOG.info(() -> String.format(
                    "ExhaustiveConstraintSolver: %d nodes exceeds threshold %d; using greedy",
                    n, EXHAUSTIVE_THRESHOLD));
            Map<SchemaPath, RelationRenderMode> result = new LinkedHashMap<>(fixed);
            for (RelationConstraint node : variable) {
                result.put(node.path(), greedyMode(node));
            }
            return Map.copyOf(result);
        }

        // 3. Build radix array: 3 choices for crosstab-eligible nodes, 2 for others.
        //    Digit encoding: MODE_TABLE=0, MODE_OUTLINE=1, MODE_CROSSTAB=2.
        int[] radixes = new int[n];
        for (int i = 0; i < n; i++) {
            radixes[i] = variable.get(i).crosstabEligible() ? 3 : 2;
        }

        long total = 1L;
        for (int r : radixes) {
            total *= r;
        }

        // Enumerate all mixed-radix combinations.
        // Best assignment is tracked by (column-oriented count, TABLE count) — both maximised.
        int bestColumnCount = -1;
        int bestTableCount  = -1;
        int[] bestAssignment = new int[n]; // defaults to all-TABLE (0) initially; overwritten below

        // Verify all-OUTLINE is feasible (digit=1 for every variable)
        int[] outlineAll = new int[n];
        java.util.Arrays.fill(outlineAll, MODE_OUTLINE);
        assert checkFeasible(root, outlineAll, variable, indexMap, fixed, Double.MAX_VALUE)
                : "all-OUTLINE assignment must always be feasible";

        int[] assignment = new int[n];
        for (long combo = 0; combo < total; combo++) {
            // Decode mixed-radix into assignment array (right-to-left)
            long temp = combo;
            for (int i = n - 1; i >= 0; i--) {
                assignment[i] = (int) (temp % radixes[i]);
                temp /= radixes[i];
            }

            if (checkFeasible(root, assignment, variable, indexMap, fixed, Double.MAX_VALUE)) {
                int colCount   = countColumnOriented(assignment, n);
                int tableCount = countTable(assignment, n);
                // Prefer more column-oriented modes; tie-break by more TABLE specifically
                if (colCount > bestColumnCount
                        || (colCount == bestColumnCount && tableCount > bestTableCount)) {
                    bestColumnCount = colCount;
                    bestTableCount  = tableCount;
                    bestAssignment  = assignment.clone();
                }
            }
        }

        // 4. Build result map from best assignment
        Map<SchemaPath, RelationRenderMode> result = new LinkedHashMap<>(fixed);
        for (int i = 0; i < n; i++) {
            result.put(variable.get(i).path(), toMode(bestAssignment[i]));
        }
        return Map.copyOf(result);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /** Greedy mode selection for a single node: CROSSTAB > TABLE > OUTLINE. */
    private static RelationRenderMode greedyMode(RelationConstraint node) {
        if (node.crosstabEligible() && node.fitsCrosstab()) {
            return RelationRenderMode.CROSSTAB;
        } else if (node.fitsTable()) {
            return RelationRenderMode.TABLE;
        } else {
            return RelationRenderMode.OUTLINE;
        }
    }

    /** Convert a digit value to a {@link RelationRenderMode}. */
    private static RelationRenderMode toMode(int digit) {
        return switch (digit) {
            case MODE_TABLE    -> RelationRenderMode.TABLE;
            case MODE_CROSSTAB -> RelationRenderMode.CROSSTAB;
            default            -> RelationRenderMode.OUTLINE;
        };
    }

    /** Count nodes assigned TABLE or CROSSTAB (column-oriented). */
    private static int countColumnOriented(int[] assignment, int n) {
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (assignment[i] == MODE_TABLE || assignment[i] == MODE_CROSSTAB) {
                count++;
            }
        }
        return count;
    }

    /** Count nodes assigned TABLE (used for TABLE-over-CROSSTAB tie-break). */
    private static int countTable(int[] assignment, int n) {
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (assignment[i] == MODE_TABLE) {
                count++;
            }
        }
        return count;
    }

    /** Appends every reachable node to {@code out} in depth-first order. */
    private static void collectDfs(RelationConstraint node, List<RelationConstraint> out) {
        out.add(node);
        for (RelationConstraint child : node.children()) {
            collectDfs(child, out);
        }
    }

    /**
     * Recursively checks feasibility for {@code node} given the mixed-radix assignment.
     *
     * <p>Constraints checked per node:
     * <ul>
     *   <li>TABLE: own fit ({@link RelationConstraint#fitsTable()} or
     *       {@link RelationConstraint#fitsTableInParentTable()}) and parent-column fit.</li>
     *   <li>CROSSTAB: {@link RelationConstraint#fitsCrosstab()} or
     *       {@link RelationConstraint#fitsCrosstabInParentTable()} depending on parent mode.</li>
     *   <li>OUTLINE / fixed-CROSSTAB: children see unbounded parent width.</li>
     * </ul>
     *
     * @param parentTableWidth the parent's {@code tableWidth} when the parent was assigned TABLE;
     *                         {@code Double.MAX_VALUE} at the root or when the parent is
     *                         OUTLINE/CROSSTAB.
     */
    private static boolean checkFeasible(RelationConstraint node,
                                          int[] assignment,
                                          List<RelationConstraint> variable,
                                          Map<SchemaPath, Integer> indexMap,
                                          Map<SchemaPath, RelationRenderMode> fixed,
                                          double parentTableWidth) {
        int digit;
        if (fixed.containsKey(node.path())) {
            // Fixed CROSSTAB — treat as outline for child-propagation purposes
            digit = MODE_OUTLINE;
        } else {
            Integer idx = indexMap.get(node.path());
            digit = (idx != null) ? assignment[idx] : MODE_OUTLINE;
        }

        if (digit == MODE_TABLE) {
            // Own-fit check
            boolean fits = (parentTableWidth < Double.MAX_VALUE) ? node.fitsTableInParentTable()
                                                                 : node.fitsTable();
            if (!fits) {
                return false;
            }
            // Parent-column fit
            if (node.tableWidth() + node.nestedHorizontalInset() > parentTableWidth) {
                return false;
            }
            // Children of TABLE parent see this node's tableWidth
            for (RelationConstraint child : node.children()) {
                if (!checkFeasible(child, assignment, variable, indexMap, fixed,
                                   node.tableWidth())) {
                    return false;
                }
            }
        } else if (digit == MODE_CROSSTAB) {
            // Crosstab fit check
            boolean fits = (parentTableWidth < Double.MAX_VALUE)
                           ? node.fitsCrosstabInParentTable()
                           : node.fitsCrosstab();
            if (!fits) {
                return false;
            }
            // Children of CROSSTAB see unbounded width (crosstab column width is data-dependent)
            for (RelationConstraint child : node.children()) {
                if (!checkFeasible(child, assignment, variable, indexMap, fixed,
                                   Double.MAX_VALUE)) {
                    return false;
                }
            }
        } else {
            // OUTLINE: children see unbounded parent width
            for (RelationConstraint child : node.children()) {
                if (!checkFeasible(child, assignment, variable, indexMap, fixed,
                                   Double.MAX_VALUE)) {
                    return false;
                }
            }
        }

        return true;
    }
}
