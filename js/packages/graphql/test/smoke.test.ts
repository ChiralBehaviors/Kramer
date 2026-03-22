// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parse } from 'graphql';
import {
  buildSchema, parseQuery, addField, removeField, serialize,
  parseIntrospection, baseName, INTROSPECTION_QUERY,
} from '../src/index.js';

describe('GraphQL Util: buildSchema', () => {
  it('parses flat query into Relation', () => {
    const schema = buildSchema('{ users { name email } }');
    expect(schema.kind).toBe('relation');
    expect(schema.field).toBe('users');
    expect(schema.children).toHaveLength(2);
    expect(schema.children[0].field).toBe('name');
    expect(schema.children[1].field).toBe('email');
  });

  it('parses nested query', () => {
    const schema = buildSchema('{ departments { name courses { number title } } }');
    expect(schema.field).toBe('departments');
    expect(schema.children).toHaveLength(2);
    const courses = schema.children[1];
    expect(courses.kind).toBe('relation');
    if (courses.kind === 'relation') {
      expect(courses.children).toHaveLength(2);
    }
  });

  it('parses with source selection', () => {
    const schema = buildSchema('{ users { name } posts { title } }', 'posts');
    expect(schema.field).toBe('posts');
    expect(schema.children[0].field).toBe('title');
  });

  it('handles multi-root without source', () => {
    const schema = buildSchema('{ users { name } posts { title } }');
    expect(schema.field).toBe('query');
    expect(schema.children).toHaveLength(2);
  });
});

describe('QueryExpander', () => {
  it('adds a field to a selection set', () => {
    const doc = parse('{ users { name } }');
    const modified = addField(doc, ['users'], 'email');
    const result = serialize(modified);
    expect(result).toContain('email');
    expect(result).toContain('name');
  });

  it('is idempotent — adding existing field is no-op', () => {
    const doc = parse('{ users { name email } }');
    const modified = addField(doc, ['users'], 'name');
    const result = serialize(modified);
    // Should still have exactly one 'name'
    const nameCount = (result.match(/name/g) ?? []).length;
    expect(nameCount).toBe(1);
  });

  it('removes a field', () => {
    const doc = parse('{ users { name email age } }');
    const modified = removeField(doc, ['users', 'email']);
    const result = serialize(modified);
    expect(result).not.toContain('email');
    expect(result).toContain('name');
    expect(result).toContain('age');
  });

  it('round-trip: add → serialize → parse preserves structure', () => {
    const doc = parse('{ users { name } }');
    const modified = addField(doc, ['users'], 'age');
    const serialized = serialize(modified);
    const reparsed = parse(serialized);

    const opDef = reparsed.definitions[0];
    expect(opDef.kind).toBe('OperationDefinition');
    if (opDef.kind === 'OperationDefinition') {
      const users = opDef.selectionSet.selections[0];
      if (users.kind === 'Field' && users.selectionSet) {
        const fields = users.selectionSet.selections
          .filter((s): s is import('graphql').FieldNode => s.kind === 'Field')
          .map(f => f.name.value);
        expect(fields).toContain('name');
        expect(fields).toContain('age');
      }
    }
  });
});

describe('TypeIntrospector', () => {
  const sampleIntrospection = {
    data: {
      __schema: {
        types: [
          {
            name: 'Employee', kind: 'OBJECT',
            fields: [
              { name: 'id', description: 'Primary key', type: { name: 'ID', kind: 'SCALAR', ofType: null } },
              { name: 'name', description: null, type: { name: 'String', kind: 'SCALAR', ofType: null } },
              { name: 'projects', description: null, type: { name: null, kind: 'LIST', ofType: { name: 'Project', kind: 'OBJECT', ofType: null } } },
            ],
          },
          {
            name: 'Project', kind: 'OBJECT',
            fields: [
              { name: 'title', description: null, type: { name: 'String', kind: 'SCALAR', ofType: null } },
            ],
          },
          { name: '__Schema', kind: 'OBJECT', fields: [] },
          { name: 'String', kind: 'SCALAR', fields: null },
        ],
      },
    },
  };

  it('parses user types, filters system types', () => {
    const result = parseIntrospection(sampleIntrospection);
    expect(result.typeIndex.has('Employee')).toBe(true);
    expect(result.typeIndex.has('Project')).toBe(true);
    expect(result.typeIndex.has('__Schema')).toBe(false);
    expect(result.typeIndex.has('String')).toBe(false);
  });

  it('parses fields with type refs', () => {
    const result = parseIntrospection(sampleIntrospection);
    const employee = result.typeIndex.get('Employee')!;
    expect(employee.fields).toHaveLength(3);
    expect(employee.fields[0].name).toBe('id');
    expect(employee.fields[0].description).toBe('Primary key');
  });

  it('unwraps LIST type ref', () => {
    const result = parseIntrospection(sampleIntrospection);
    const employee = result.typeIndex.get('Employee')!;
    const projects = employee.fields[2];
    expect(projects.type.kind).toBe('LIST');
    expect(baseName(projects.type)).toBe('Project');
  });

  it('lists user types', () => {
    const result = parseIntrospection(sampleIntrospection);
    expect(result.userTypes).toHaveLength(2);
    expect(result.userTypes.map(t => t.name)).toContain('Employee');
    expect(result.userTypes.map(t => t.name)).toContain('Project');
  });

  it('introspection query is valid GraphQL', () => {
    expect(() => parse(INTROSPECTION_QUERY)).not.toThrow();
  });
});
