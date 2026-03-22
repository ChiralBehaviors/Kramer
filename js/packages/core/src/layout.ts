// SPDX-License-Identifier: Apache-2.0

/**
 * Layout decision + constraint solver — port of Java ExhaustiveConstraintSolver.
 * Decides table vs outline mode for each relation in the schema tree.
 */

import type { Relation } from './schema.js';
import type { RelationRenderMode } from './types.js';
import type { RelationMeasureContext } from './measure.js';

// --- Constraint types ---

export interface RelationConstraint {
  readonly path: string;
  readonly readableTableWidth: number;
  readonly availableWidth: number;
  readonly children: RelationConstraint[];
}

// --- Exhaustive Constraint Solver ---

const EXHAUSTIVE_THRESHOLD = 15;

/**
 * Exhaustive constraint solver — enumerates all render-mode assignments
 * and picks the one that maximizes table mode usage while fitting.
 * Falls back to greedy when node count exceeds threshold.
 *
 * Port of Java ExhaustiveConstraintSolver.
 */
export function solveConstraints(
  root: RelationConstraint,
): Map<string, RelationRenderMode> {
  const allNodes: RelationConstraint[] = [];
  collectDfs(root, allNodes);

  if (allNodes.length > EXHAUSTIVE_THRESHOLD) {
    // Greedy fallback
    const result = new Map<string, RelationRenderMode>();
    for (const node of allNodes) {
      result.set(node.path, node.readableTableWidth <= node.availableWidth ? 'TABLE' : 'OUTLINE');
    }
    return result;
  }

  const n = allNodes.length;

  // Verify all-OUTLINE is feasible (should always be)
  const allOutline = new Array(n).fill(1); // 1 = OUTLINE
  if (!checkFeasible(root, allOutline, allNodes)) {
    const result = new Map<string, RelationRenderMode>();
    for (const node of allNodes) result.set(node.path, 'OUTLINE');
    return result;
  }

  // Enumerate all 2^n combinations (TABLE=0, OUTLINE=1)
  const total = 1 << n;
  let bestTableCount = -1;
  let bestAssignment = allOutline;

  for (let combo = 0; combo < total; combo++) {
    const assignment = new Array(n);
    for (let i = 0; i < n; i++) {
      assignment[i] = (combo >> (n - 1 - i)) & 1;
    }

    if (checkFeasible(root, assignment, allNodes)) {
      const tableCount = assignment.filter(a => a === 0).length;
      if (tableCount > bestTableCount) {
        bestTableCount = tableCount;
        bestAssignment = assignment.slice();
      }
    }
  }

  const result = new Map<string, RelationRenderMode>();
  for (let i = 0; i < n; i++) {
    result.set(allNodes[i].path, bestAssignment[i] === 0 ? 'TABLE' : 'OUTLINE');
  }
  return result;
}

function collectDfs(node: RelationConstraint, out: RelationConstraint[]): void {
  out.push(node);
  for (const child of node.children) collectDfs(child, out);
}

function checkFeasible(
  node: RelationConstraint,
  assignment: number[],
  allNodes: RelationConstraint[],
): boolean {
  const idx = allNodes.indexOf(node);
  const isTable = idx >= 0 && assignment[idx] === 0;

  if (isTable && node.readableTableWidth > node.availableWidth) {
    return false;
  }

  for (const child of node.children) {
    // When parent is TABLE, child available width is constrained
    const childAvailable = isTable
      ? node.availableWidth / Math.max(1, node.children.length)
      : node.availableWidth;

    const childIdx = allNodes.indexOf(child);
    if (childIdx >= 0 && assignment[childIdx] === 0) {
      // Child is TABLE — check it fits
      if (child.readableTableWidth > childAvailable) return false;
    }

    if (!checkFeasible(child, assignment, allNodes)) return false;
  }

  return true;
}

// --- Simple mode decision (used when solver is overkill) ---

export function decideMode(
  readableTableWidth: number,
  availableWidth: number,
): RelationRenderMode {
  return readableTableWidth <= availableWidth ? 'TABLE' : 'OUTLINE';
}

// --- Build constraint tree from measure context ---

export function buildConstraintTree(
  node: Relation,
  measure: RelationMeasureContext,
  availableWidth: number,
): RelationConstraint {
  const children: RelationConstraint[] = [];

  for (const child of node.children) {
    if (child.kind === 'relation') {
      const childMeasure = measure.childResults.get(child.field);
      if (childMeasure) {
        const childAvailable = availableWidth - measure.labelWidth;
        // Reconstruct a RelationMeasureContext for the child
        const childCtx: RelationMeasureContext = {
          labelWidth: childMeasure.labelWidth,
          readableTableWidth: childMeasure.dataWidth, // stored as dataWidth for relations
          averageChildCardinality: childMeasure.averageChildCardinality,
          childResults: new Map(
            childMeasure.childResults.map((cr, i) => [
              child.kind === 'relation' ? child.children[i]?.field ?? String(i) : String(i),
              cr,
            ])
          ),
        };
        children.push(buildConstraintTree(child, childCtx, childAvailable));
      }
    }
  }

  return {
    path: node.field,
    readableTableWidth: measure.readableTableWidth,
    availableWidth,
    children,
  };
}

// --- Justify: two-pass minimum-guarantee width distribution ---

export interface JustifiedChild {
  readonly field: string;
  readonly kind: 'primitive' | 'relation';
  readonly width: number;
}

/**
 * Distribute available width among children using two-pass
 * minimum-guarantee distribution (Bakke §3.3).
 */
export function justifyChildren(
  node: Relation,
  measure: RelationMeasureContext,
  availableWidth: number,
): JustifiedChild[] {
  const children = node.children;
  if (children.length === 0) return [];

  // Minimums: label width for primitives, sum of children's labels for relations
  const minimums = children.map(child => {
    const mr = measure.childResults.get(child.field);
    if (!mr) return 30;
    return mr.labelWidth;
  });

  const minimumTotal = minimums.reduce((a, b) => a + b, 0);
  const surplus = Math.max(0, availableWidth - minimumTotal);

  // Effective widths (natural content width above minimum)
  const effectiveWidths = children.map(child => {
    const mr = measure.childResults.get(child.field);
    if (!mr) return 30;
    return Math.max(mr.dataWidth, mr.labelWidth);
  });

  const surplusWeightTotal = effectiveWidths.reduce((sum, ew, i) =>
    sum + Math.max(0, ew - minimums[i]), 0);

  return children.map((child, i) => {
    let width: number;
    if (surplus <= 0 || minimumTotal <= 0) {
      width = availableWidth * (minimums[i] / Math.max(1, minimumTotal));
    } else {
      const surplusShare = surplusWeightTotal > 0
        ? surplus * (Math.max(0, effectiveWidths[i] - minimums[i]) / surplusWeightTotal)
        : surplus / children.length;
      width = minimums[i] + surplusShare;
    }
    return { field: child.field, kind: child.kind, width: Math.floor(width) };
  });
}

// --- DP Column Partitioner ---

/**
 * DP-optimal column partitioning (painter's partition problem, O(N²K)).
 * Minimizes max column height across all contiguous partitions.
 */
export function dpPartition(fieldHeights: number[], numColumns: number): number[] {
  const n = fieldHeights.length;
  if (n === 0) return new Array(numColumns).fill(0);
  if (numColumns >= n) {
    const sizes = new Array(numColumns).fill(0);
    for (let i = 0; i < n; i++) sizes[i] = 1;
    return sizes;
  }
  if (numColumns === 1) return [n];

  const prefix = new Array(n + 1).fill(0);
  for (let i = 0; i < n; i++) prefix[i + 1] = prefix[i] + fieldHeights[i];

  const dp: number[][] = Array.from({ length: n + 1 }, () =>
    new Array(numColumns + 1).fill(Infinity));
  const split: number[][] = Array.from({ length: n + 1 }, () =>
    new Array(numColumns + 1).fill(0));

  for (let i = 0; i <= n; i++) dp[i][1] = prefix[i];

  for (let j = 2; j <= numColumns; j++) {
    for (let i = j; i <= n; i++) {
      for (let s = j - 1; s < i; s++) {
        const cost = Math.max(dp[s][j - 1], prefix[i] - prefix[s]);
        if (cost < dp[i][j]) {
          dp[i][j] = cost;
          split[i][j] = s;
        }
      }
    }
  }

  const boundaries = new Array(numColumns + 1).fill(0);
  boundaries[numColumns] = n;
  for (let j = numColumns; j >= 2; j--) {
    boundaries[j - 1] = split[boundaries[j]][j];
  }

  const sizes = new Array(numColumns);
  for (let j = 0; j < numColumns; j++) {
    sizes[j] = boundaries[j + 1] - boundaries[j];
  }
  return sizes;
}
