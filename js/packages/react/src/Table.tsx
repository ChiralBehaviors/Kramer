// SPDX-License-Identifier: Apache-2.0

import React from 'react';
import type { Relation } from '@kramer/core';
import { extractField, type JustifiedChild } from '@kramer/core';

interface TableProps {
  schema: Relation;
  data: unknown[];
  columnWidths: JustifiedChild[];
}

/**
 * Renders a relation as a nested table with column headers and data rows.
 */
export function Table({ schema, data, columnWidths }: TableProps) {
  return (
    <table className="kramer-table" style={{ borderCollapse: 'collapse', width: '100%' }}>
      <thead>
        <tr>
          {schema.children.map((child, i) => (
            <th
              key={child.field}
              style={{
                width: columnWidths[i]?.width ?? 'auto',
                padding: '4px 8px',
                borderBottom: '2px solid #ccc',
                textAlign: 'left',
                fontWeight: 'bold',
              }}
            >
              {child.label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {data.map((row, rowIdx) => (
          <tr key={rowIdx} style={{ borderBottom: '1px solid #eee' }}>
            {schema.children.map((child, colIdx) => (
              <td
                key={child.field}
                style={{
                  width: columnWidths[colIdx]?.width ?? 'auto',
                  padding: '4px 8px',
                  verticalAlign: 'top',
                }}
              >
                {child.kind === 'primitive'
                  ? String(extractField(row, child.field)[0] ?? '')
                  : renderNestedRelation(row, child)}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function renderNestedRelation(row: unknown, child: Relation): React.ReactNode {
  const nestedData = extractField(row, child.field) as unknown[];
  const leafChildren = child.children.filter(c => c.kind === 'primitive');
  return (
    <table className="kramer-nested-table" style={{ borderCollapse: 'collapse', width: '100%' }}>
      <thead>
        <tr>
          {leafChildren.map(gc => (
            <th key={gc.field} style={{ padding: '2px 4px', borderBottom: '1px solid #ddd', fontSize: '0.9em' }}>
              {gc.label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {nestedData.map((item, idx) => (
          <tr key={idx}>
            {leafChildren.map(gc => (
              <td key={gc.field} style={{ padding: '2px 4px' }}>
                {String(extractField(item, gc.field)[0] ?? '')}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
