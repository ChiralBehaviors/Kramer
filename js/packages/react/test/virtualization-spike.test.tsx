// SPDX-License-Identifier: Apache-2.0
/**
 * Virtualization spike tests.
 *
 * VirtualOutline uses @tanstack/react-virtual which requires a scroll
 * container with real dimensions (clientHeight > 0). JSDOM provides 0
 * for all layout properties, so the virtualizer renders no items.
 *
 * Tests validate:
 * 1. The component renders without errors (API compatibility)
 * 2. The Table component renders data correctly (non-virtualized)
 * 3. AutoLayout mode switching works
 *
 * Full VirtualOutline rendering is validated in browser (npm run dev).
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import React from 'react';
import { relation, primitive } from '@kramer/core';
import { AutoLayout } from '../src/AutoLayout.js';
import { VirtualOutline } from '../src/VirtualOutline.js';
import { Table } from '../src/Table.js';
import { FixedMeasurement } from '@kramer/measurement';

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
    courses: [{ number: 'MATH101', title: 'Calculus I', credits: 4 }],
  },
];

describe('Spike: VirtualOutline renders without error', () => {
  it('mounts without throwing', () => {
    // @tanstack/virtual won't show items in JSDOM (0 clientHeight),
    // but the component should mount without errors.
    expect(() => {
      render(
        <VirtualOutline schema={nestedSchema} data={nestedData} width={800} />
      );
    }).not.toThrow();
  });

  it('renders the scroll container', () => {
    const { container } = render(
      <VirtualOutline schema={nestedSchema} data={nestedData} width={800} />
    );
    const scrollEl = container.querySelector('.kramer-virtual-outline');
    expect(scrollEl).not.toBeNull();
  });
});

describe('Spike: Table renders data', () => {
  it('renders flat data as table with headers and values', () => {
    const flatSchema = relation('items', primitive('name'), primitive('price'));
    const flatData = [{ name: 'Widget', price: 9.99 }, { name: 'Gadget', price: 24.50 }];
    const widths = [
      { field: 'name', kind: 'primitive' as const, width: 200 },
      { field: 'price', kind: 'primitive' as const, width: 100 },
    ];

    const { container } = render(
      <Table schema={flatSchema} data={flatData} columnWidths={widths} />
    );

    expect(screen.getByText('Widget')).toBeDefined();
    expect(screen.getByText('Gadget')).toBeDefined();
    expect(container.querySelectorAll('th').length).toBe(2);
    expect(container.querySelectorAll('tbody tr').length).toBe(2);
  });

  it('renders nested relation as inner table', () => {
    const widths = nestedSchema.children.map(c =>
      ({ field: c.field, kind: c.kind, width: 250 }));

    render(<Table schema={nestedSchema} data={nestedData} columnWidths={widths} />);

    expect(screen.getByText('CS101')).toBeDefined();
    expect(screen.getByText('MATH101')).toBeDefined();
    expect(screen.getByText('Data Structures')).toBeDefined();
  });
});

describe('Spike: AutoLayout mode switching', () => {
  it('renders with mode indicator', () => {
    render(
      <AutoLayout schema={nestedSchema} data={nestedData} measurement={measurement} showModeIndicator />
    );
    expect(screen.getByText(/TABLE|OUTLINE/)).toBeDefined();
  });

  it('renders all data values', () => {
    render(
      <AutoLayout schema={nestedSchema} data={nestedData} measurement={measurement} showModeIndicator />
    );
    expect(screen.getByText('Computer Science')).toBeDefined();
    expect(screen.getByText('CS101')).toBeDefined();
    expect(screen.getByText('MATH101')).toBeDefined();
  });
});
