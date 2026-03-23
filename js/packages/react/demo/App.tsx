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
      height: '100vh', display: 'flex', flexDirection: 'column',
      fontFamily: 'var(--kr-font-body)', color: 'var(--kr-text)', background: '#f8fafc',
    }}>
      <header style={{
        padding: '12px 20px', borderBottom: '1px solid var(--kr-border)',
        background: '#fff', display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        flexShrink: 0,
      }}>
        <div>
          <h1 style={{ fontSize: 18, margin: 0, fontWeight: 700, letterSpacing: '-0.02em' }}>
            Kramer <span style={{ color: 'var(--kr-accent)', fontWeight: 800 }}>JS</span>
          </h1>
          <p style={{ fontSize: 12, margin: '2px 0 0', color: 'var(--kr-text-muted)' }}>
            Course Catalog — resize window to see table ↔ outline switching
          </p>
        </div>
        <div style={{ display: 'flex', gap: 6 }}>
          <ToolbarBtn active={showFields} onClick={() => setShowFields(!showFields)} label="Fields" />
          <ToolbarBtn active={showInspector} onClick={() => setShowInspector(!showInspector)} label="Inspector" />
        </div>
      </header>

      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {showFields && (
          <div style={{
            width: 230, borderRight: '1px solid var(--kr-border)',
            padding: '14px 12px', overflow: 'auto', background: '#fff', flexShrink: 0,
          }}>
            <SectionLabel>Schema Fields</SectionLabel>
            <FieldSelector schema={schema} queryState={queryState} onFieldSelected={handleFieldSelected} />
          </div>
        )}

        <div style={{ flex: 1, padding: 14, overflow: 'auto' }}>
          <AutoLayout schema={schema} data={data} showModeIndicator />
        </div>

        {showInspector && (
          <div style={{
            width: 260, borderLeft: '1px solid var(--kr-border)',
            padding: '14px 12px', overflow: 'auto', background: '#fff', flexShrink: 0,
          }}>
            <SectionLabel>Field Properties</SectionLabel>
            <FieldInspector path={selectedPath} queryState={queryState} />
          </div>
        )}
      </div>

      <footer style={{
        padding: '6px 20px', borderTop: '1px solid var(--kr-border)', background: '#fff',
        fontSize: 11, color: 'var(--kr-text-dim)', display: 'flex', justifyContent: 'space-between', flexShrink: 0,
        fontFamily: 'var(--kr-font-mono)',
      }}>
        <span>Bakke InfoVis 2013 — Adaptive table/outline hybrid layout</span>
        <span>5 departments · 15 courses · 32 sections</span>
      </footer>
    </div>
  );
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <div style={{
      fontSize: 10, fontWeight: 700, color: 'var(--kr-text-muted)',
      textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: 10,
      paddingBottom: 6, borderBottom: '1px solid var(--kr-border)',
    }}>
      {children}
    </div>
  );
}

function ToolbarBtn({ active, onClick, label }: { active: boolean; onClick: () => void; label: string }) {
  return (
    <button onClick={onClick} style={{
      padding: '6px 16px', fontSize: 12, fontWeight: 600, fontFamily: 'inherit',
      background: active ? 'var(--kr-depth-0)' : '#fff',
      color: active ? '#fff' : 'var(--kr-text)',
      border: `1px solid ${active ? 'var(--kr-depth-0)' : 'var(--kr-border)'}`,
      borderRadius: 6, cursor: 'pointer', transition: 'all 0.15s ease',
    }}>
      {label}
    </button>
  );
}
