// SPDX-License-Identifier: Apache-2.0

import React, { useRef, useState, useEffect, useMemo } from 'react';
import type { Relation, MeasurementStrategy } from '@kramer/core';
import {
  measureRelation, buildConstraintTree, solveConstraints,
  justifyChildren,
} from '@kramer/core';
import { FixedMeasurement } from '@kramer/measurement';
import { Table } from './Table.js';
import { VirtualOutline } from './VirtualOutline.js';

interface AutoLayoutProps {
  schema: Relation;
  data: unknown[];
  measurement?: MeasurementStrategy;
  height?: number;
}

/**
 * Top-level autolayout component. Measures data, decides table vs outline
 * mode based on available width, and renders accordingly.
 * Re-layouts on resize via ResizeObserver.
 */
export function AutoLayout({ schema, data, measurement, height = 600 }: AutoLayoutProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(800);

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

  const measure = useMemo(
    () => measureRelation(schema, data, strategy),
    [schema, data, strategy],
  );

  const constraint = useMemo(
    () => buildConstraintTree(schema, measure, width),
    [schema, measure, width],
  );

  const assignments = useMemo(
    () => solveConstraints(constraint),
    [constraint],
  );

  const rootMode = assignments.get(schema.field) ?? 'OUTLINE';

  const columnWidths = useMemo(
    () => justifyChildren(schema, measure, width),
    [schema, measure, width],
  );

  return (
    <div
      ref={containerRef}
      className="kramer-auto-layout"
      style={{ width: '100%', height, overflow: 'hidden' }}
    >
      <div style={{ fontSize: '0.8em', color: '#999', marginBottom: 4 }}>
        {width}px — {rootMode} mode
        (readable: {Math.round(measure.readableTableWidth)}px)
      </div>
      {rootMode === 'TABLE' ? (
        <Table schema={schema} data={data} columnWidths={columnWidths} />
      ) : (
        <VirtualOutline schema={schema} data={data} width={width} />
      )}
    </div>
  );
}
