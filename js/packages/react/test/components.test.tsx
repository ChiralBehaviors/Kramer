// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import React from 'react';
import { relation, primitive } from '@kramer/core';
import { ColumnHeader } from '../src/ColumnHeader.js';
import { NestedRow } from '../src/NestedRow.js';

describe('ColumnHeader', () => {
  it('renders label text', () => {
    render(<table><thead><tr><ColumnHeader label="name" width={100} /></tr></thead></table>);
    expect(screen.getByText('name')).toBeDefined();
  });

  it('shows ascending sort arrow', () => {
    render(<table><thead><tr><ColumnHeader label="price" width={100} sortDirection="asc" /></tr></thead></table>);
    expect(screen.getByText(/▲/)).toBeDefined();
  });

  it('shows descending sort arrow', () => {
    render(<table><thead><tr><ColumnHeader label="price" width={100} sortDirection="desc" /></tr></thead></table>);
    expect(screen.getByText(/▼/)).toBeDefined();
  });

  it('calls onSort when clicked', () => {
    let clicked = false;
    render(
      <table><thead><tr>
        <ColumnHeader label="name" width={100} onSort={() => { clicked = true; }} />
      </tr></thead></table>
    );
    fireEvent.click(screen.getByText('name'));
    expect(clicked).toBe(true);
  });

  it('renders vertical when rotateVertical', () => {
    const { container } = render(
      <ColumnHeader label="sections" width={40} rotateVertical />
    );
    const vertical = container.querySelector('.kramer-column-header--vertical');
    expect(vertical).not.toBeNull();
  });
});

describe('NestedRow', () => {
  const schema = relation('items',
    primitive('name'),
    primitive('price'),
  );

  it('renders primitive values', () => {
    const widths = [
      { field: 'name', kind: 'primitive' as const, width: 200 },
      { field: 'price', kind: 'primitive' as const, width: 100 },
    ];
    render(
      <table><tbody>
        <NestedRow
          row={{ name: 'Widget', price: 9.99 }}
          schema={schema}
          columnWidths={widths}
          rowIndex={0}
        />
      </tbody></table>
    );
    expect(screen.getByText('Widget')).toBeDefined();
    expect(screen.getByText('9.99')).toBeDefined();
  });

  it('applies even/odd CSS classes', () => {
    const widths = [
      { field: 'name', kind: 'primitive' as const, width: 200 },
      { field: 'price', kind: 'primitive' as const, width: 100 },
    ];
    const { container } = render(
      <table><tbody>
        <NestedRow row={{ name: 'A', price: 1 }} schema={schema} columnWidths={widths} rowIndex={0} />
        <NestedRow row={{ name: 'B', price: 2 }} schema={schema} columnWidths={widths} rowIndex={1} />
      </tbody></table>
    );
    expect(container.querySelector('.kramer-row-even')).not.toBeNull();
    expect(container.querySelector('.kramer-row-odd')).not.toBeNull();
  });

  it('renders nested relation as sub-table', () => {
    const nestedSchema = relation('depts',
      primitive('name'),
      relation('courses', primitive('number'), primitive('title')),
    );
    const widths = [
      { field: 'name', kind: 'primitive' as const, width: 200 },
      { field: 'courses', kind: 'relation' as const, width: 400 },
    ];
    render(
      <table><tbody>
        <NestedRow
          row={{ name: 'CS', courses: [{ number: 'CS101', title: 'Intro' }] }}
          schema={nestedSchema}
          columnWidths={widths}
          rowIndex={0}
        />
      </tbody></table>
    );
    expect(screen.getByText('CS101')).toBeDefined();
    expect(screen.getByText('Intro')).toBeDefined();
  });
});
