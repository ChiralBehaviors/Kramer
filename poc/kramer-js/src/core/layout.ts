// SPDX-License-Identifier: Apache-2.0

import { type SchemaNode, type Relation, type Primitive } from './schema.js';
import { type RenderMode, type LayoutResult } from './types.js';
import { type RelationMeasureResult, type MeasureResult } from './measure.js';

/**
 * Layout decision: choose table vs outline mode for each relation.
 *
 * Port of ExhaustiveConstraintSolver + RelationLayout.layout().
 * Simplified for PoC: greedy per-node decision (not exhaustive enumeration).
 */
export function decideMode(
  node: Relation,
  measure: RelationMeasureResult,
  availableWidth: number,
): RenderMode {
  // Table fits if readable table width fits within available space
  const fitsTable = measure.readableTableWidth <= availableWidth;
  return fitsTable ? 'table' : 'outline';
}

/**
 * Recursive layout decision for a relation tree.
 */
export function layoutRelation(
  node: Relation,
  measure: RelationMeasureResult,
  availableWidth: number,
): LayoutResult {
  const mode = decideMode(node, measure, availableWidth);

  const childResults: LayoutResult[] = [];
  for (const child of node.children) {
    if (child.kind === 'relation') {
      const childMeasure = measure.childResults.get(child.field);
      if (childMeasure && 'childResults' in childMeasure) {
        // In outline mode, child relations get the full available width
        // In table mode, they share the table column width
        const childWidth = mode === 'outline'
          ? availableWidth - measure.labelWidth
          : availableWidth / node.children.length;
        childResults.push(layoutRelation(child, childMeasure, childWidth));
      }
    }
  }

  return {
    mode,
    tableColumnWidth: measure.readableTableWidth,
    justifiedWidth: availableWidth,
    children: childResults,
  };
}

/**
 * Justify: distribute available width among children.
 *
 * Port of RelationLayout.justifyColumn() — two-pass minimum-guarantee
 * distribution.
 */
export interface JustifiedChild {
  readonly field: string;
  readonly kind: 'primitive' | 'relation';
  readonly width: number;
}

export function justifyChildren(
  node: Relation,
  measure: RelationMeasureResult,
  availableWidth: number,
): JustifiedChild[] {
  const children = node.children;
  if (children.length === 0) return [];

  // Phase 1: compute minimums
  const minimums = children.map(child => {
    const mr = measure.childResults.get(child.field);
    if (!mr) return 30; // fallback
    if ('childResults' in mr) {
      // Relation: minimum is sum of children's label widths
      return mr.labelWidth;
    }
    // Primitive: minimum is label width
    return mr.labelWidth;
  });

  const minimumTotal = minimums.reduce((a, b) => a + b, 0);
  const surplus = Math.max(0, availableWidth - minimumTotal);

  // Phase 2: distribute surplus proportionally by effective width above minimum
  const effectiveWidths = children.map((child, i) => {
    const mr = measure.childResults.get(child.field);
    if (!mr) return minimums[i];
    if ('childResults' in mr) {
      return mr.readableTableWidth;
    }
    return Math.max((mr as MeasureResult).dataWidth, (mr as MeasureResult).labelWidth);
  });

  const surplusWeightTotal = effectiveWidths.reduce((sum, ew, i) =>
    sum + Math.max(0, ew - minimums[i]), 0);

  return children.map((child, i) => {
    let width: number;
    if (surplus <= 0 || minimumTotal <= 0) {
      // Scale proportionally
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

/**
 * DP-optimal column partitioner (painter's partition problem).
 * Port of Java ColumnPartitioner.dpOptimal().
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

  // Prefix sums
  const prefix = new Array(n + 1).fill(0);
  for (let i = 0; i < n; i++) prefix[i + 1] = prefix[i] + fieldHeights[i];

  // DP table
  const dp: number[][] = Array.from({ length: n + 1 }, () => new Array(numColumns + 1).fill(Infinity));
  const split: number[][] = Array.from({ length: n + 1 }, () => new Array(numColumns + 1).fill(0));

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

  // Backtrack
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
