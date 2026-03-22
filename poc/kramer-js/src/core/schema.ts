// SPDX-License-Identifier: Apache-2.0

/**
 * Schema types — direct port from Java SchemaNode hierarchy.
 * Pure data structures, zero platform dependency.
 */

export type SchemaNode = Relation | Primitive;

export interface Relation {
  readonly kind: 'relation';
  readonly field: string;
  readonly label: string;
  readonly children: SchemaNode[];
}

export interface Primitive {
  readonly kind: 'primitive';
  readonly field: string;
  readonly label: string;
}

export interface SchemaPath {
  readonly segments: readonly string[];
}

export function relation(field: string, ...children: SchemaNode[]): Relation {
  return { kind: 'relation', field, label: field, children };
}

export function primitive(field: string): Primitive {
  return { kind: 'primitive', field, label: field };
}

export function schemaPath(...segments: string[]): SchemaPath {
  return { segments };
}

export function childPath(parent: SchemaPath, field: string): SchemaPath {
  return { segments: [...parent.segments, field] };
}

/**
 * Extract a field value from a JSON data node.
 * Returns an array (wraps scalar values).
 */
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
