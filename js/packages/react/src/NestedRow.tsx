// SPDX-License-Identifier: Apache-2.0

import React from 'react';
import type { SchemaNode, Relation } from '@kramer/core';
import { extractField } from '@kramer/core';
import type { JustifiedChild } from '@kramer/core';

interface NestedRowProps {
  row: unknown;
  schema: Relation;
  columnWidths: JustifiedChild[];
  rowIndex: number;
}

/**
 * A single row in a nested table. Renders primitive values inline
 * and relation children as nested sub-tables.
 */
export function NestedRow({ row, schema, columnWidths, rowIndex }: NestedRowProps) {
  return (
    <tr
      className={`kramer-nested-row ${rowIndex % 2 === 0 ? 'kramer-row-even' : 'kramer-row-odd'}`}
      style={{ borderBottom: '1px solid #eee' }}
    >
      {schema.children.map((child, colIdx) => (
        <NestedCell
          key={child.field}
          child={child}
          row={row}
          width={columnWidths[colIdx]?.width ?? 100}
        />
      ))}
    </tr>
  );
}

interface NestedCellProps {
  child: SchemaNode;
  row: unknown;
  width: number;
}

/**
 * A single cell in a nested table row.
 * Primitives render their value; relations render a sub-table.
 */
function NestedCell({ child, row, width }: NestedCellProps) {
  return (
    <td
      className="kramer-nested-cell"
      style={{ width, padding: '4px 8px', verticalAlign: 'top' }}
    >
      {child.kind === 'primitive'
        ? renderValue(row, child.field)
        : renderSubTable(row, child)}
    </td>
  );
}

function renderValue(row: unknown, field: string): React.ReactNode {
  const values = extractField(row, field);
  return values.map(String).join(', ');
}

function renderSubTable(row: unknown, rel: Relation): React.ReactNode {
  const nestedData = extractField(row, rel.field) as unknown[];
  if (!nestedData || nestedData.length === 0) return null;

  const leafChildren = rel.children.filter(c => c.kind === 'primitive');
  return (
    <table className="kramer-sub-table" style={{ borderCollapse: 'collapse', width: '100%' }}>
      <thead>
        <tr>
          {leafChildren.map(gc => (
            <th key={gc.field} style={{ padding: '2px 4px', borderBottom: '1px solid #ddd', fontSize: '0.85em' }}>
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

export { NestedCell };
