// SPDX-License-Identifier: Apache-2.0

import React, { useRef } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import type { Relation, MeasurementStrategy } from '@kramer/core';
import { extractField, measureRelation, decideMode } from '@kramer/core';
import { FixedMeasurement } from '@kramer/measurement';

interface VirtualOutlineProps {
  schema: Relation;
  data: unknown[];
  width: number;
  estimateRowHeight?: number;
  measurement?: MeasurementStrategy;
}

export function VirtualOutline({ schema, data, width, estimateRowHeight = 120, measurement }: VirtualOutlineProps) {
  const parentRef = useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: data.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => estimateRowHeight,
    overscan: 3,
  });

  const primitives = schema.children.filter(c => c.kind === 'primitive');
  const relations = schema.children.filter(c => c.kind === 'relation') as Relation[];

  return (
    <div ref={parentRef} className="kramer-virtual-outline" style={{ height: '100%', overflow: 'auto', width }}>
      <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
        {virtualizer.getVirtualItems().map(virtualRow => {
          const row = data[virtualRow.index];
          return (
            <div
              key={virtualRow.key}
              data-index={virtualRow.index}
              ref={virtualizer.measureElement}
              style={{
                position: 'absolute', top: 0, left: 0, width: '100%',
                transform: `translateY(${virtualRow.start}px)`,
              }}
            >
              <OutlineRow row={row} primitives={primitives} relations={relations}
                          width={width} measurement={measurement} />
            </div>
          );
        })}
      </div>
    </div>
  );
}

interface OutlineRowProps {
  row: unknown;
  primitives: { field: string; label: string }[];
  relations: Relation[];
  width: number;
  measurement?: MeasurementStrategy;
}

function OutlineRow({ row, primitives, relations, width, measurement }: OutlineRowProps) {
  const strategy = measurement ?? new FixedMeasurement(7);
  // Available width for nested content (minus card padding/border)
  const innerWidth = width - 28;

  return (
    <div className="kramer-outline-row">
      <div className="kramer-outline-header">
        {primitives.map(child => {
          const values = extractField(row, child.field);
          return (
            <div key={child.field} className="kramer-outline-field">
              <span className="kramer-outline-label">{child.label}</span>
              <span className="kramer-outline-value">{values.map(String).join(', ')}</span>
            </div>
          );
        })}
      </div>

      {relations.map(rel => {
        const nestedData = extractField(row, rel.field) as unknown[];
        if (!nestedData || nestedData.length === 0) return null;
        return (
          <div key={rel.field} className="kramer-outline-nested">
            <div className="kramer-outline-relation-label">{rel.label}</div>
            <AdaptiveRelation schema={rel} data={nestedData}
                              width={innerWidth} depth={2} measurement={strategy} />
          </div>
        );
      })}
    </div>
  );
}

/**
 * Renders a relation as TABLE or OUTLINE based on available width.
 * Recurses for arbitrary nesting depth.
 */
function AdaptiveRelation({ schema, data, width, depth, measurement }: {
  schema: Relation; data: unknown[]; width: number; depth: number;
  measurement: MeasurementStrategy;
}) {
  const measure = measureRelation(schema, data, measurement);
  const mode = decideMode(measure.readableTableWidth, width);

  if (mode === 'TABLE') {
    return <RecursiveTable schema={schema} data={data} depth={depth}
                           width={width} measurement={measurement} />;
  }

  // OUTLINE: render each item as a mini-card with primitives + nested relations
  const primitives = schema.children.filter(c => c.kind === 'primitive');
  const relations = schema.children.filter(c => c.kind === 'relation') as Relation[];

  return (
    <div className="kramer-nested-outline">
      {data.map((item, idx) => (
        <div key={idx} className="kramer-nested-outline-item">
          <div className="kramer-nested-outline-fields">
            {primitives.map(child => {
              const val = extractField(item, child.field)[0];
              const isNum = typeof val === 'number';
              return (
                <div key={child.field} className="kramer-nested-outline-field">
                  <span className="kramer-nested-outline-label">{child.label}</span>
                  <span className={isNum ? 'kramer-mono' : ''}>{String(val ?? '')}</span>
                </div>
              );
            })}
          </div>
          {relations.map(rel => {
            const nested = extractField(item, rel.field) as unknown[];
            if (!nested || nested.length === 0) return null;
            return (
              <div key={rel.field} className="kramer-outline-nested" style={{ marginTop: 4 }}>
                <div className="kramer-outline-relation-label">{rel.label}</div>
                <AdaptiveRelation schema={rel} data={nested}
                                  width={width - 20} depth={depth + 1} measurement={measurement} />
              </div>
            );
          })}
        </div>
      ))}
    </div>
  );
}

function RecursiveTable({ schema, data, depth, width, measurement }: {
  schema: Relation; data: unknown[]; depth: number; width: number;
  measurement: MeasurementStrategy;
}) {
  const primitiveChildren = schema.children.filter(c => c.kind === 'primitive');
  const relationChildren = schema.children.filter(c => c.kind === 'relation') as Relation[];

  return (
    <table className={`kramer-inner-table ${depth >= 3 ? 'kramer-depth-3' : ''}`}>
      <thead>
        <tr>
          {primitiveChildren.map(child => <th key={child.field}>{child.label}</th>)}
          {relationChildren.map(rel => <th key={rel.field}>{rel.label}</th>)}
        </tr>
      </thead>
      <tbody>
        {data.map((item, idx) => (
          <tr key={idx}>
            {primitiveChildren.map(child => {
              const val = extractField(item, child.field)[0];
              const isNum = typeof val === 'number';
              return (
                <td key={child.field}
                    style={isNum ? { fontFamily: 'var(--kr-font-mono)', textAlign: 'right' } : undefined}>
                  {String(val ?? '')}
                </td>
              );
            })}
            {relationChildren.map(rel => {
              const nested = extractField(item, rel.field) as unknown[];
              if (!nested || nested.length === 0) return <td key={rel.field} />;
              return (
                <td key={rel.field} style={{ padding: '4px 6px' }}>
                  <AdaptiveRelation schema={rel} data={nested}
                                    width={width / (primitiveChildren.length + relationChildren.length)}
                                    depth={depth + 1} measurement={measurement} />
                </td>
              );
            })}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
