// SPDX-License-Identifier: Apache-2.0

import React, { useRef } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import type { Relation } from '@kramer/core';
import { extractField } from '@kramer/core';

interface VirtualOutlineProps {
  schema: Relation;
  data: unknown[];
  width: number;
  estimateRowHeight?: number;
}

export function VirtualOutline({ schema, data, width, estimateRowHeight = 120 }: VirtualOutlineProps) {
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
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                transform: `translateY(${virtualRow.start}px)`,
              }}
            >
              <OutlineRow row={row} primitives={primitives} relations={relations} />
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
}

function OutlineRow({ row, primitives, relations }: OutlineRowProps) {
  return (
    <div className="kramer-outline-row">
      {/* Header bar with primitive fields */}
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

      {/* Nested relations */}
      {relations.map(rel => {
        const nestedData = extractField(row, rel.field) as unknown[];
        if (!nestedData || nestedData.length === 0) return null;
        return (
          <div key={rel.field} className="kramer-outline-nested">
            <div className="kramer-outline-relation-label">{rel.label}</div>
            <RecursiveTable schema={rel} data={nestedData} depth={2} />
          </div>
        );
      })}
    </div>
  );
}

/** Recursively render nested tables — handles arbitrary nesting depth */
function RecursiveTable({ schema, data, depth }: { schema: Relation; data: unknown[]; depth: number }) {
  const primitiveChildren = schema.children.filter(c => c.kind === 'primitive');
  const relationChildren = schema.children.filter(c => c.kind === 'relation') as Relation[];

  return (
    <table className={`kramer-inner-table ${depth >= 3 ? 'kramer-depth-3' : ''}`}>
      <thead>
        <tr>
          {primitiveChildren.map(child => (
            <th key={child.field}>{child.label}</th>
          ))}
          {relationChildren.map(rel => (
            <th key={rel.field}>{rel.label}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {data.map((item, idx) => (
          <tr key={idx}>
            {primitiveChildren.map(child => {
              const val = extractField(item, child.field)[0];
              const isNum = typeof val === 'number';
              return (
                <td key={child.field} data-type={isNum ? 'number' : undefined}
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
                  <RecursiveTable schema={rel} data={nested} depth={depth + 1} />
                </td>
              );
            })}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
