// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { relation, primitive } from '../../src/core/schema.js';
import { FixedMeasurement, measureRelation } from '../../src/core/measure.js';
import { decideMode, layoutRelation, justifyChildren, dpPartition } from '../../src/core/layout.js';

const measurement = new FixedMeasurement(7);

describe('Layout decisions', () => {
  const flatSchema = relation('departments',
    primitive('name'),
    primitive('building'),
  );

  const flatData = [
    { name: 'Computer Science', building: 'Gates Hall' },
    { name: 'Mathematics', building: 'Hilbert Hall' },
  ];

  it('chooses TABLE when width is sufficient', () => {
    const measure = measureRelation(flatSchema, flatData, measurement);
    const mode = decideMode(flatSchema, measure, 1000);
    expect(mode).toBe('table');
  });

  it('chooses OUTLINE when width is insufficient', () => {
    const measure = measureRelation(flatSchema, flatData, measurement);
    const mode = decideMode(flatSchema, measure, 50);
    expect(mode).toBe('outline');
  });

  it('mode switches at threshold', () => {
    const measure = measureRelation(flatSchema, flatData, measurement);
    const threshold = measure.readableTableWidth;

    const wide = decideMode(flatSchema, measure, threshold + 100);
    const narrow = decideMode(flatSchema, measure, threshold - 10);

    expect(wide).toBe('table');
    expect(narrow).toBe('outline');
  });
});

describe('Nested layout decisions', () => {
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
  ];

  it('nested layout produces child results', () => {
    const measure = measureRelation(nestedSchema, nestedData, measurement);
    const result = layoutRelation(nestedSchema, measure, 800);

    expect(result.mode).toBeDefined();
    expect(result.children).toHaveLength(1); // courses relation
  });

  it('at narrow width, outer is OUTLINE', () => {
    const measure = measureRelation(nestedSchema, nestedData, measurement);
    const result = layoutRelation(nestedSchema, measure, 200);

    expect(result.mode).toBe('outline');
  });
});

describe('Justify children', () => {
  const schema = relation('departments',
    primitive('name'),
    primitive('building'),
  );

  const data = [
    { name: 'CS', building: 'Gates Hall' },
  ];

  it('distributes width among children', () => {
    const measure = measureRelation(schema, data, measurement);
    const justified = justifyChildren(schema, measure, 500);

    expect(justified).toHaveLength(2);
    const totalWidth = justified.reduce((sum, c) => sum + c.width, 0);
    expect(totalWidth).toBeLessThanOrEqual(500);
    expect(totalWidth).toBeGreaterThan(400); // should use most of the space

    // Each child should get non-trivial width
    for (const child of justified) {
      expect(child.width).toBeGreaterThan(30);
    }
  });
});

describe('DP Column Partitioner', () => {
  it('single column returns all fields', () => {
    expect(dpPartition([10, 20, 30], 1)).toEqual([3]);
  });

  it('equal heights split evenly', () => {
    expect(dpPartition([20, 20, 20, 20], 2)).toEqual([2, 2]);
  });

  it('optimal split for unequal heights', () => {
    const sizes = dpPartition([40, 10, 10, 10], 2);
    expect(sizes).toEqual([1, 3]); // [40] vs [10,10,10]=30
  });

  it('DP never worse than total / numColumns', () => {
    const heights = [30, 10, 10, 20, 10, 20];
    const sizes = dpPartition(heights, 3);
    const total = heights.reduce((a, b) => a + b, 0);
    const ideal = total / 3;

    // Max column height should be close to ideal
    let idx = 0;
    let maxH = 0;
    for (const size of sizes) {
      let colH = 0;
      for (let j = 0; j < size; j++) colH += heights[idx++];
      maxH = Math.max(maxH, colH);
    }
    expect(maxH).toBeLessThanOrEqual(ideal * 1.5);
  });
});
