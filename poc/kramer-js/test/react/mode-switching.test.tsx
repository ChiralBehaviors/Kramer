// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import React from 'react';
import { relation, primitive } from '../../src/core/schema.js';
import { FixedMeasurement, measureRelation } from '../../src/core/measure.js';
import { decideMode, layoutRelation, justifyChildren } from '../../src/core/layout.js';
import { Table } from '../../src/react/Table.js';
import { Outline } from '../../src/react/Outline.js';

const measurement = new FixedMeasurement(7);

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

describe('Mode switching at threshold', () => {
  const measure = measureRelation(nestedSchema, nestedData, measurement);
  const threshold = measure.readableTableWidth;

  it('readableTableWidth is a positive number', () => {
    expect(threshold).toBeGreaterThan(0);
    console.log(`[MODE-SWITCH] readableTableWidth = ${threshold}`);
  });

  it('TABLE mode at wide width', () => {
    const mode = decideMode(nestedSchema, measure, threshold + 200);
    expect(mode).toBe('table');
  });

  it('OUTLINE mode at narrow width', () => {
    const mode = decideMode(nestedSchema, measure, threshold - 10);
    expect(mode).toBe('outline');
  });

  it('wide rendering produces <table> DOM', () => {
    const widths = justifyChildren(nestedSchema, measure, threshold + 200);
    const { container } = render(
      <Table schema={nestedSchema} data={nestedData} columnWidths={widths} />
    );

    // Table mode: should have <table> with <th> headers
    const tables = container.querySelectorAll('table');
    expect(tables.length).toBeGreaterThan(0);

    const headers = container.querySelectorAll('th');
    expect(headers.length).toBeGreaterThanOrEqual(3); // name, building, courses (or sub-headers)

    // Data present
    expect(container.textContent).toContain('Computer Science');
    expect(container.textContent).toContain('CS101');
  });

  it('narrow rendering produces outline DOM (no root table)', () => {
    const { container } = render(
      <Outline
        schema={nestedSchema}
        data={nestedData}
        width={threshold - 10}
        measure={measure}
      />
    );

    // Outline mode: should have .kramer-outline class, not a root <table>
    const outline = container.querySelector('.kramer-outline');
    expect(outline).not.toBeNull();

    // Outline elements with label:value pairs
    const elements = container.querySelectorAll('.kramer-outline-element');
    expect(elements.length).toBe(2); // 2 departments

    // Data still present — not lost
    expect(container.textContent).toContain('Computer Science');
    expect(container.textContent).toContain('Gates Hall');
    expect(container.textContent).toContain('CS101');
    expect(container.textContent).toContain('MATH101');
  });

  it('different widths produce different DOM structures', () => {
    // Wide: table
    const wideWidths = justifyChildren(nestedSchema, measure, threshold + 200);
    const wide = render(
      <Table schema={nestedSchema} data={nestedData} columnWidths={wideWidths} />
    );
    const wideTables = wide.container.querySelectorAll('table.kramer-table');

    // Narrow: outline
    const narrow = render(
      <Outline
        schema={nestedSchema}
        data={nestedData}
        width={threshold - 10}
        measure={measure}
      />
    );
    const narrowOutlines = narrow.container.querySelectorAll('.kramer-outline');

    // Wide has root table, narrow has outline — different structures
    expect(wideTables.length).toBeGreaterThan(0);
    expect(narrowOutlines.length).toBeGreaterThan(0);
  });

  it('nested courses render as table inside outline', () => {
    const { container } = render(
      <Outline
        schema={nestedSchema}
        data={nestedData}
        width={threshold - 10}
        measure={measure}
      />
    );

    // The courses relation should render as a table inside the outline
    // (because courses readableTableWidth fits within the outline column width)
    const nestedTables = container.querySelectorAll('table.kramer-table');
    expect(nestedTables.length).toBeGreaterThan(0);

    // Nested table should have course headers
    const courseHeaders = Array.from(container.querySelectorAll('th'))
      .map(th => th.textContent);
    expect(courseHeaders).toContain('number');
    expect(courseHeaders).toContain('title');
    expect(courseHeaders).toContain('credits');
  });
});
