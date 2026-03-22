// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { relation, primitive, schemaPath, childPath, extractField } from '../../src/core/schema.js';

describe('Schema', () => {
  it('creates a primitive', () => {
    const p = primitive('name');
    expect(p.kind).toBe('primitive');
    expect(p.field).toBe('name');
  });

  it('creates a relation with children', () => {
    const r = relation('departments',
      primitive('name'),
      primitive('building'),
    );
    expect(r.kind).toBe('relation');
    expect(r.children).toHaveLength(2);
    expect(r.children[0].field).toBe('name');
  });

  it('creates nested schema', () => {
    const schema = relation('departments',
      primitive('name'),
      relation('courses',
        primitive('number'),
        primitive('title'),
      ),
    );
    expect(schema.children).toHaveLength(2);
    const courses = schema.children[1];
    expect(courses.kind).toBe('relation');
    if (courses.kind === 'relation') {
      expect(courses.children).toHaveLength(2);
    }
  });

  it('builds schema paths', () => {
    const root = schemaPath('departments');
    expect(root.segments).toEqual(['departments']);

    const child = childPath(root, 'courses');
    expect(child.segments).toEqual(['departments', 'courses']);
  });

  it('extracts field from object', () => {
    const data = { name: 'CS', building: 'Gates' };
    expect(extractField(data, 'name')).toEqual(['CS']);
    expect(extractField(data, 'building')).toEqual(['Gates']);
    expect(extractField(data, 'missing')).toEqual([]);
  });

  it('extracts array field', () => {
    const data = {
      courses: [
        { number: 'CS101' },
        { number: 'CS201' },
      ],
    };
    const courses = extractField(data, 'courses');
    expect(courses).toHaveLength(2);
  });

  it('handles null/undefined data', () => {
    expect(extractField(null, 'name')).toEqual([]);
    expect(extractField(undefined, 'name')).toEqual([]);
  });
});
