// SPDX-License-Identifier: Apache-2.0
import React from 'react';
import { relation, primitive } from './core/schema.js';
import { AutoLayout } from './react/AutoLayout.js';

const schema = relation('departments',
  primitive('name'),
  primitive('building'),
  relation('courses',
    primitive('number'),
    primitive('title'),
    primitive('credits'),
  ),
);

const data = [
  {
    name: 'Computer Science', building: 'Gates Hall',
    courses: [
      { number: 'CS101', title: 'Intro to Programming', credits: 3 },
      { number: 'CS201', title: 'Data Structures', credits: 4 },
      { number: 'CS301', title: 'Algorithms', credits: 4 },
    ],
  },
  {
    name: 'Mathematics', building: 'Hilbert Hall',
    courses: [
      { number: 'MATH101', title: 'Calculus I', credits: 4 },
      { number: 'MATH201', title: 'Linear Algebra', credits: 3 },
    ],
  },
  {
    name: 'Physics', building: 'Newton Building',
    courses: [
      { number: 'PHYS101', title: 'Mechanics', credits: 4 },
      { number: 'PHYS201', title: 'E&M', credits: 4 },
    ],
  },
  {
    name: 'English', building: 'Austen Hall',
    courses: [
      { number: 'ENG101', title: 'Composition', credits: 3 },
      { number: 'ENG201', title: 'American Lit', credits: 3 },
    ],
  },
];

export default function App() {
  return (
    <div style={{ fontFamily: 'sans-serif', padding: 16 }}>
      <h1 style={{ fontSize: 20, marginBottom: 12 }}>
        Kramer JS — Course Catalog Demo
      </h1>
      <p style={{ color: '#666', fontSize: 13, marginBottom: 16 }}>
        Resize the window to see table ↔ outline mode switching.
      </p>
      <AutoLayout schema={schema} data={data} />
    </div>
  );
}
