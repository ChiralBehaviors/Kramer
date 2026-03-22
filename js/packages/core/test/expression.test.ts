// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import {
  parseExpression, evaluateExpr, evaluateAggregate,
  literal, fieldRef, binaryOp, unaryOp, aggregateCall,
  type ExprContext,
} from '../src/expression.js';

describe('Expression parser', () => {
  it('parses number literal', () => {
    const expr = parseExpression('42');
    expect(expr).toEqual(literal(42));
  });

  it('parses string literal', () => {
    expect(parseExpression('"hello"')).toEqual(literal('hello'));
    expect(parseExpression("'world'")).toEqual(literal('world'));
  });

  it('parses boolean literals', () => {
    expect(parseExpression('true')).toEqual(literal(true));
    expect(parseExpression('false')).toEqual(literal(false));
  });

  it('parses null', () => {
    expect(parseExpression('null')).toEqual(literal(null));
  });

  it('parses field reference $name', () => {
    const expr = parseExpression('$price');
    expect(expr).toEqual(fieldRef('price'));
  });

  it('parses nested field reference ${a.b.c}', () => {
    const expr = parseExpression('${a.b.c}');
    expect(expr).toEqual(fieldRef('a', 'b', 'c'));
  });

  it('parses arithmetic', () => {
    const expr = parseExpression('$price * 1.1');
    expect(expr.kind).toBe('binaryOp');
  });

  it('parses comparison', () => {
    const expr = parseExpression('$age > 18');
    expect(expr).toEqual(binaryOp('GT', fieldRef('age'), literal(18)));
  });

  it('parses logical operators', () => {
    const expr = parseExpression('$a > 1 and $b < 10');
    expect(expr.kind).toBe('binaryOp');
    if (expr.kind === 'binaryOp') expect(expr.op).toBe('AND');
  });

  it('parses negation', () => {
    const expr = parseExpression('-$price');
    expect(expr).toEqual(unaryOp('NEG', fieldRef('price')));
  });

  it('parses NOT', () => {
    const expr = parseExpression('not $active');
    expect(expr).toEqual(unaryOp('NOT', fieldRef('active')));
  });

  it('parses aggregate count()', () => {
    const expr = parseExpression('count()');
    expect(expr).toEqual(aggregateCall('count', null));
  });

  it('parses aggregate sum($price)', () => {
    const expr = parseExpression('sum($price)');
    expect(expr).toEqual(aggregateCall('sum', fieldRef('price')));
  });

  it('parses aggregate avg($score)', () => {
    const expr = parseExpression('avg($score)');
    expect(expr).toEqual(aggregateCall('avg', fieldRef('score')));
  });

  it('parses scalar function abs(-5)', () => {
    const expr = parseExpression('abs(-5)');
    expect(expr.kind).toBe('scalarCall');
  });

  it('parses if(condition, then, else)', () => {
    const expr = parseExpression('if($x > 0, $x, 0)');
    expect(expr.kind).toBe('scalarCall');
    if (expr.kind === 'scalarCall') {
      expect(expr.name).toBe('if');
      expect(expr.args).toHaveLength(3);
    }
  });

  it('parses parenthesized expression', () => {
    const expr = parseExpression('($a + $b) * $c');
    expect(expr.kind).toBe('binaryOp');
  });

  it('respects operator precedence', () => {
    const expr = parseExpression('$a + $b * $c');
    // Should be a + (b * c), not (a + b) * c
    expect(expr.kind).toBe('binaryOp');
    if (expr.kind === 'binaryOp') {
      expect(expr.op).toBe('ADD');
      expect(expr.right.kind).toBe('binaryOp');
    }
  });
});

describe('Expression evaluator', () => {
  const row: ExprContext = {
    name: 'Alice',
    age: 30,
    price: 19.99,
    active: true,
    nested: { deep: { value: 42 } },
  };

  it('evaluates literal', () => {
    expect(evaluateExpr(literal(42), row)).toBe(42);
  });

  it('evaluates field reference', () => {
    expect(evaluateExpr(fieldRef('name'), row)).toBe('Alice');
    expect(evaluateExpr(fieldRef('age'), row)).toBe(30);
  });

  it('evaluates nested field reference', () => {
    expect(evaluateExpr(fieldRef('nested', 'deep', 'value'), row)).toBe(42);
  });

  it('evaluates arithmetic', () => {
    expect(evaluateExpr(parseExpression('$price * 2'), row)).toBeCloseTo(39.98);
    expect(evaluateExpr(parseExpression('$age + 5'), row)).toBe(35);
    expect(evaluateExpr(parseExpression('$age - 10'), row)).toBe(20);
    expect(evaluateExpr(parseExpression('$age / 3'), row)).toBeCloseTo(10);
  });

  it('evaluates comparison', () => {
    expect(evaluateExpr(parseExpression('$age > 18'), row)).toBe(true);
    expect(evaluateExpr(parseExpression('$age < 18'), row)).toBe(false);
    expect(evaluateExpr(parseExpression('$age = 30'), row)).toBe(true);
    expect(evaluateExpr(parseExpression('$age != 30'), row)).toBe(false);
  });

  it('evaluates logical operators', () => {
    expect(evaluateExpr(parseExpression('$age > 18 and $active'), row)).toBe(true);
    expect(evaluateExpr(parseExpression('$age < 18 or $active'), row)).toBe(true);
    expect(evaluateExpr(parseExpression('not $active'), row)).toBe(false);
  });

  it('evaluates negation', () => {
    expect(evaluateExpr(parseExpression('-$price'), row)).toBeCloseTo(-19.99);
  });

  it('evaluates scalar functions', () => {
    expect(evaluateExpr(parseExpression('abs(-5)'), row)).toBe(5);
    expect(evaluateExpr(parseExpression('round(3.7)'), row)).toBe(4);
    expect(evaluateExpr(parseExpression('len("hello")'), row)).toBe(5);
    expect(evaluateExpr(parseExpression('upper("hello")'), row)).toBe('HELLO');
    expect(evaluateExpr(parseExpression('lower("HELLO")'), row)).toBe('hello');
  });

  it('evaluates if()', () => {
    expect(evaluateExpr(parseExpression('if($age > 18, "adult", "minor")'), row)).toBe('adult');
  });

  it('handles division by zero', () => {
    expect(evaluateExpr(parseExpression('$age / 0'), row)).toBeNull();
  });

  it('handles missing field', () => {
    expect(evaluateExpr(fieldRef('nonexistent'), row)).toBeUndefined();
  });
});

describe('Aggregate evaluator', () => {
  const rows: ExprContext[] = [
    { name: 'A', score: 10 },
    { name: 'B', score: 20 },
    { name: 'C', score: 30 },
    { name: 'D', score: 40 },
  ];

  it('count()', () => {
    expect(evaluateAggregate(parseExpression('count()'), rows)).toBe(4);
  });

  it('count($field)', () => {
    const rowsWithNull = [...rows, { name: 'E' }]; // no score
    expect(evaluateAggregate(parseExpression('count($score)'), rowsWithNull)).toBe(4);
  });

  it('sum($score)', () => {
    expect(evaluateAggregate(parseExpression('sum($score)'), rows)).toBe(100);
  });

  it('avg($score)', () => {
    expect(evaluateAggregate(parseExpression('avg($score)'), rows)).toBe(25);
  });

  it('min($score)', () => {
    expect(evaluateAggregate(parseExpression('min($score)'), rows)).toBe(10);
  });

  it('max($score)', () => {
    expect(evaluateAggregate(parseExpression('max($score)'), rows)).toBe(40);
  });

  it('empty rows return null', () => {
    expect(evaluateAggregate(parseExpression('sum($score)'), [])).toBeNull();
  });
});
