// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import React from 'react';
import { relation, primitive } from '../../src/core/schema.js';
import { FixedMeasurement, measureRelation } from '../../src/core/measure.js';
import { justifyChildren } from '../../src/core/layout.js';
import { AutoLayout } from '../../src/react/AutoLayout.js';
import { Table } from '../../src/react/Table.js';
import { Outline } from '../../src/react/Outline.js';

const measurement = new FixedMeasurement(7);

const flatSchema = relation('departments',
  primitive('name'),
  primitive('building'),
);

const flatData = [
  { name: 'Computer Science', building: 'Gates Hall' },
  { name: 'Mathematics', building: 'Hilbert Hall' },
];

const nestedSchema = relation('departments',
  primitive('name'),
  primitive('building'),
  relation('courses',
    primitive('number'),
    primitive('title'),
    primitive('credits'),
  ),
);

const nestedData = [
  {
    name: 'Computer Science', building: 'Gates Hall',
    courses: [
      { number: 'CS101', title: 'Intro to Programming', credits: 3 },
      { number: 'CS201', title: 'Data Structures', credits: 4 },
    ],
  },
  {
    name: 'Mathematics', building: 'Hilbert Hall',
    courses: [
      { number: 'MATH101', title: 'Calculus I', credits: 4 },
    ],
  },
];

describe('Table component', () => {
  it('renders column headers', () => {
    const measure = measureRelation(flatSchema, flatData, measurement);
    const widths = justifyChildren(flatSchema, measure, 800);

    render(<Table schema={flatSchema} data={flatData} columnWidths={widths} />);

    expect(screen.getByText('name')).toBeDefined();
    expect(screen.getByText('building')).toBeDefined();
  });

  it('renders data values', () => {
    const measure = measureRelation(flatSchema, flatData, measurement);
    const widths = justifyChildren(flatSchema, measure, 800);

    render(<Table schema={flatSchema} data={flatData} columnWidths={widths} />);

    expect(screen.getByText('Computer Science')).toBeDefined();
    expect(screen.getByText('Gates Hall')).toBeDefined();
    expect(screen.getByText('Mathematics')).toBeDefined();
    expect(screen.getByText('Hilbert Hall')).toBeDefined();
  });

  it('renders all rows', () => {
    const measure = measureRelation(flatSchema, flatData, measurement);
    const widths = justifyChildren(flatSchema, measure, 800);

    const { container } = render(
      <Table schema={flatSchema} data={flatData} columnWidths={widths} />
    );

    const rows = container.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
  });
});

describe('Table with nested relation', () => {
  it('renders nested table with course data', () => {
    const measure = measureRelation(nestedSchema, nestedData, measurement);
    const widths = justifyChildren(nestedSchema, measure, 1200);

    render(<Table schema={nestedSchema} data={nestedData} columnWidths={widths} />);

    // Course data should be present
    expect(screen.getByText('CS101')).toBeDefined();
    expect(screen.getByText('Data Structures')).toBeDefined();
    expect(screen.getByText('MATH101')).toBeDefined();
  });
});

describe('Outline component', () => {
  it('renders primitive labels and values', () => {
    const measure = measureRelation(flatSchema, flatData, measurement);

    render(
      <Outline
        schema={flatSchema}
        data={flatData}
        width={300}
        measure={measure}
      />
    );

    expect(screen.getByText('Computer Science')).toBeDefined();
    expect(screen.getByText('Gates Hall')).toBeDefined();
  });

  it('renders nested relation as table when it fits', () => {
    const measure = measureRelation(nestedSchema, nestedData, measurement);

    render(
      <Outline
        schema={nestedSchema}
        data={nestedData}
        width={800}
        measure={measure}
      />
    );

    // Should render courses label
    const courseLabels = screen.getAllByText('courses');
    expect(courseLabels.length).toBeGreaterThan(0);

    // Course data should be present
    expect(screen.getByText('CS101')).toBeDefined();
    expect(screen.getByText('MATH101')).toBeDefined();
  });
});

describe('AutoLayout component', () => {
  it('renders with mode indicator', () => {
    render(
      <AutoLayout
        schema={flatSchema}
        data={flatData}
        measurement={measurement}
      />
    );

    // Should show mode indicator
    const modeText = screen.getByText(/mode/);
    expect(modeText).toBeDefined();
  });

  it('renders all data values', () => {
    render(
      <AutoLayout
        schema={flatSchema}
        data={flatData}
        measurement={measurement}
      />
    );

    expect(screen.getByText('Computer Science')).toBeDefined();
    expect(screen.getByText('Mathematics')).toBeDefined();
  });

  it('renders nested schema with course data', () => {
    render(
      <AutoLayout
        schema={nestedSchema}
        data={nestedData}
        measurement={measurement}
      />
    );

    expect(screen.getByText('CS101')).toBeDefined();
    expect(screen.getByText('Calculus I')).toBeDefined();
  });

  it('no data is missing — first item always present', () => {
    render(
      <AutoLayout
        schema={nestedSchema}
        data={nestedData}
        measurement={measurement}
      />
    );

    // First department
    expect(screen.getByText('Computer Science')).toBeDefined();
    // First course of first department
    expect(screen.getByText('CS101')).toBeDefined();
    expect(screen.getByText(/Intro/)).toBeDefined();
  });
});
