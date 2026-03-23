// SPDX-License-Identifier: Apache-2.0

import React, { useState } from 'react';
import { relation, primitive, schemaPath, LayoutQueryState } from '@kramer/core';
import { FixedMeasurement } from '@kramer/measurement';
import { AutoLayout } from '../src/AutoLayout.js';
import { FieldSelector } from '@kramer/react-ui';
import { FieldInspector } from '@kramer/react-ui';

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
    ],
  },
  {
    name: 'Mathematics', building: 'Hilbert Hall',
    courses: [
      { number: 'MATH101', title: 'Calculus I', credits: 4,
        sections: [{ id: 'A', enrolled: 50 }, { id: 'B', enrolled: 48 }] },
      { number: 'MATH201', title: 'Linear Algebra', credits: 3,
        sections: [{ id: 'A', enrolled: 35 }] },
    ],
  },
  {
    name: 'Physics', building: 'Newton Building',
    courses: [
      { number: 'PHYS101', title: 'Mechanics', credits: 4,
        sections: [{ id: 'A', enrolled: 55 }, { id: 'B', enrolled: 50 }] },
      { number: 'PHYS201', title: 'E&M', credits: 4,
        sections: [{ id: 'A', enrolled: 40 }] },
    ],
  },
  {
    name: 'English', building: 'Austen Hall',
    courses: [
      { number: 'ENG101', title: 'Composition', credits: 3,
        sections: [{ id: 'A', enrolled: 25 }, { id: 'B', enrolled: 25 }] },
      { number: 'ENG201', title: 'American Lit', credits: 3,
        sections: [{ id: 'A', enrolled: 30 }] },
    ],
  },
];

export default function App() {
  const [queryState] = useState(() => new LayoutQueryState());
  const [showFields, setShowFields] = useState(false);
  const [showInspector, setShowInspector] = useState(false);
  const [selectedPath, setSelectedPath] = useState(schemaPath('departments'));

  return (
    <div style={{ fontFamily: '-apple-system, BlinkMacSystemFont, sans-serif', height: '100vh', display: 'flex', flexDirection: 'column' }}>
      <header style={{ padding: '8px 16px', borderBottom: '1px solid #ddd', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h1 style={{ fontSize: 18, margin: 0 }}>Kramer JS — Course Catalog Demo</h1>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => setShowFields(!showFields)}
            style={{ padding: '4px 12px', background: showFields ? '#e0e0e0' : '#f5f5f5', border: '1px solid #ccc', borderRadius: 4, cursor: 'pointer' }}>
            Fields
          </button>
          <button onClick={() => setShowInspector(!showInspector)}
            style={{ padding: '4px 12px', background: showInspector ? '#e0e0e0' : '#f5f5f5', border: '1px solid #ccc', borderRadius: 4, cursor: 'pointer' }}>
            Inspector
          </button>
        </div>
      </header>

      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {showFields && (
          <div style={{ width: 220, borderRight: '1px solid #ddd', padding: 8, overflow: 'auto' }}>
            <FieldSelector
              schema={schema}
              queryState={queryState}
              onFieldSelected={setSelectedPath}
            />
          </div>
        )}

        <div style={{ flex: 1, padding: 8 }}>
          <AutoLayout schema={schema} data={data} />
        </div>

        {showInspector && (
          <div style={{ width: 250, borderLeft: '1px solid #ddd', padding: 8, overflow: 'auto' }}>
            <FieldInspector path={selectedPath} queryState={queryState} />
          </div>
        )}
      </div>
    </div>
  );
}
