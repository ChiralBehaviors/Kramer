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
 * Flattened table renderer — expands nested relation columns inline
 * rather than nesting tables-in-cells. Produces a single unified
 * table with grouped column headers.
 */
export function Table({ schema, data, columnWidths }: TableProps) {
  // Build flat column list, expanding relations into their children
  const columns = buildColumns(schema);
  const hasNestedHeaders = columns.some(c => c.parent !== null);

  return (
    <table className="kramer-table">
      <thead>
        {/* Group header row (shows relation names spanning their children) */}
        {hasNestedHeaders && (
          <tr>
            {schema.children.map(child => {
              if (child.kind === 'primitive') {
                return <th key={child.field} rowSpan={2} style={{ verticalAlign: 'bottom' }}>{child.label}</th>;
              }
              const leafCount = child.children.filter(c => c.kind === 'primitive').length;
              // Count nested relations' leaves too
              const totalLeaves = countLeaves(child);
              return (
                <th key={child.field} colSpan={totalLeaves}
                    style={{ textAlign: 'center', borderBottom: '1px solid rgba(255,255,255,0.15)' }}>
                  {child.label}
                </th>
              );
            })}
          </tr>
        )}
        {/* Leaf column header row */}
        {hasNestedHeaders && (
          <tr>
            {columns.filter(c => c.parent !== null).map(col => (
              <th key={col.path}>{col.label}</th>
            ))}
          </tr>
        )}
        {/* Simple header (no nesting) */}
        {!hasNestedHeaders && (
          <tr>
            {columns.map(col => (
              <th key={col.path}>{col.label}</th>
            ))}
          </tr>
        )}
      </thead>
      <tbody>
        {data.map((row, rowIdx) => (
          <FlatRow key={rowIdx} row={row} columns={columns} schema={schema} rowIndex={rowIdx} />
        ))}
      </tbody>
    </table>
  );
}

interface FlatColumn {
  field: string;
  label: string;
  path: string; // unique key
  parent: string | null; // parent relation field, null for top-level primitives
  extractPath: string[]; // path segments to extract value from row
  isNumeric?: boolean;
}

function buildColumns(schema: Relation, parentField: string | null = null, pathPrefix: string[] = []): FlatColumn[] {
  const cols: FlatColumn[] = [];
  for (const child of schema.children) {
    const extractPath = [...pathPrefix, child.field];
    if (child.kind === 'primitive') {
      cols.push({
        field: child.field,
        label: child.label,
        path: extractPath.join('.'),
        parent: parentField,
        extractPath,
      });
    } else {
      // Expand relation children recursively
      cols.push(...buildColumns(child, child.field, extractPath));
    }
  }
  return cols;
}

function countLeaves(node: Relation): number {
  let count = 0;
  for (const child of node.children) {
    if (child.kind === 'primitive') count++;
    else count += countLeaves(child);
  }
  return count;
}

function FlatRow({ row, columns, schema, rowIndex }: {
  row: unknown; columns: FlatColumn[]; schema: Relation; rowIndex: number;
}) {
  // For nested relations, we need to handle multiple rows per parent row
  // Find the max cardinality of nested relations
  const maxRows = getMaxNestedRows(row, schema);

  if (maxRows <= 1) {
    // Simple case: one row
    return (
      <tr className={rowIndex % 2 === 0 ? 'kramer-row-even' : 'kramer-row-odd'}>
        {columns.map(col => {
          const val = extractNested(row, col.extractPath, 0);
          const isNum = typeof val === 'number';
          return (
            <td key={col.path}
                style={isNum ? { fontFamily: 'var(--kr-font-mono)', textAlign: 'right' } : undefined}>
              {val != null ? String(val) : ''}
            </td>
          );
        })}
      </tr>
    );
  }

  // Multiple nested rows: first row has rowSpan on parent primitives
  return (
    <>
      {Array.from({ length: maxRows }, (_, nestedIdx) => (
        <tr key={nestedIdx}
            className={rowIndex % 2 === 0 ? 'kramer-row-even' : 'kramer-row-odd'}
            style={nestedIdx === maxRows - 1 ? { borderBottom: '2px solid var(--kr-border-strong, #cbd5e1)' } : undefined}>
          {columns.map(col => {
            // Top-level primitives: only render on first nested row with rowSpan
            if (col.parent === null || col.extractPath.length === 1) {
              if (nestedIdx > 0) return null; // skip — covered by rowSpan
              const val = extractNested(row, col.extractPath, 0);
              const isNum = typeof val === 'number';
              return (
                <td key={col.path} rowSpan={maxRows}
                    style={{
                      verticalAlign: 'top',
                      ...(isNum ? { fontFamily: 'var(--kr-font-mono)', textAlign: 'right' } : {}),
                    }}>
                  {val != null ? String(val) : ''}
                </td>
              );
            }
            // Nested primitive: extract from the nth nested item
            const val = extractNested(row, col.extractPath, nestedIdx);
            const isNum = typeof val === 'number';
            return (
              <td key={col.path}
                  style={isNum ? { fontFamily: 'var(--kr-font-mono)', textAlign: 'right' } : undefined}>
                {val != null ? String(val) : ''}
              </td>
            );
          })}
        </tr>
      ))}
    </>
  );
}

function extractNested(row: unknown, path: string[], nestedIndex: number): unknown {
  let current: unknown = row;
  for (let i = 0; i < path.length; i++) {
    if (current == null) return null;
    if (Array.isArray(current)) {
      // We've hit an array — take the nestedIndex item and continue
      current = current[nestedIndex];
      if (current == null) return null;
      // Re-extract the current field
      current = (current as Record<string, unknown>)[path[i]];
    } else if (typeof current === 'object') {
      const next = (current as Record<string, unknown>)[path[i]];
      if (Array.isArray(next)) {
        // This is the array level — take nestedIndex and continue
        current = next[nestedIndex];
      } else {
        current = next;
      }
    } else {
      return null;
    }
  }
  return current;
}

function getMaxNestedRows(row: unknown, schema: Relation): number {
  let max = 1;
  for (const child of schema.children) {
    if (child.kind === 'relation') {
      const arr = extractField(row, child.field);
      max = Math.max(max, arr.length);
    }
  }
  return max;
}
