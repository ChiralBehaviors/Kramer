// SPDX-License-Identifier: Apache-2.0
import React from 'react';
import { type Relation, type Primitive, extractField } from '../core/schema.js';
import { type JustifiedChild } from '../core/layout.js';

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
                  ? renderPrimitiveValue(row, child)
                  : renderNestedRelation(row, child, columnWidths[colIdx]?.width ?? 200)}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function renderPrimitiveValue(row: unknown, child: Primitive): React.ReactNode {
  const values = extractField(row, child.field);
  return values.map(String).join(', ');
}

function renderNestedRelation(row: unknown, child: Relation, _width: number): React.ReactNode {
  const nestedData = extractField(row, child.field);
  // Simple nested table — no recursive justify for PoC
  return (
    <table className="kramer-nested-table" style={{ borderCollapse: 'collapse', width: '100%' }}>
      <thead>
        <tr>
          {child.children.map(gc => (
            <th key={gc.field} style={{ padding: '2px 4px', borderBottom: '1px solid #ddd', fontSize: '0.9em' }}>
              {gc.label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {(nestedData as unknown[]).map((item, idx) => (
          <tr key={idx}>
            {child.children.map(gc => (
              <td key={gc.field} style={{ padding: '2px 4px' }}>
                {gc.kind === 'primitive'
                  ? String(extractField(item, gc.field)[0] ?? '')
                  : '...'}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
