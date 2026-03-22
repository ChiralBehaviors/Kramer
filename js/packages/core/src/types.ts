// SPDX-License-Identifier: Apache-2.0

/**
 * Layout pipeline result types — port of Java record hierarchy.
 * Pure data, zero platform dependency. These mirror LayoutDecisionNode
 * and its constituents, forming the portable cross-language contract.
 */

import type { SchemaPath } from './schema.js';

// --- Render modes ---

export type RelationRenderMode = 'TABLE' | 'OUTLINE' | 'CROSSTAB';
export type PrimitiveRenderMode = 'TEXT' | 'BADGE' | 'BAR' | 'SPARKLINE';

// --- Measure result (14 fields, matches Java MeasureResult) ---

export interface MeasureResult {
  readonly labelWidth: number;
  readonly columnWidth: number;
  readonly dataWidth: number;
  readonly maxWidth: number;
  readonly averageCardinality: number;
  readonly isVariableLength: boolean;
  readonly averageChildCardinality: number;
  readonly maxCardinality: number;
  // extractor is @JsonIgnore in Java — omitted from portable protocol
  readonly childResults: MeasureResult[];
  readonly contentStats: ContentWidthStats | null;
  readonly numericStats: NumericStats | null;
  readonly pivotStats: PivotStats | null;
  readonly sparklineStats: SparklineStats | null;
}

export interface ContentWidthStats {
  readonly p90: number;
  readonly min: number;
  readonly max: number;
  readonly mean: number;
  readonly sampleCount: number;
}

export interface NumericStats {
  readonly min: number;
  readonly max: number;
  readonly mean: number;
}

export interface PivotStats {
  readonly pivotField: string;
  readonly pivotValues: string[];
  readonly pivotCount: number;
}

export interface SparklineStats {
  readonly min: number;
  readonly max: number;
  readonly valueCount: number;
}

// --- Layout result (mode decision + geometry) ---

export interface LayoutResult {
  readonly relationMode: RelationRenderMode | null;
  readonly primitiveMode: PrimitiveRenderMode;
  readonly useVerticalHeader: boolean;
  readonly tableColumnWidth: number;
  readonly columnHeaderIndentation: number;
  readonly constrainedColumnWidth: number;
  readonly childResults: LayoutResult[];
}

// --- Compress result (column set packing) ---

export interface CompressResult {
  readonly justifiedWidth: number;
  readonly columnSetSnapshots: ColumnSetSnapshot[];
  readonly cellHeight: number;
  readonly childResults: CompressResult[];
}

export interface ColumnSetSnapshot {
  readonly columns: ColumnSnapshot[];
  readonly height: number;
}

export interface ColumnSnapshot {
  readonly width: number;
  readonly fieldNames: string[];
}

// --- Height result ---

export interface HeightResult {
  readonly height: number;
  readonly cellHeight: number;
  readonly resolvedCardinality: number;
  readonly columnHeaderHeight: number;
  readonly childResults: HeightResult[];
}

// --- Layout Decision Node (the portable cross-language protocol) ---

export interface LayoutDecisionNode {
  readonly path: SchemaPath;
  readonly fieldName: string;
  readonly measureResult: MeasureResult | null;
  readonly layoutResult: LayoutResult | null;
  readonly compressResult: CompressResult | null;
  readonly heightResult: HeightResult | null;
  readonly columnSetSnapshots: ColumnSetSnapshot[];
  readonly childNodes: LayoutDecisionNode[];
}

// --- Measurement strategy (platform adapter) ---

export interface MeasurementStrategy {
  textWidth(text: string, font: string, fontSize: number): number;
  lineHeight(font: string, fontSize: number): number;
}
