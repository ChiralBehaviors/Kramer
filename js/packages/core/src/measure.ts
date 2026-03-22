// SPDX-License-Identifier: Apache-2.0

/**
 * Measure phase — port of Java PrimitiveLayout.measure() and RelationLayout.measure().
 * Computes statistical content-width measurements (P90) for layout decisions.
 */

import type { SchemaNode, Relation, Primitive } from './schema.js';
import { extractField } from './schema.js';
import type { MeasurementStrategy, MeasureResult, ContentWidthStats } from './types.js';

/**
 * Measure a primitive field against data, producing width statistics.
 */
export function measurePrimitive(
  node: Primitive,
  data: unknown[],
  strategy: MeasurementStrategy,
  font: string = 'sans-serif',
  fontSize: number = 13,
): MeasureResult {
  const labelWidth = strategy.textWidth(node.label, font, fontSize) + 10;
  const values = data.flatMap(item => extractField(item, node.field));

  if (values.length === 0) {
    return emptyMeasureResult(labelWidth);
  }

  const widths = values.map(v => strategy.textWidth(String(v), font, fontSize));
  widths.sort((a, b) => a - b);

  const p90Index = Math.min(widths.length - 1, Math.floor(widths.length * 0.9));
  const dataWidth = widths[p90Index];
  const maxWidth = widths[widths.length - 1];
  const minWidth = widths[0];
  const mean = widths.reduce((a, b) => a + b, 0) / widths.length;

  const isVariableLength = minWidth > 0 ? (maxWidth / minWidth) > 2.0 : maxWidth > 0;

  const contentStats: ContentWidthStats = {
    p90: dataWidth,
    min: minWidth,
    max: maxWidth,
    mean,
    sampleCount: widths.length,
  };

  return {
    labelWidth,
    columnWidth: Math.max(labelWidth, dataWidth),
    dataWidth,
    maxWidth,
    averageCardinality: Math.max(1, Math.ceil(values.length / Math.max(1, data.length))),
    isVariableLength,
    averageChildCardinality: 0,
    maxCardinality: 0,
    childResults: [],
    contentStats,
    numericStats: null,
    pivotStats: null,
    sparklineStats: null,
  };
}

export interface RelationMeasureContext {
  readonly labelWidth: number;
  readonly readableTableWidth: number;
  readonly averageChildCardinality: number;
  readonly childResults: Map<string, MeasureResult>;
}

/**
 * Measure a relation — aggregates children's measurements recursively.
 */
export function measureRelation(
  node: Relation,
  data: unknown[],
  strategy: MeasurementStrategy,
  font: string = 'sans-serif',
  fontSize: number = 13,
): RelationMeasureContext {
  const childResults = new Map<string, MeasureResult>();
  let maxLabelWidth = 0;
  let readableTableWidth = 0;
  let totalCardinality = 0;
  let relationChildCount = 0;

  for (const child of node.children) {
    if (child.kind === 'primitive') {
      const mr = measurePrimitive(child, data, strategy, font, fontSize);
      childResults.set(child.field, mr);
      maxLabelWidth = Math.max(maxLabelWidth, mr.labelWidth);
      // Readable width: each column needs content width + label headroom
      readableTableWidth += Math.max(mr.dataWidth, mr.labelWidth) + mr.labelWidth;
    } else {
      // Relation child — extract nested data and measure recursively
      const nestedData = data.flatMap(item => {
        const extracted = extractField(item, child.field);
        return extracted.flatMap(e => Array.isArray(e) ? e : [e]);
      });
      const nestedCtx = measureRelation(child, nestedData, strategy, font, fontSize);
      // Store as a MeasureResult for the relation child
      const childLabelWidth = strategy.textWidth(child.label, font, fontSize) + 10;
      maxLabelWidth = Math.max(maxLabelWidth, childLabelWidth);
      readableTableWidth += nestedCtx.readableTableWidth;
      totalCardinality += nestedCtx.averageChildCardinality;
      relationChildCount++;

      childResults.set(child.field, {
        labelWidth: childLabelWidth,
        columnWidth: nestedCtx.readableTableWidth,
        dataWidth: nestedCtx.readableTableWidth,
        maxWidth: nestedCtx.readableTableWidth,
        averageCardinality: Math.max(1, Math.ceil(nestedData.length / Math.max(1, data.length))),
        isVariableLength: false,
        averageChildCardinality: nestedCtx.averageChildCardinality,
        maxCardinality: 0,
        childResults: Array.from(nestedCtx.childResults.values()),
        contentStats: null,
        numericStats: null,
        pivotStats: null,
        sparklineStats: null,
      });
    }
  }

  return {
    labelWidth: maxLabelWidth,
    readableTableWidth,
    averageChildCardinality: Math.max(1,
      relationChildCount > 0
        ? Math.ceil(totalCardinality / relationChildCount)
        : Math.ceil(data.length)),
    childResults,
  };
}

function emptyMeasureResult(labelWidth: number): MeasureResult {
  return {
    labelWidth,
    columnWidth: labelWidth,
    dataWidth: 0,
    maxWidth: 0,
    averageCardinality: 1,
    isVariableLength: false,
    averageChildCardinality: 0,
    maxCardinality: 0,
    childResults: [],
    contentStats: null,
    numericStats: null,
    pivotStats: null,
    sparklineStats: null,
  };
}
