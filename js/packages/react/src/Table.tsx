// SPDX-License-Identifier: Apache-2.0

import React from 'react';
import type { Relation } from '@kramer/core';
import { extractField, type JustifiedChild } from '@kramer/core';

interface TableProps {
  schema: Relation;
  data: unknown[];
  columnWidths: JustifiedChild[];
}

export function Table({ schema, data, columnWidths }: TableProps) {
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
                      style={{
                        width: columnWidths[colIdx]?.width ?? 'auto',
                        ...(isNum ? { fontFamily: 'var(--kr-font-mono)', textAlign: 'right' } : {}),
                      }}>
                    {String(val ?? '')}
                  </td>
                );
              }
              // Nested relation
              const nestedData = extractField(row, child.field) as unknown[];
              const leafChildren = child.children.filter(c => c.kind === 'primitive');
              return (
                <td key={child.field} style={{ width: columnWidths[colIdx]?.width ?? 'auto', padding: '4px 6px' }}>
                  <table className="kramer-nested-table">
                    <thead>
                      <tr>
                        {leafChildren.map(gc => <th key={gc.field}>{gc.label}</th>)}
                      </tr>
                    </thead>
                    <tbody>
                      {nestedData.map((item, idx) => (
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
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </td>
              );
            })}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
