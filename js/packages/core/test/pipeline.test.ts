// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { relation, primitive } from '../src/schema.js';
import { measurePrimitive, measureRelation } from '../src/measure.js';
import {
  decideMode, solveConstraints, buildConstraintTree,
  justifyChildren, dpPartition,
} from '../src/layout.js';

// Fixed measurement: 7px per character
const CHAR_WIDTH = 7;
const strategy = {
  textWidth: (text: string) => Math.ceil(text.length * CHAR_WIDTH),
  lineHeight: (_font: string, fontSize: number) => Math.ceil(fontSize * 1.2),
};

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
    name: 'CS', building: 'Gates',
    courses: [
      { number: 'CS101', title: 'Intro', credits: 3 },
      { number: 'CS201', title: 'Data Structures', credits: 4 },
    ],
  },
  {
    name: 'Math', building: 'Hilbert',
    courses: [
      { number: 'MATH101', title: 'Calculus', credits: 4 },
    ],
  },
];

// --- Measure tests ---

describe('Measure', () => {
  it('measures primitive P90 width', () => {
    const mr = measurePrimitive(primitive('name'), flatData, strategy);
    expect(mr.dataWidth).toBeGreaterThan(0);
    expect(mr.labelWidth).toBeGreaterThan(0);
    expect(mr.averageCardinality).toBe(1);
  });

  it('detects variable-length data', () => {
    const data = [{ x: 'a' }, { x: 'a very long string with many characters' }];
    const mr = measurePrimitive(primitive('x'), data, strategy);
    expect(mr.isVariableLength).toBe(true);
  });

  it('detects fixed-length data', () => {
    const data = [{ x: '2026-01-01' }, { x: '2026-06-15' }];
    const mr = measurePrimitive(primitive('x'), data, strategy);
    expect(mr.isVariableLength).toBe(false);
  });

  it('measures relation with children', () => {
    const ctx = measureRelation(flatSchema, flatData, strategy);
    expect(ctx.labelWidth).toBeGreaterThan(0);
    expect(ctx.readableTableWidth).toBeGreaterThan(0);
    expect(ctx.childResults.size).toBe(2);
  });

  it('measures nested relation', () => {
    const ctx = measureRelation(nestedSchema, nestedData, strategy);
    expect(ctx.childResults.size).toBe(3); // name, building, courses
    expect(ctx.childResults.has('courses')).toBe(true);
  });
});

// --- Layout decision tests ---

describe('Layout decisions', () => {
  it('TABLE when width sufficient', () => {
    expect(decideMode(500, 1000)).toBe('TABLE');
  });

  it('OUTLINE when width insufficient', () => {
    expect(decideMode(500, 300)).toBe('OUTLINE');
  });

  it('mode switches at threshold', () => {
    const ctx = measureRelation(flatSchema, flatData, strategy);
    const threshold = ctx.readableTableWidth;

    expect(decideMode(threshold, threshold + 100)).toBe('TABLE');
    expect(decideMode(threshold, threshold - 10)).toBe('OUTLINE');
  });
});

// --- Constraint solver tests ---

describe('Exhaustive constraint solver', () => {
  it('assigns TABLE when all fit', () => {
    const constraint = {
      path: 'root',
      readableTableWidth: 300,
      availableWidth: 1000,
      children: [],
    };
    const result = solveConstraints(constraint);
    expect(result.get('root')).toBe('TABLE');
  });

  it('assigns OUTLINE when root too wide', () => {
    const constraint = {
      path: 'root',
      readableTableWidth: 1200,
      availableWidth: 1000,
      children: [],
    };
    const result = solveConstraints(constraint);
    expect(result.get('root')).toBe('OUTLINE');
  });

  it('maximizes TABLE assignments', () => {
    const constraint = {
      path: 'root',
      readableTableWidth: 300,
      availableWidth: 500,
      children: [{
        path: 'child',
        readableTableWidth: 200,
        availableWidth: 400,
        children: [],
      }],
    };
    const result = solveConstraints(constraint);
    // Both should be TABLE since they fit
    expect(result.get('root')).toBe('TABLE');
    expect(result.get('child')).toBe('TABLE');
  });

  it('nested: outer OUTLINE, inner TABLE when outer too wide', () => {
    const constraint = {
      path: 'root',
      readableTableWidth: 900,
      availableWidth: 800,
      children: [{
        path: 'child',
        readableTableWidth: 300,
        availableWidth: 700,
        children: [],
      }],
    };
    const result = solveConstraints(constraint);
    expect(result.get('root')).toBe('OUTLINE');
    expect(result.get('child')).toBe('TABLE');
  });
});

// --- Justify tests ---

describe('Justify children', () => {
  it('distributes width among children', () => {
    const ctx = measureRelation(flatSchema, flatData, strategy);
    const justified = justifyChildren(flatSchema, ctx, 500);

    expect(justified).toHaveLength(2);
    const total = justified.reduce((s, c) => s + c.width, 0);
    expect(total).toBeLessThanOrEqual(500);
    expect(total).toBeGreaterThan(400);

    for (const c of justified) {
      expect(c.width).toBeGreaterThan(30);
    }
  });

  it('gives each child at least label width', () => {
    const ctx = measureRelation(flatSchema, flatData, strategy);
    const justified = justifyChildren(flatSchema, ctx, 200);

    for (const c of justified) {
      const mr = ctx.childResults.get(c.field);
      if (mr) {
        expect(c.width).toBeGreaterThanOrEqual(mr.labelWidth * 0.5);
      }
    }
  });
});

// --- DP Partitioner tests ---

describe('DP Column Partitioner', () => {
  it('single column', () => {
    expect(dpPartition([10, 20, 30], 1)).toEqual([3]);
  });

  it('equal heights split evenly', () => {
    expect(dpPartition([20, 20, 20, 20], 2)).toEqual([2, 2]);
  });

  it('optimal split for unequal', () => {
    expect(dpPartition([40, 10, 10, 10], 2)).toEqual([1, 3]);
  });

  it('DP never worse than greedy', () => {
    const heights = [5, 5, 5, 5, 40, 5, 5, 5, 5];
    const sizes = dpPartition(heights, 3);
    let idx = 0, maxH = 0;
    for (const size of sizes) {
      let h = 0;
      for (let j = 0; j < size; j++) h += heights[idx++];
      maxH = Math.max(maxH, h);
    }
    expect(maxH).toBe(40); // optimal: [5,5,5,5] [40] [5,5,5,5]
  });

  it('sizes sum to field count', () => {
    const heights = [15, 25, 35, 10, 20, 30, 5];
    for (let k = 1; k <= 5; k++) {
      const sizes = dpPartition(heights, k);
      expect(sizes.reduce((a, b) => a + b, 0)).toBe(heights.length);
    }
  });
});

// --- Integration: full pipeline ---

describe('Full pipeline integration', () => {
  it('flat schema at wide width → TABLE', () => {
    const ctx = measureRelation(flatSchema, flatData, strategy);
    const mode = decideMode(ctx.readableTableWidth, 1000);
    expect(mode).toBe('TABLE');

    const justified = justifyChildren(flatSchema, ctx, 1000);
    expect(justified).toHaveLength(2);
    expect(justified.every(c => c.width > 50)).toBe(true);
  });

  it('nested schema at narrow width → OUTLINE root', () => {
    const ctx = measureRelation(nestedSchema, nestedData, strategy);
    const constraint = buildConstraintTree(nestedSchema, ctx, 400);
    const assignments = solveConstraints(constraint);

    expect(assignments.get('departments')).toBe('OUTLINE');
  });

  it('nested schema at wide width → TABLE root', () => {
    const ctx = measureRelation(nestedSchema, nestedData, strategy);
    const constraint = buildConstraintTree(nestedSchema, ctx, 2000);
    const assignments = solveConstraints(constraint);

    expect(assignments.get('departments')).toBe('TABLE');
  });
});
