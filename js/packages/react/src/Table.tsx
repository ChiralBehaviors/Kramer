// SPDX-License-Identifier: Apache-2.0

import React from 'react';
import type { Relation, MeasurementStrategy } from '@kramer/core';
import { extractField, measureRelation, decideMode, type JustifiedChild } from '@kramer/core';
import { FixedMeasurement } from '@kramer/measurement';

interface TableProps {
  schema: Relation;
  data: unknown[];
  columnWidths: JustifiedChild[];
  measurement?: MeasurementStrategy;
}

export function Table({ schema, data, columnWidths, measurement }: TableProps) {
  const strategy = measurement ?? new FixedMeasurement(7);

  return (
    <table className="kramer-table">
      <thead>
        <tr>
          {schema.children.map((child, i) => (
            <th key={child.field} style={{ width: columnWidths[i]?.width ?? 'auto' }}>
              {child.label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {data.map((row, rowIdx) => (
          <tr key={rowIdx} className={rowIdx % 2 === 0 ? 'kramer-row-even' : 'kramer-row-odd'}>
            {schema.children.map((child, colIdx) => {
              if (child.kind === 'primitive') {
                const val = extractField(row, child.field)[0];
                const isNum = typeof val === 'number';
                return (
                  <td key={child.field}
                      style={isNum ? { fontFamily: 'var(--kr-font-mono)', textAlign: 'right' } : undefined}>
                    {String(val ?? '')}
                  </td>
                );
              }
              // Nested relation — render adaptively (table or outline)
              const nestedData = extractField(row, child.field) as unknown[];
              const cellWidth = columnWidths[colIdx]?.width ?? 300;
              return (
                <td key={child.field} style={{ padding: '4px 6px', verticalAlign: 'top' }}>
                  <NestedRelation schema={child} data={nestedData}
                                  width={cellWidth} measurement={strategy} />
                </td>
              );
            })}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function NestedRelation({ schema, data, width, measurement }: {
  schema: Relation; data: unknown[]; width: number; measurement: MeasurementStrategy;
}) {
  if (!data || data.length === 0) return null;

  const measure = measureRelation(schema, data, measurement);
  const mode = decideMode(measure.readableTableWidth, width);

  if (mode === 'OUTLINE') {
    // Compact outline for narrow cells
    const prims = schema.children.filter(c => c.kind === 'primitive');
    const rels = schema.children.filter(c => c.kind === 'relation') as Relation[];
    return (
      <div className="kramer-nested-outline">
        {data.map((item, idx) => (
          <div key={idx} className="kramer-nested-outline-item">
            <div className="kramer-nested-outline-fields">
              {prims.map(p => {
                const v = extractField(item, p.field)[0];
                return (
                  <div key={p.field} className="kramer-nested-outline-field">
                    <span className="kramer-nested-outline-label">{p.label}</span>
                    <span className={typeof v === 'number' ? 'kramer-mono' : ''}>{String(v ?? '')}</span>
                  </div>
                );
              })}
            </div>
            {rels.map(rel => {
              const nested = extractField(item, rel.field) as unknown[];
              if (!nested?.length) return null;
              return (
                <div key={rel.field} style={{ marginTop: 4, marginLeft: 8 }}>
                  <div className="kramer-outline-relation-label">{rel.label}</div>
                  <NestedRelation schema={rel} data={nested} width={width - 16} measurement={measurement} />
                </div>
              );
            })}
          </div>
        ))}
      </div>
    );
  }

  // TABLE mode
  const leafChildren = schema.children.filter(c => c.kind === 'primitive');
  const relChildren = schema.children.filter(c => c.kind === 'relation') as Relation[];

  return (
    <table className="kramer-nested-table">
      <thead>
        <tr>
          {leafChildren.map(gc => <th key={gc.field}>{gc.label}</th>)}
          {relChildren.map(rc => <th key={rc.field}>{rc.label}</th>)}
        </tr>
      </thead>
      <tbody>
        {data.map((item, idx) => (
          <tr key={idx}>
            {leafChildren.map(gc => {
              const v = extractField(item, gc.field)[0];
              const isN = typeof v === 'number';
              return (
                <td key={gc.field}
                    style={isN ? { fontFamily: 'var(--kr-font-mono)', textAlign: 'right' } : undefined}>
                  {String(v ?? '')}
                </td>
              );
            })}
            {relChildren.map(rc => {
              const nested = extractField(item, rc.field) as unknown[];
              if (!nested?.length) return <td key={rc.field} />;
              const cellW = width / (leafChildren.length + relChildren.length);
              return (
                <td key={rc.field} style={{ padding: '3px 4px', verticalAlign: 'top' }}>
                  <NestedRelation schema={rc} data={nested} width={cellW} measurement={measurement} />
                </td>
              );
            })}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
