// SPDX-License-Identifier: Apache-2.0

/**
 * Expression language — port of Java expression package.
 * Supports field references ($name), arithmetic, comparison,
 * aggregates (sum, count, avg, min, max), and scalar functions.
 */

// --- AST ---

export type Expr =
  | { kind: 'literal'; value: number | string | boolean | null }
  | { kind: 'fieldRef'; path: string[] }
  | { kind: 'binaryOp'; op: BinaryOp; left: Expr; right: Expr }
  | { kind: 'unaryOp'; op: UnaryOp; operand: Expr }
  | { kind: 'scalarCall'; name: string; args: Expr[] }
  | { kind: 'aggregateCall'; fn: string; arg: Expr | null };

export type BinaryOp =
  | 'ADD' | 'SUB' | 'MUL' | 'DIV'
  | 'EQ' | 'NEQ' | 'LT' | 'GT' | 'LTE' | 'GTE'
  | 'AND' | 'OR';

export type UnaryOp = 'NEG' | 'NOT';

// --- Constructors ---

export const literal = (value: number | string | boolean | null): Expr =>
  ({ kind: 'literal', value });

export const fieldRef = (...path: string[]): Expr =>
  ({ kind: 'fieldRef', path });

export const binaryOp = (op: BinaryOp, left: Expr, right: Expr): Expr =>
  ({ kind: 'binaryOp', op, left, right });

export const unaryOp = (op: UnaryOp, operand: Expr): Expr =>
  ({ kind: 'unaryOp', op, operand });

export const scalarCall = (name: string, ...args: Expr[]): Expr =>
  ({ kind: 'scalarCall', name, args });

export const aggregateCall = (fn: string, arg: Expr | null): Expr =>
  ({ kind: 'aggregateCall', fn, arg });

// --- Tokenizer ---

type TokenKind =
  | 'NUMBER' | 'STRING' | 'IDENT' | 'FIELD_REF'
  | 'PLUS' | 'MINUS' | 'STAR' | 'SLASH'
  | 'EQ' | 'NEQ' | 'LT' | 'GT' | 'LTE' | 'GTE'
  | 'AND' | 'OR' | 'NOT'
  | 'LPAREN' | 'RPAREN' | 'COMMA' | 'DOT'
  | 'TRUE' | 'FALSE' | 'NULL'
  | 'EOF';

interface Token {
  kind: TokenKind;
  value: string;
  pos: number;
}

function tokenize(input: string): Token[] {
  const tokens: Token[] = [];
  let i = 0;

  while (i < input.length) {
    // Skip whitespace
    if (/\s/.test(input[i])) { i++; continue; }

    const pos = i;

    // Field reference: $name or ${path.to.field}
    if (input[i] === '$') {
      i++;
      if (i < input.length && input[i] === '{') {
        i++;
        const start = i;
        while (i < input.length && input[i] !== '}') i++;
        tokens.push({ kind: 'FIELD_REF', value: input.slice(start, i), pos });
        i++; // skip }
      } else {
        const start = i;
        while (i < input.length && /[a-zA-Z0-9_]/.test(input[i])) i++;
        tokens.push({ kind: 'FIELD_REF', value: input.slice(start, i), pos });
      }
      continue;
    }

    // Number
    if (/[0-9]/.test(input[i]) || (input[i] === '.' && i + 1 < input.length && /[0-9]/.test(input[i + 1]))) {
      const start = i;
      while (i < input.length && /[0-9.]/.test(input[i])) i++;
      tokens.push({ kind: 'NUMBER', value: input.slice(start, i), pos });
      continue;
    }

    // String literal
    if (input[i] === '"' || input[i] === "'") {
      const quote = input[i]; i++;
      const start = i;
      while (i < input.length && input[i] !== quote) i++;
      tokens.push({ kind: 'STRING', value: input.slice(start, i), pos });
      i++; // skip closing quote
      continue;
    }

    // Identifiers and keywords
    if (/[a-zA-Z_]/.test(input[i])) {
      const start = i;
      while (i < input.length && /[a-zA-Z0-9_]/.test(input[i])) i++;
      const word = input.slice(start, i);
      const kw: Record<string, TokenKind> = {
        'true': 'TRUE', 'false': 'FALSE', 'null': 'NULL',
        'and': 'AND', 'or': 'OR', 'not': 'NOT',
        'AND': 'AND', 'OR': 'OR', 'NOT': 'NOT',
      };
      tokens.push({ kind: kw[word] ?? 'IDENT', value: word, pos });
      continue;
    }

    // Two-character operators
    if (i + 1 < input.length) {
      const two = input.slice(i, i + 2);
      const twoMap: Record<string, TokenKind> = {
        '!=': 'NEQ', '<>': 'NEQ', '<=': 'LTE', '>=': 'GTE',
        '==': 'EQ', '&&': 'AND', '||': 'OR',
      };
      if (twoMap[two]) {
        tokens.push({ kind: twoMap[two], value: two, pos });
        i += 2;
        continue;
      }
    }

    // Single-character operators
    const oneMap: Record<string, TokenKind> = {
      '+': 'PLUS', '-': 'MINUS', '*': 'STAR', '/': 'SLASH',
      '=': 'EQ', '<': 'LT', '>': 'GT', '!': 'NOT',
      '(': 'LPAREN', ')': 'RPAREN', ',': 'COMMA', '.': 'DOT',
    };
    if (oneMap[input[i]]) {
      tokens.push({ kind: oneMap[input[i]], value: input[i], pos });
      i++;
      continue;
    }

    throw new Error(`Unexpected character '${input[i]}' at position ${i}`);
  }

  tokens.push({ kind: 'EOF', value: '', pos: i });
  return tokens;
}

// --- Parser (recursive descent) ---

class ExprParser {
  private tokens: Token[];
  private pos = 0;

  constructor(tokens: Token[]) {
    this.tokens = tokens;
  }

  parse(): Expr {
    const expr = this.parseOr();
    if (this.peek().kind !== 'EOF') {
      throw new Error(`Unexpected token '${this.peek().value}' at position ${this.peek().pos}`);
    }
    return expr;
  }

  private peek(): Token { return this.tokens[this.pos]; }
  private advance(): Token { return this.tokens[this.pos++]; }

  private expect(kind: TokenKind): Token {
    const t = this.advance();
    if (t.kind !== kind) {
      throw new Error(`Expected ${kind} but got ${t.kind} at position ${t.pos}`);
    }
    return t;
  }

  private parseOr(): Expr {
    let left = this.parseAnd();
    while (this.peek().kind === 'OR') {
      this.advance();
      left = binaryOp('OR', left, this.parseAnd());
    }
    return left;
  }

  private parseAnd(): Expr {
    let left = this.parseComparison();
    while (this.peek().kind === 'AND') {
      this.advance();
      left = binaryOp('AND', left, this.parseComparison());
    }
    return left;
  }

  private parseComparison(): Expr {
    let left = this.parseAddSub();
    const cmpOps: Partial<Record<TokenKind, BinaryOp>> = {
      'EQ': 'EQ', 'NEQ': 'NEQ', 'LT': 'LT', 'GT': 'GT', 'LTE': 'LTE', 'GTE': 'GTE',
    };
    while (cmpOps[this.peek().kind]) {
      const op = cmpOps[this.advance().kind]!;
      left = binaryOp(op, left, this.parseAddSub());
    }
    return left;
  }

  private parseAddSub(): Expr {
    let left = this.parseMulDiv();
    while (this.peek().kind === 'PLUS' || this.peek().kind === 'MINUS') {
      const op: BinaryOp = this.advance().kind === 'PLUS' ? 'ADD' : 'SUB';
      left = binaryOp(op, left, this.parseMulDiv());
    }
    return left;
  }

  private parseMulDiv(): Expr {
    let left = this.parseUnary();
    while (this.peek().kind === 'STAR' || this.peek().kind === 'SLASH') {
      const op: BinaryOp = this.advance().kind === 'STAR' ? 'MUL' : 'DIV';
      left = binaryOp(op, left, this.parseUnary());
    }
    return left;
  }

  private parseUnary(): Expr {
    if (this.peek().kind === 'MINUS') {
      this.advance();
      return unaryOp('NEG', this.parseUnary());
    }
    if (this.peek().kind === 'NOT') {
      this.advance();
      return unaryOp('NOT', this.parseUnary());
    }
    return this.parsePrimary();
  }

  private parsePrimary(): Expr {
    const t = this.peek();

    if (t.kind === 'NUMBER') {
      this.advance();
      return literal(parseFloat(t.value));
    }
    if (t.kind === 'STRING') {
      this.advance();
      return literal(t.value);
    }
    if (t.kind === 'TRUE') { this.advance(); return literal(true); }
    if (t.kind === 'FALSE') { this.advance(); return literal(false); }
    if (t.kind === 'NULL') { this.advance(); return literal(null); }

    if (t.kind === 'FIELD_REF') {
      this.advance();
      return fieldRef(...t.value.split('.'));
    }

    if (t.kind === 'IDENT') {
      this.advance();
      const name = t.value.toLowerCase();

      // Function call
      if (this.peek().kind === 'LPAREN') {
        this.advance(); // (

        // Aggregate functions
        const aggregates = ['sum', 'count', 'avg', 'min', 'max'];
        if (aggregates.includes(name)) {
          let arg: Expr | null = null;
          if (this.peek().kind !== 'RPAREN') {
            arg = this.parseOr();
          }
          this.expect('RPAREN');
          return aggregateCall(name, arg);
        }

        // Scalar functions
        const args: Expr[] = [];
        if (this.peek().kind !== 'RPAREN') {
          args.push(this.parseOr());
          while (this.peek().kind === 'COMMA') {
            this.advance();
            args.push(this.parseOr());
          }
        }
        this.expect('RPAREN');
        return scalarCall(name, ...args);
      }

      // Bare identifier — treat as field ref
      return fieldRef(t.value);
    }

    if (t.kind === 'LPAREN') {
      this.advance();
      const expr = this.parseOr();
      this.expect('RPAREN');
      return expr;
    }

    throw new Error(`Unexpected token '${t.value}' at position ${t.pos}`);
  }
}

export function parseExpression(input: string): Expr {
  return new ExprParser(tokenize(input)).parse();
}

// --- Evaluator ---

export type ExprContext = Record<string, unknown>;

export function evaluateExpr(expr: Expr, row: ExprContext): unknown {
  switch (expr.kind) {
    case 'literal':
      return expr.value;

    case 'fieldRef': {
      let val: unknown = row;
      for (const seg of expr.path) {
        if (val == null || typeof val !== 'object') return null;
        val = (val as Record<string, unknown>)[seg];
      }
      return val;
    }

    case 'unaryOp':
      if (expr.op === 'NEG') return -(toNumber(evaluateExpr(expr.operand, row)));
      if (expr.op === 'NOT') return !toBool(evaluateExpr(expr.operand, row));
      return null;

    case 'binaryOp': {
      const l = evaluateExpr(expr.left, row);
      const r = evaluateExpr(expr.right, row);
      switch (expr.op) {
        case 'ADD': return toNumber(l) + toNumber(r);
        case 'SUB': return toNumber(l) - toNumber(r);
        case 'MUL': return toNumber(l) * toNumber(r);
        case 'DIV': { const d = toNumber(r); return d === 0 ? null : toNumber(l) / d; }
        case 'EQ':  return l === r;
        case 'NEQ': return l !== r;
        case 'LT':  return toNumber(l) < toNumber(r);
        case 'GT':  return toNumber(l) > toNumber(r);
        case 'LTE': return toNumber(l) <= toNumber(r);
        case 'GTE': return toNumber(l) >= toNumber(r);
        case 'AND': return toBool(l) && toBool(r);
        case 'OR':  return toBool(l) || toBool(r);
      }
      return null;
    }

    case 'scalarCall': {
      const args = expr.args.map(a => evaluateExpr(a, row));
      switch (expr.name) {
        case 'abs': return Math.abs(toNumber(args[0]));
        case 'round': return Math.round(toNumber(args[0]));
        case 'len': return String(args[0] ?? '').length;
        case 'upper': return String(args[0] ?? '').toUpperCase();
        case 'lower': return String(args[0] ?? '').toLowerCase();
        case 'if': return toBool(args[0]) ? args[1] : args[2];
        default: return null;
      }
    }

    case 'aggregateCall':
      // Aggregates are evaluated over arrays, not single rows
      // Caller must handle aggregate context
      return null;
  }
}

/**
 * Evaluate an aggregate expression over an array of rows.
 */
export function evaluateAggregate(
  expr: Expr,
  rows: ExprContext[],
): unknown {
  if (expr.kind !== 'aggregateCall') {
    return evaluateExpr(expr, rows[0] ?? {});
  }

  if (expr.fn === 'count') {
    if (expr.arg == null) return rows.length;
    return rows.filter(r => evaluateExpr(expr.arg!, r) != null).length;
  }

  const values = rows
    .map(r => expr.arg ? evaluateExpr(expr.arg, r) : null)
    .filter(v => v != null)
    .map(toNumber);

  if (values.length === 0) return null;

  switch (expr.fn) {
    case 'sum': return values.reduce((a, b) => a + b, 0);
    case 'avg': return values.reduce((a, b) => a + b, 0) / values.length;
    case 'min': return Math.min(...values);
    case 'max': return Math.max(...values);
    default: return null;
  }
}

// --- Helpers ---

function toNumber(v: unknown): number {
  if (typeof v === 'number') return v;
  if (typeof v === 'string') { const n = parseFloat(v); return isNaN(n) ? 0 : n; }
  if (typeof v === 'boolean') return v ? 1 : 0;
  return 0;
}

function toBool(v: unknown): boolean {
  if (typeof v === 'boolean') return v;
  if (typeof v === 'number') return v !== 0;
  if (typeof v === 'string') return v.length > 0;
  return v != null;
}
