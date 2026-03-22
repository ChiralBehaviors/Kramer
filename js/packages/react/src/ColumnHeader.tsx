// SPDX-License-Identifier: Apache-2.0

import React from 'react';

export type SortDirection = 'asc' | 'desc' | null;

interface ColumnHeaderProps {
  label: string;
  width: number;
  sortDirection?: SortDirection;
  onSort?: () => void;
  rotateVertical?: boolean;
}

/**
 * Column header with sort indicators and optional vertical rotation.
 * Port of Java ColumnHeader + OutlineElement vertical label logic.
 */
export function ColumnHeader({
  label, width, sortDirection = null, onSort, rotateVertical = false,
}: ColumnHeaderProps) {
  const sortArrow = sortDirection === 'asc' ? ' ▲'
                   : sortDirection === 'desc' ? ' ▼'
                   : '';

  if (rotateVertical) {
    return (
      <div
        className="kramer-column-header kramer-column-header--vertical"
        style={{
          width,
          height: label.length * 8 + 16, // approximate rotated height
          position: 'relative',
          cursor: onSort ? 'pointer' : 'default',
          overflow: 'hidden',
        }}
        onClick={onSort}
      >
        <span
          style={{
            display: 'block',
            transform: 'rotate(-90deg)',
            transformOrigin: 'left bottom',
            position: 'absolute',
            left: width / 2 + 6,
            bottom: 4,
            whiteSpace: 'nowrap',
            fontWeight: 'bold',
            fontSize: '0.85em',
          }}
        >
          {label}{sortArrow}
        </span>
      </div>
    );
  }

  return (
    <th
      className="kramer-column-header"
      style={{
        width,
        padding: '4px 8px',
        borderBottom: '2px solid #ccc',
        textAlign: 'left',
        fontWeight: 'bold',
        cursor: onSort ? 'pointer' : 'default',
        userSelect: 'none',
      }}
      onClick={onSort}
    >
      {label}{sortArrow}
    </th>
  );
}
