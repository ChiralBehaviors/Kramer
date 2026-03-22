// SPDX-License-Identifier: Apache-2.0

/**
 * Virtualized outline renderer using @tanstack/react-virtual.
 * Spike: validates nested virtualization pattern (outer outline + inner tables).
 */

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

/**
 * Outer virtualizer: renders outline rows (one per data item).
 * Each row contains primitive label:value pairs + nested relation tables.
 * Inner tables are non-virtualized (<50 rows per RF-2 research).
 */
export function VirtualOutline({ schema, data, width, estimateRowHeight = 100 }: VirtualOutlineProps) {
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
    <div
      ref={parentRef}
      className="kramer-virtual-outline"
      style={{ height: '100%', overflow: 'auto', width }}
    >
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
              <OutlineRow
                row={row}
                primitives={primitives}
                relations={relations}
                width={width}
              />
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
}

function OutlineRow({ row, primitives, relations, width }: OutlineRowProps) {
  return (
    <div className="kramer-outline-row" style={{ borderBottom: '1px solid #ddd', padding: '4px 0' }}>
      {/* Primitive label:value pairs */}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px 16px' }}>
        {primitives.map(child => {
          const values = extractField(row, child.field);
          return (
            <div key={child.field} style={{ display: 'flex', gap: 4 }}>
              <span style={{ fontWeight: 'bold' }}>{child.label}</span>
              <span>{values.map(String).join(', ')}</span>
            </div>
          );
        })}
      </div>

      {/* Nested relations as non-virtualized tables */}
      {relations.map(rel => {
        const nestedData = extractField(row, rel.field) as unknown[];
        if (!nestedData || nestedData.length === 0) return null;
        return (
          <div key={rel.field} style={{ marginLeft: 16, marginTop: 4 }}>
            <div style={{ fontWeight: 'bold', fontSize: '0.9em', color: '#666' }}>
              {rel.label}
            </div>
            <InnerTable schema={rel} data={nestedData} />
          </div>
        );
      })}
    </div>
  );
}

interface InnerTableProps {
  schema: Relation;
  data: unknown[];
}

/**
 * Non-virtualized inner table (for <50 rows).
 * measureElement on the outer virtualizer auto-remeasures when this expands.
 */
function InnerTable({ schema, data }: InnerTableProps) {
  const leafChildren = schema.children.filter(c => c.kind === 'primitive');

  return (
    <table className="kramer-inner-table" style={{ borderCollapse: 'collapse', width: '100%' }}>
      <thead>
        <tr>
          {leafChildren.map(child => (
            <th key={child.field} style={{ padding: '2px 6px', borderBottom: '1px solid #ccc', textAlign: 'left', fontSize: '0.85em' }}>
              {child.label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {data.map((item, idx) => (
          <tr key={idx}>
            {leafChildren.map(child => (
              <td key={child.field} style={{ padding: '2px 6px' }}>
                {String(extractField(item, child.field)[0] ?? '')}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
