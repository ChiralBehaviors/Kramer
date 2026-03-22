// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import {
  relation, primitive, schemaPath, childPath, pathLeaf, pathToString,
  extractField, asArray, getChild, getAutoFoldable, setFold,
  isRelation, isPrimitive,
} from '../src/schema.js';

describe('Schema types', () => {
  it('creates a primitive', () => {
    const p = primitive('name');
    expect(p.kind).toBe('primitive');
    expect(p.field).toBe('name');
    expect(p.label).toBe('name');
  });

  it('creates a relation with children', () => {
    const r = relation('departments', primitive('name'), primitive('building'));
    expect(r.kind).toBe('relation');
    expect(r.children).toHaveLength(2);
    expect(r.autoFold).toBe(true);
    expect(r.sortFields).toEqual([]);
    expect(r.hideIfEmpty).toBeNull();
    expect(r.fold).toBeNull();
  });

  it('creates nested schema', () => {
    const schema = relation('depts',
      primitive('name'),
      relation('courses', primitive('number'), primitive('title')),
    );
    expect(schema.children).toHaveLength(2);
    expect(schema.children[1].kind).toBe('relation');
    if (schema.children[1].kind === 'relation') {
      expect(schema.children[1].children).toHaveLength(2);
    }
  });
});

describe('SchemaPath', () => {
  it('builds paths', () => {
    const root = schemaPath('departments');
    expect(root.segments).toEqual(['departments']);
  });

  it('builds child paths', () => {
    const child = childPath(schemaPath('depts'), 'courses');
    expect(child.segments).toEqual(['depts', 'courses']);
  });

  it('gets leaf', () => {
    expect(pathLeaf(schemaPath('a', 'b', 'c'))).toBe('c');
  });

  it('converts to string', () => {
    expect(pathToString(schemaPath('a', 'b'))).toBe('a/b');
  });
});

describe('Auto-fold', () => {
  it('returns single relation child when autoFold', () => {
    const inner = relation('courses', primitive('number'));
    const outer = relation('wrapper', inner);
    expect(getAutoFoldable(outer)).toBe(inner);
  });

  it('returns null when multiple children', () => {
    const outer = relation('depts', primitive('name'), primitive('building'));
    expect(getAutoFoldable(outer)).toBeNull();
  });

  it('returns null when child is primitive', () => {
    const outer = relation('wrapper', primitive('name'));
    expect(getAutoFoldable(outer)).toBeNull();
  });

  it('setFold enables fold for single relation child', () => {
    const inner = relation('courses', primitive('number'));
    const outer = relation('wrapper', inner);
    setFold(outer, true);
    expect(outer.fold).toBe(inner);
  });

  it('setFold disables fold', () => {
    const inner = relation('courses', primitive('number'));
    const outer = relation('wrapper', inner);
    setFold(outer, true);
    setFold(outer, false);
    expect(outer.fold).toBeNull();
  });
});

describe('extractField', () => {
  it('extracts from object', () => {
    expect(extractField({ name: 'CS' }, 'name')).toEqual(['CS']);
  });

  it('extracts array field', () => {
    const data = { courses: [{ n: 1 }, { n: 2 }] };
    expect(extractField(data, 'courses')).toHaveLength(2);
  });

  it('handles missing field', () => {
    expect(extractField({ a: 1 }, 'b')).toEqual([]);
  });

  it('handles null/undefined', () => {
    expect(extractField(null, 'x')).toEqual([]);
    expect(extractField(undefined, 'x')).toEqual([]);
  });
});

describe('asArray', () => {
  it('wraps scalar', () => {
    expect(asArray('x')).toEqual(['x']);
  });

  it('passes array through', () => {
    expect(asArray([1, 2])).toEqual([1, 2]);
  });

  it('handles null', () => {
    expect(asArray(null)).toEqual([]);
  });
});

describe('getChild', () => {
  it('finds child by field name', () => {
    const r = relation('depts', primitive('name'), primitive('building'));
    expect(getChild(r, 'name')).toBe(r.children[0]);
    expect(getChild(r, 'building')).toBe(r.children[1]);
  });

  it('returns null for missing child', () => {
    const r = relation('depts', primitive('name'));
    expect(getChild(r, 'missing')).toBeNull();
  });
});

describe('Type guards', () => {
  it('isRelation', () => {
    expect(isRelation(relation('x'))).toBe(true);
    expect(isRelation(primitive('x'))).toBe(false);
  });

  it('isPrimitive', () => {
    expect(isPrimitive(primitive('x'))).toBe(true);
    expect(isPrimitive(relation('x'))).toBe(false);
  });
});
