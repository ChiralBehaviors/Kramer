// SPDX-License-Identifier: Apache-2.0
import React, { useRef, useState, useEffect, useMemo } from 'react';
import { type Relation } from '../core/schema.js';
import { type MeasurementStrategy } from '../core/types.js';
import { measureRelation, FixedMeasurement } from '../core/measure.js';
import { layoutRelation, justifyChildren } from '../core/layout.js';
import { Table } from './Table.js';
import { Outline } from './Outline.js';

interface AutoLayoutProps {
  schema: Relation;
  data: unknown[];
  measurement?: MeasurementStrategy;
}

/**
 * Top-level autolayout component. Measures data, decides table vs outline
 * mode based on available width, and renders accordingly.
 * Re-layouts on resize.
 */
export function AutoLayout({ schema, data, measurement }: AutoLayoutProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(800);

  // Track container width
  useEffect(() => {
    if (!containerRef.current) return;
    const observer = new ResizeObserver(entries => {
      for (const entry of entries) {
        setWidth(Math.floor(entry.contentRect.width));
      }
    });
    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, []);

  const strategy = measurement ?? new FixedMeasurement(7);

  // Measure schema against data
  const measure = useMemo(
    () => measureRelation(schema, data, strategy),
    [schema, data, strategy],
  );

  // Layout decision
  const layoutResult = useMemo(
    () => layoutRelation(schema, measure, width),
    [schema, measure, width],
  );

  // Justify column widths
  const columnWidths = useMemo(
    () => justifyChildren(schema, measure, width),
    [schema, measure, width],
  );

  return (
    <div
      ref={containerRef}
      className="kramer-auto-layout"
      style={{ width: '100%', overflow: 'hidden' }}
    >
      <div style={{ fontSize: '0.8em', color: '#999', marginBottom: 4 }}>
        {width}px — {layoutResult.mode} mode
        (readable: {Math.round(measure.readableTableWidth)}px)
      </div>
      {layoutResult.mode === 'table' ? (
        <Table schema={schema} data={data} columnWidths={columnWidths} />
      ) : (
        <Outline schema={schema} data={data} width={width} measure={measure} />
      )}
    </div>
  );
}
