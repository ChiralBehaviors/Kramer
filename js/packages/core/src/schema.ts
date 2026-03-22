// SPDX-License-Identifier: Apache-2.0

/**
 * Schema types — port of Java SchemaNode hierarchy.
 * Pure data structures, zero platform dependency.
 */

export type SchemaNode = Relation | Primitive;

export interface Relation {
  readonly kind: 'relation';
  readonly field: string;
  readonly label: string;
  readonly children: SchemaNode[];
  readonly autoFold: boolean;
  readonly autoSort: boolean;
  readonly sortFields: readonly string[];
  readonly hideIfEmpty: boolean | null;
  fold: Relation | null;
}

export interface Primitive {
  readonly kind: 'primitive';
  readonly field: string;
  readonly label: string;
}

// --- Constructors ---

export function relation(
  field: string,
  ...children: SchemaNode[]
): Relation {
  return {
    kind: 'relation',
    field,
    label: field,
    children,
    autoFold: true,
    autoSort: false,
    sortFields: [],
    hideIfEmpty: null,
    fold: null,
  };
}

export function primitive(field: string): Primitive {
  return { kind: 'primitive', field, label: field };
}

// --- SchemaPath ---

export interface SchemaPath {
  readonly segments: readonly string[];
}

export function schemaPath(...segments: string[]): SchemaPath {
  return { segments };
}

export function childPath(parent: SchemaPath, field: string): SchemaPath {
  return { segments: [...parent.segments, field] };
}

export function pathLeaf(path: SchemaPath): string {
  return path.segments[path.segments.length - 1];
}

export function pathToString(path: SchemaPath): string {
  return path.segments.join('/');
}

// --- Auto-fold ---

export function getAutoFoldable(rel: Relation): Relation | null {
  if (rel.fold != null) return rel.fold;
  if (rel.autoFold && rel.children.length === 1 && rel.children[0].kind === 'relation') {
    return rel.children[0];
  }
  return null;
}

export function setFold(rel: Relation, fold: boolean): void {
  if (fold && rel.children.length === 1 && rel.children[0].kind === 'relation') {
    rel.fold = rel.children[0];
  } else {
    rel.fold = null;
  }
}

// --- Data extraction ---

export function extractField(node: unknown, field: string): unknown[] {
  if (node == null) return [];
  if (Array.isArray(node)) {
    return node.flatMap(item => extractField(item, field));
  }
  if (typeof node === 'object') {
    const value = (node as Record<string, unknown>)[field];
    if (value == null) return [];
    if (Array.isArray(value)) return value;
    return [value];
  }
  return [];
}

export function asArray(node: unknown): unknown[] {
  if (node == null) return [];
  if (Array.isArray(node)) return node;
  return [node];
}

// --- Child lookup ---

export function getChild(rel: Relation, field: string): SchemaNode | null {
  for (const child of rel.children) {
    if (child.field === field) return child;
  }
  return null;
}

// --- Type guards ---

export function isRelation(node: SchemaNode): node is Relation {
  return node.kind === 'relation';
}

export function isPrimitive(node: SchemaNode): node is Primitive {
  return node.kind === 'primitive';
}
