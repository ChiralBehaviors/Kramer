// SPDX-License-Identifier: Apache-2.0

import { type SchemaNode, type Relation, type Primitive, extractField } from './schema.js';
import { type MeasureResult, type MeasurementStrategy } from './types.js';

/**
 * Browser measurement strategy using canvas.measureText().
 */
export class CanvasMeasurement implements MeasurementStrategy {
  private readonly ctx: CanvasRenderingContext2D;

  constructor() {
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('Canvas 2D context not available');
    this.ctx = ctx;
  }

  textWidth(text: string, font: string, fontSize: number): number {
    this.ctx.font = `${fontSize}px ${font}`;
    return Math.ceil(this.ctx.measureText(text).width);
  }

  lineHeight(_font: string, fontSize: number): number {
    return Math.ceil(fontSize * 1.2);
  }
}

/**
 * Fixed-width measurement for testing (no DOM needed).
 */
export class FixedMeasurement implements MeasurementStrategy {
  constructor(private readonly charWidth: number = 7) {}

  textWidth(text: string, _font: string, _fontSize: number): number {
    return Math.ceil(text.length * this.charWidth);
  }

  lineHeight(_font: string, fontSize: number): number {
    return Math.ceil(fontSize * 1.2);
  }
}

/**
 * Measure a schema node against data, producing width statistics.
 */
export function measurePrimitive(
  node: Primitive,
  data: unknown[],
  strategy: MeasurementStrategy,
  font: string = 'sans-serif',
  fontSize: number = 13,
): MeasureResult {
  const labelWidth = strategy.textWidth(node.label, font, fontSize) + 10; // padding
  const values = data.flatMap(item => extractField(item, node.field));

  if (values.length === 0) {
    return {
      labelWidth,
      dataWidth: 0,
      maxWidth: 0,
      averageCardinality: 1,
      isVariableLength: false,
    };
  }

  const widths = values.map(v => strategy.textWidth(String(v), font, fontSize));
  widths.sort((a, b) => a - b);

  const p90Index = Math.min(widths.length - 1, Math.floor(widths.length * 0.9));
  const dataWidth = widths[p90Index];
  const maxWidth = widths[widths.length - 1];
  const minWidth = widths[0];

  // Variable length if max/min ratio > 2.0 (same heuristic as Java)
  const isVariableLength = minWidth > 0 ? (maxWidth / minWidth) > 2.0 : maxWidth > 0;

  return {
    labelWidth,
    dataWidth,
    maxWidth,
    averageCardinality: Math.max(1, Math.ceil(values.length / data.length)),
    isVariableLength,
  };
}

export interface RelationMeasureResult {
  readonly labelWidth: number; // max of children's label widths
  readonly readableTableWidth: number; // sum of children's readable widths
  readonly averageChildCardinality: number;
  readonly childResults: Map<string, MeasureResult | RelationMeasureResult>;
}

/**
 * Measure a relation — aggregates children's measurements.
 */
export function measureRelation(
  node: Relation,
  data: unknown[],
  strategy: MeasurementStrategy,
  font: string = 'sans-serif',
  fontSize: number = 13,
): RelationMeasureResult {
  const childResults = new Map<string, MeasureResult | RelationMeasureResult>();
  let maxLabelWidth = 0;
  let readableTableWidth = 0;
  let cardSum = 0;

  for (const child of node.children) {
    if (child.kind === 'primitive') {
      const mr = measurePrimitive(child, data, strategy, font, fontSize);
      childResults.set(child.field, mr);
      maxLabelWidth = Math.max(maxLabelWidth, mr.labelWidth);
      readableTableWidth += Math.max(mr.dataWidth, mr.labelWidth) + mr.labelWidth;
    } else {
      // Relation child — extract nested data and measure recursively
      const nestedData = data.flatMap(item => {
        const extracted = extractField(item, child.field);
        return extracted.flatMap(e => Array.isArray(e) ? e : [e]);
      });
      const rmr = measureRelation(child, nestedData, strategy, font, fontSize);
      childResults.set(child.field, rmr);
      maxLabelWidth = Math.max(maxLabelWidth, strategy.textWidth(child.label, font, fontSize) + 10);
      readableTableWidth += rmr.readableTableWidth;
      cardSum += rmr.averageChildCardinality;
    }
  }

  return {
    labelWidth: maxLabelWidth,
    readableTableWidth,
    averageChildCardinality: Math.max(1, Math.ceil(data.length > 0 ? cardSum / node.children.length : 1)),
    childResults,
  };
}
