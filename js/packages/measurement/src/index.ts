// SPDX-License-Identifier: Apache-2.0

/**
 * @kramer/measurement — browser and approximate text measurement strategies.
 */

import type { MeasurementStrategy } from '@kramer/core';

/**
 * Browser measurement using canvas.measureText().
 * Requires a DOM context (not available in SSR).
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
 * Fixed-width approximation for testing and SSR.
 * Uses a constant character width — no DOM required.
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
 * Character-width-table approximation for SSR.
 * Uses per-character average widths from common sans-serif fonts.
 */
export class ApproximateMeasurement implements MeasurementStrategy {
  private static readonly CHAR_WIDTHS: Record<string, number> = {
    ' ': 0.28, '!': 0.33, '"': 0.36, '#': 0.60, '$': 0.56, '%': 0.78,
    '&': 0.67, "'": 0.20, '(': 0.33, ')': 0.33, '*': 0.39, '+': 0.60,
    ',': 0.28, '-': 0.33, '.': 0.28, '/': 0.28, '0': 0.56, '1': 0.56,
    '2': 0.56, '3': 0.56, '4': 0.56, '5': 0.56, '6': 0.56, '7': 0.56,
    '8': 0.56, '9': 0.56, ':': 0.28, ';': 0.28, '<': 0.60, '=': 0.60,
    '>': 0.60, '?': 0.56,
  };
  private static readonly DEFAULT_WIDTH = 0.56;

  textWidth(text: string, _font: string, fontSize: number): number {
    let width = 0;
    for (const ch of text) {
      width += (ApproximateMeasurement.CHAR_WIDTHS[ch]
                ?? ApproximateMeasurement.DEFAULT_WIDTH) * fontSize;
    }
    return Math.ceil(width);
  }

  lineHeight(_font: string, fontSize: number): number {
    return Math.ceil(fontSize * 1.2);
  }
}
