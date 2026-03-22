// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import type {
  LayoutDecisionNode, MeasureResult, LayoutResult, CompressResult,
  HeightResult, ColumnSetSnapshot, ColumnSnapshot,
} from '../src/types.js';
import { schemaPath } from '../src/schema.js';

describe('LayoutDecisionNode types', () => {
  it('constructs a minimal decision node', () => {
    const node: LayoutDecisionNode = {
      path: schemaPath('departments'),
      fieldName: 'departments',
      measureResult: null,
      layoutResult: null,
      compressResult: null,
      heightResult: null,
      columnSetSnapshots: [],
      childNodes: [],
    };
    expect(node.fieldName).toBe('departments');
    expect(node.path.segments).toEqual(['departments']);
  });

  it('constructs with full results', () => {
    const mr: MeasureResult = {
      labelWidth: 50, columnWidth: 100, dataWidth: 80, maxWidth: 120,
      averageCardinality: 3, isVariableLength: true,
      averageChildCardinality: 2, maxCardinality: 5,
      childResults: [],
      contentStats: { p90: 80, min: 10, max: 120, mean: 60, sampleCount: 15 },
      numericStats: null,
      pivotStats: null,
      sparklineStats: null,
    };

    const lr: LayoutResult = {
      relationMode: 'TABLE',
      primitiveMode: 'TEXT',
      useVerticalHeader: false,
      tableColumnWidth: 200,
      columnHeaderIndentation: 0,
      constrainedColumnWidth: 180,
      childResults: [],
    };

    const col: ColumnSnapshot = { width: 300, fieldNames: ['name', 'building'] };
    const cs: ColumnSetSnapshot = { columns: [col], height: 42 };

    const cr: CompressResult = {
      justifiedWidth: 800,
      columnSetSnapshots: [cs],
      cellHeight: 21,
      childResults: [],
    };

    const hr: HeightResult = {
      height: 200,
      cellHeight: 21,
      resolvedCardinality: 5,
      columnHeaderHeight: 20,
      childResults: [],
    };

    const node: LayoutDecisionNode = {
      path: schemaPath('departments'),
      fieldName: 'departments',
      measureResult: mr,
      layoutResult: lr,
      compressResult: cr,
      heightResult: hr,
      columnSetSnapshots: [cs],
      childNodes: [],
    };

    expect(node.measureResult?.averageCardinality).toBe(3);
    expect(node.layoutResult?.relationMode).toBe('TABLE');
    expect(node.compressResult?.justifiedWidth).toBe(800);
    expect(node.heightResult?.resolvedCardinality).toBe(5);
    expect(node.columnSetSnapshots[0].columns[0].fieldNames).toEqual(['name', 'building']);
  });

  it('constructs nested decision tree', () => {
    const child: LayoutDecisionNode = {
      path: schemaPath('departments', 'courses'),
      fieldName: 'courses',
      measureResult: null,
      layoutResult: { relationMode: 'TABLE', primitiveMode: 'TEXT',
        useVerticalHeader: false, tableColumnWidth: 100,
        columnHeaderIndentation: 0, constrainedColumnWidth: 90,
        childResults: [] },
      compressResult: null,
      heightResult: null,
      columnSetSnapshots: [],
      childNodes: [],
    };

    const root: LayoutDecisionNode = {
      path: schemaPath('departments'),
      fieldName: 'departments',
      measureResult: null,
      layoutResult: { relationMode: 'OUTLINE', primitiveMode: 'TEXT',
        useVerticalHeader: false, tableColumnWidth: 0,
        columnHeaderIndentation: 0, constrainedColumnWidth: 800,
        childResults: [] },
      compressResult: null,
      heightResult: null,
      columnSetSnapshots: [],
      childNodes: [child],
    };

    expect(root.childNodes).toHaveLength(1);
    expect(root.childNodes[0].layoutResult?.relationMode).toBe('TABLE');
    expect(root.layoutResult?.relationMode).toBe('OUTLINE');
  });

  it('matches Java record structure for contract test compatibility', () => {
    // Verify the type shape matches what Jackson would serialize
    // from the Java LayoutDecisionNode record
    const jsonFixture = {
      path: { segments: ['departments'] },
      fieldName: 'departments',
      measureResult: null,
      layoutResult: {
        relationMode: 'TABLE',
        primitiveMode: 'TEXT',
        useVerticalHeader: false,
        tableColumnWidth: 200,
        columnHeaderIndentation: 0,
        constrainedColumnWidth: 180,
        childResults: [],
      },
      compressResult: null,
      heightResult: null,
      columnSetSnapshots: [],
      childNodes: [],
    };

    // This should type-check as LayoutDecisionNode
    const node: LayoutDecisionNode = jsonFixture;
    expect(node.path.segments[0]).toBe('departments');
    expect(node.layoutResult?.relationMode).toBe('TABLE');
  });
});
