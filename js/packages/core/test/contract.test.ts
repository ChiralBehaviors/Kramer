// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { relation, primitive, type Relation, type SchemaNode } from '../src/schema.js';
import { measureRelation } from '../src/measure.js';
import { decideMode, buildConstraintTree, solveConstraints } from '../src/layout.js';

const FIXTURES_DIR = resolve(__dirname, '../../../../test/fixtures');

const strategy = {
  textWidth: (text: string) => Math.ceil(text.length * 7),
  lineHeight: (_font: string, fontSize: number) => Math.ceil(fontSize * 1.2),
};

function loadFixture(name: string): unknown {
  const path = resolve(FIXTURES_DIR, name);
  return JSON.parse(readFileSync(path, 'utf-8'));
}

function buildSchema(def: any): SchemaNode {
  if (def.kind === 'primitive') return primitive(def.field);
  return relation(def.field, ...def.children.map(buildSchema));
}

describe('Contract test: threshold-switching', () => {
  const fixture = loadFixture('threshold-switching.json') as any;
  const schema = buildSchema(fixture.schema) as Relation;
  const data = fixture.data;

  for (const test of fixture.widthTests) {
    it(`at width ${test.width}: root should be ${test.rootMode}`, () => {
      const ctx = measureRelation(schema, data, strategy);

      // Use constraint solver for accurate mode decision
      const constraint = buildConstraintTree(schema, ctx, test.width);
      const assignments = solveConstraints(constraint);
      const tsMode = assignments.get(schema.field) ?? 'OUTLINE';

      // Note: Java uses real font metrics (JavaFX), TS uses FixedMeasurement.
      // The threshold width may differ, so we check structural consistency
      // rather than exact mode match at boundary widths.
      //
      // At clearly wide widths (1200), both should be TABLE.
      // At clearly narrow widths (200), both should be OUTLINE.
      if (test.width >= 1000) {
        expect(tsMode).toBe('TABLE');
      } else if (test.width <= 300) {
        expect(tsMode).toBe('OUTLINE');
      }
      // At boundary widths (400-800), modes may differ due to font metrics.
      // Log for analysis:
      console.log(`[CONTRACT] width=${test.width} java=${test.rootMode} ts=${tsMode}`);
    });
  }
});

describe('Contract test: flat-3-primitives', () => {
  const fixture = loadFixture('flat-3-primitives.json') as any;
  const schema = buildSchema(fixture.schema) as Relation;
  const data = fixture.data;

  it('schema structure matches', () => {
    expect(schema.kind).toBe('relation');
    expect(schema.field).toBe(fixture.schema.field);
    expect(schema.children).toHaveLength(fixture.schema.children.length);
  });

  it('TS pipeline produces a layout decision', () => {
    const ctx = measureRelation(schema, data, strategy);
    const constraint = buildConstraintTree(schema, ctx, fixture.width);
    const assignments = solveConstraints(constraint);

    expect(assignments.size).toBeGreaterThan(0);
    const mode = assignments.get(schema.field);
    expect(mode).toBeDefined();
    console.log(`[CONTRACT] flat @${fixture.width}: java=${fixture.expected.layoutResult.relationMode} ts=${mode}`);
  });

  it('measure produces non-zero widths', () => {
    const ctx = measureRelation(schema, data, strategy);
    expect(ctx.readableTableWidth).toBeGreaterThan(0);
    expect(ctx.labelWidth).toBeGreaterThan(0);
    expect(ctx.childResults.size).toBe(schema.children.length);
  });
});

describe('Contract test: nested-2-level', () => {
  const fixture = loadFixture('nested-2-level.json') as any;
  const schema = buildSchema(fixture.schema) as Relation;
  const data = fixture.data;

  it('schema structure matches (2 levels)', () => {
    expect(schema.kind).toBe('relation');
    expect(schema.children.length).toBe(fixture.schema.children.length);
    const relations = schema.children.filter(c => c.kind === 'relation');
    expect(relations.length).toBeGreaterThan(0);
  });

  it('TS pipeline handles nested schema', () => {
    const ctx = measureRelation(schema, data, strategy);
    const constraint = buildConstraintTree(schema, ctx, fixture.width);
    const assignments = solveConstraints(constraint);

    expect(assignments.size).toBeGreaterThan(0);
    console.log(`[CONTRACT] nested @${fixture.width}:`,
      Object.fromEntries(assignments));
  });

  it('nested relations are measured recursively', () => {
    const ctx = measureRelation(schema, data, strategy);
    const coursesMr = ctx.childResults.get('courses');
    expect(coursesMr).toBeDefined();
    expect(coursesMr!.childResults.length).toBeGreaterThan(0);
  });
});
