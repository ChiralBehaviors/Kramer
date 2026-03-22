// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import React from 'react';
import { relation, primitive, schemaPath, LayoutQueryState } from '@kramer/core';
import { FieldSelector } from '../src/FieldSelector.js';
import { FieldInspector } from '../src/FieldInspector.js';

describe('FieldSelector', () => {
  const schema = relation('departments',
    primitive('name'),
    primitive('building'),
    relation('courses', primitive('number'), primitive('title')),
  );

  it('renders all fields as checkboxes', () => {
    const qs = new LayoutQueryState();
    const { container } = render(<FieldSelector schema={schema} queryState={qs} />);
    const checkboxes = container.querySelectorAll('input[type="checkbox"]');
    expect(checkboxes.length).toBe(6); // depts + name + building + courses + number + title
  });

  it('shows field labels', () => {
    const qs = new LayoutQueryState();
    render(<FieldSelector schema={schema} queryState={qs} />);
    expect(screen.getByText('departments')).toBeDefined();
    expect(screen.getByText('name')).toBeDefined();
    expect(screen.getByText('courses')).toBeDefined();
  });

  it('toggles visibility', () => {
    const qs = new LayoutQueryState();
    const { container } = render(<FieldSelector schema={schema} queryState={qs} />);
    const checkboxes = container.querySelectorAll('input[type="checkbox"]') as NodeListOf<HTMLInputElement>;
    fireEvent.click(checkboxes[1]); // toggle 'name'
    expect(qs.isVisible(schemaPath('departments', 'name'))).toBe(false);
  });

  it('calls onFieldSelected', () => {
    const qs = new LayoutQueryState();
    let selected = '';
    render(
      <FieldSelector schema={schema} queryState={qs}
        onFieldSelected={(p) => { selected = p.segments.join('/'); }} />
    );
    fireEvent.click(screen.getByText('name'));
    expect(selected).toBe('departments/name');
  });
});

describe('FieldInspector', () => {
  it('shows placeholder when no path', () => {
    render(<FieldInspector path={null} queryState={new LayoutQueryState()} />);
    expect(screen.getByText(/Select a field/)).toBeDefined();
  });

  it('shows field properties', () => {
    const qs = new LayoutQueryState();
    const path = schemaPath('departments', 'name');
    render(<FieldInspector path={path} queryState={qs} />);
    expect(screen.getByText('departments/name')).toBeDefined();
    expect(screen.getByText('Visible')).toBeDefined();
  });

  it('reflects sort state', () => {
    const qs = new LayoutQueryState();
    const path = schemaPath('name');
    qs.apply({ kind: 'sortBy', path, descending: true });
    render(<FieldInspector path={path} queryState={qs} />);
    expect(screen.getByText('desc')).toBeDefined();
  });
});
