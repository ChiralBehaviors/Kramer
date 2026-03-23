// SPDX-License-Identifier: Apache-2.0

import React, { useState, useCallback } from 'react';
import { relation, primitive, schemaPath, LayoutQueryState, type SchemaPath } from '@kramer/core';
import { AutoLayout } from '../src/AutoLayout.js';
import { FieldSelector, FieldInspector } from '@kramer/react-ui';
import '../src/kramer.css';

const schema = relation('departments',
  primitive('name'),
  primitive('building'),
  relation('courses',
    primitive('number'),
    primitive('title'),
    primitive('credits'),
    relation('sections',
      primitive('id'),
      primitive('enrolled'),
    ),
  ),
);

const data = [
  {
    name: 'Computer Science', building: 'Gates Hall',
    courses: [
      { number: 'CS101', title: 'Intro to Programming', credits: 3,
        sections: [{ id: 'A', enrolled: 45 }, { id: 'B', enrolled: 42 }, { id: 'C', enrolled: 38 }] },
      { number: 'CS201', title: 'Data Structures', credits: 4,
        sections: [{ id: 'A', enrolled: 35 }, { id: 'B', enrolled: 30 }] },
      { number: 'CS301', title: 'Algorithms', credits: 4,
        sections: [{ id: 'A', enrolled: 28 }] },
      { number: 'CS410', title: 'Machine Learning', credits: 3,
        sections: [{ id: 'A', enrolled: 40 }, { id: 'B', enrolled: 38 }] },
    ],
  },
  {
    name: 'Mathematics', building: 'Hilbert Hall',
    courses: [
      { number: 'MATH101', title: 'Calculus I', credits: 4,
        sections: [{ id: 'A', enrolled: 50 }, { id: 'B', enrolled: 48 }, { id: 'C', enrolled: 45 }] },
      { number: 'MATH201', title: 'Linear Algebra', credits: 3,
        sections: [{ id: 'A', enrolled: 35 }] },
      { number: 'MATH301', title: 'Real Analysis', credits: 3,
        sections: [{ id: 'A', enrolled: 22 }] },
    ],
  },
  {
    name: 'Physics', building: 'Newton Building',
    courses: [
      { number: 'PHYS101', title: 'Mechanics', credits: 4,
        sections: [{ id: 'A', enrolled: 55 }, { id: 'B', enrolled: 50 }] },
      { number: 'PHYS201', title: 'E&M', credits: 4,
        sections: [{ id: 'A', enrolled: 40 }] },
      { number: 'PHYS310', title: 'Quantum Mechanics', credits: 3,
        sections: [{ id: 'A', enrolled: 25 }] },
    ],
  },
  {
    name: 'English', building: 'Austen Hall',
    courses: [
      { number: 'ENG101', title: 'Composition', credits: 3,
        sections: [{ id: 'A', enrolled: 25 }, { id: 'B', enrolled: 25 }, { id: 'C', enrolled: 24 }] },
      { number: 'ENG201', title: 'American Lit', credits: 3,
        sections: [{ id: 'A', enrolled: 30 }] },
      { number: 'ENG350', title: 'Shakespeare', credits: 3,
        sections: [{ id: 'A', enrolled: 28 }] },
    ],
  },
  {
    name: 'Economics', building: 'Smith Hall',
    courses: [
      { number: 'ECON101', title: 'Microeconomics', credits: 3,
        sections: [{ id: 'A', enrolled: 55 }, { id: 'B', enrolled: 50 }] },
      { number: 'ECON201', title: 'Macroeconomics', credits: 3,
        sections: [{ id: 'A', enrolled: 45 }] },
    ],
  },
];

export default function App() {
  const [queryState] = useState(() => new LayoutQueryState());
  const [showFields, setShowFields] = useState(false);
  const [showInspector, setShowInspector] = useState(false);
  const [selectedPath, setSelectedPath] = useState<SchemaPath>(schemaPath('departments'));

  const handleFieldSelected = useCallback((path: SchemaPath) => {
    setSelectedPath(path);
    if (!showInspector) setShowInspector(true);
  }, [showInspector]);

  return (
    <div style={{
      height: '100vh',
      display: 'flex',
      flexDirection: 'column',
      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      color: '#1a1a1a',
    }}>
      {/* Header */}
      <header style={{
        padding: '10px 20px',
        borderBottom: '1px solid #e4e7ec',
        background: 'linear-gradient(to bottom, #fff, #f9fafb)',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexShrink: 0,
      }}>
        <div>
          <h1 style={{ fontSize: 17, margin: 0, fontWeight: 700, color: '#101828' }}>
            Kramer <span style={{ color: '#3b82f6' }}>JS</span>
          </h1>
          <p style={{ fontSize: 12, margin: '2px 0 0', color: '#667085' }}>
            Course Catalog — resize window to see table ↔ outline switching
          </p>
        </div>
        <div style={{ display: 'flex', gap: 6 }}>
          <ToolbarButton active={showFields} onClick={() => setShowFields(!showFields)} label="Fields" shortcut="⇧⌘F" />
          <ToolbarButton active={showInspector} onClick={() => setShowInspector(!showInspector)} label="Inspector" shortcut="⇧⌘I" />
        </div>
      </header>

      {/* Main content */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {/* Field selector panel */}
        {showFields && (
          <div style={{
            width: 220,
            borderRight: '1px solid #e4e7ec',
            padding: '12px 10px',
            overflow: 'auto',
            background: '#fafbfc',
            flexShrink: 0,
          }}>
            <div style={{ fontSize: 11, fontWeight: 600, color: '#667085', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 8 }}>
              Schema Fields
            </div>
            <FieldSelector
              schema={schema}
              queryState={queryState}
              onFieldSelected={handleFieldSelected}
            />
          </div>
        )}

        {/* Layout area */}
        <div style={{ flex: 1, padding: 12, overflow: 'auto' }}>
          <AutoLayout schema={schema} data={data} />
        </div>

        {/* Inspector panel */}
        {showInspector && (
          <div style={{
            width: 260,
            borderLeft: '1px solid #e4e7ec',
            padding: '12px 10px',
            overflow: 'auto',
            background: '#fafbfc',
            flexShrink: 0,
          }}>
            <div style={{ fontSize: 11, fontWeight: 600, color: '#667085', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 8 }}>
              Field Properties
            </div>
            <FieldInspector path={selectedPath} queryState={queryState} />
          </div>
        )}
      </div>

      {/* Footer */}
      <footer style={{
        padding: '6px 20px',
        borderTop: '1px solid #e4e7ec',
        background: '#f9fafb',
        fontSize: 11,
        color: '#98a2b3',
        display: 'flex',
        justifyContent: 'space-between',
        flexShrink: 0,
      }}>
        <span>Bakke InfoVis 2013 — Adaptive table/outline hybrid layout</span>
        <span>5 departments · 15 courses · 32 sections</span>
      </footer>
    </div>
  );
}

function ToolbarButton({ active, onClick, label, shortcut }: {
  active: boolean; onClick: () => void; label: string; shortcut: string;
}) {
  return (
    <button
      onClick={onClick}
      style={{
        padding: '5px 14px',
        background: active ? '#3b82f6' : '#fff',
        color: active ? '#fff' : '#344054',
        border: `1px solid ${active ? '#3b82f6' : '#d0d5dd'}`,
        borderRadius: 6,
        cursor: 'pointer',
        fontSize: 12,
        fontWeight: 500,
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        transition: 'all 0.15s ease',
      }}
    >
      {label}
      <kbd style={{
        fontSize: 10,
        padding: '1px 4px',
        borderRadius: 3,
        background: active ? 'rgba(255,255,255,0.2)' : '#f2f4f7',
        border: `1px solid ${active ? 'rgba(255,255,255,0.3)' : '#e4e7ec'}`,
        fontFamily: 'inherit',
      }}>
        {shortcut}
      </kbd>
    </button>
  );
}
