// SPDX-License-Identifier: Apache-2.0

import React from 'react';
import type { SchemaPath } from '@kramer/core';
import { pathToString, type LayoutQueryState, type FieldState } from '@kramer/core';

interface FieldInspectorProps {
  path: SchemaPath | null;
  queryState: LayoutQueryState;
}

/**
 * Detail panel showing all FieldState properties for the selected field.
 * Port of Java FieldInspectorPanel.
 */
export function FieldInspector({ path, queryState }: FieldInspectorProps) {
  if (!path) {
    return (
      <div className="kramer-field-inspector" style={{ padding: 8, color: '#999' }}>
        Select a field to inspect
      </div>
    );
  }

  const state = queryState.getFieldState(path);

  return (
    <div className="kramer-field-inspector" style={{ padding: 8, fontSize: '0.9em' }}>
      <div style={{ fontWeight: 'bold', marginBottom: 8 }}>
        {pathToString(path)}
      </div>
      <PropertyRow label="Visible" value={state.visible ? 'Yes' : 'No'} />
      <PropertyRow label="Sort" value={state.sortDirection ?? 'None'} />
      <PropertyRow label="Filter" value={state.filterExpression ?? 'None'} />
      <PropertyRow label="Formula" value={state.formulaExpression ?? 'None'} />
      <PropertyRow label="Aggregate" value={state.aggregateExpression ?? 'None'} />
      <PropertyRow label="Render Mode" value={state.renderMode ?? 'Auto'} />
      <PropertyRow label="Hide if Empty" value={state.hideIfEmpty ? 'Yes' : 'No'} />
      <PropertyRow label="Frozen" value={state.frozen ? 'Yes' : 'No'} />
      <PropertyRow label="Column Width" value={state.columnWidth != null ? `${state.columnWidth}px` : 'Auto'} />
    </div>
  );
}

function PropertyRow({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '2px 0', borderBottom: '1px solid #f0f0f0' }}>
      <span style={{ color: '#666' }}>{label}</span>
      <span>{value}</span>
    </div>
  );
}
