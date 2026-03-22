// SPDX-License-Identifier: Apache-2.0

/**
 * Layout pipeline result types — portable data records.
 */

export type RenderMode = 'table' | 'outline';

export interface MeasureResult {
  readonly labelWidth: number;
  readonly dataWidth: number;
  readonly maxWidth: number;
  readonly averageCardinality: number;
  readonly isVariableLength: boolean;
}

export interface LayoutResult {
  readonly mode: RenderMode;
  readonly tableColumnWidth: number;
  readonly justifiedWidth: number;
  readonly children: LayoutResult[];
}

export interface ColumnSetSnapshot {
  readonly columns: ColumnSnapshot[];
  readonly height: number;
}

export interface ColumnSnapshot {
  readonly width: number;
  readonly fieldNames: string[];
}

export interface CompressResult {
  readonly justifiedWidth: number;
  readonly columnSets: ColumnSetSnapshot[];
  readonly cellHeight: number;
}

/**
 * MeasurementStrategy — platform adapter for text measurement.
 * Browser implementation uses canvas.measureText() or DOM measurement.
 */
export interface MeasurementStrategy {
  textWidth(text: string, font: string, fontSize: number): number;
  lineHeight(font: string, fontSize: number): number;
}
