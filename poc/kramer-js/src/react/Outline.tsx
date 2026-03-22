// SPDX-License-Identifier: Apache-2.0
import React from 'react';
import { type Relation, extractField } from '../core/schema.js';
import { type JustifiedChild, justifyChildren } from '../core/layout.js';
import { type RelationMeasureResult, measureRelation, FixedMeasurement } from '../core/measure.js';
import { Table } from './Table.js';

interface OutlineProps {
  schema: Relation;
  data: unknown[];
  width: number;
  measure: RelationMeasureResult;
}

/**
 * Renders a relation as an outline — vertical list of elements,
 * each with label + data side by side. Nested relations get
 * their own row (full width).
 */
export function Outline({ schema, data, width, measure }: OutlineProps) {
  const primitives = schema.children.filter(c => c.kind === 'primitive');
  const relations = schema.children.filter(c => c.kind === 'relation');

  return (
    <div className="kramer-outline" style={{ width }}>
      {data.map((row, rowIdx) => (
        <div
          key={rowIdx}
          className="kramer-outline-element"
          style={{
            borderBottom: '1px solid #ddd',
            padding: '4px 0',
          }}
        >
          {/* Primitives as label: value pairs */}
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px 16px' }}>
            {primitives.map(child => {
              const values = extractField(row, child.field);
              return (
                <div key={child.field} style={{ display: 'flex', gap: 4 }}>
                  <span style={{ fontWeight: 'bold', minWidth: measure.labelWidth }}>
                    {child.label}
                  </span>
                  <span>{values.map(String).join(', ')}</span>
                </div>
              );
            })}
          </div>

          {/* Nested relations as full-width blocks below */}
          {relations.map(child => {
            if (child.kind !== 'relation') return null;
            const nestedData = extractField(row, child.field) as unknown[];
            const childMeasure = measure.childResults.get(child.field);
            if (!childMeasure || !('childResults' in childMeasure)) return null;

            // Decide mode for nested relation
            const nestedWidth = width - measure.labelWidth - 16;
            const fitsTable = childMeasure.readableTableWidth <= nestedWidth;

            return (
              <div key={child.field} style={{ marginLeft: measure.labelWidth, marginTop: 4 }}>
                <div style={{ fontWeight: 'bold', fontSize: '0.9em', color: '#666' }}>
                  {child.label}
                </div>
                {fitsTable ? (
                  <Table
                    schema={child}
                    data={nestedData}
                    columnWidths={justifyChildren(child, childMeasure, nestedWidth)}
                  />
                ) : (
                  <Outline
                    schema={child}
                    data={nestedData}
                    width={nestedWidth}
                    measure={childMeasure}
                  />
                )}
              </div>
            );
          })}
        </div>
      ))}
    </div>
  );
}
