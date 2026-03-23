// SPDX-License-Identifier: Apache-2.0

/**
 * QueryExpander — add/remove fields from a GraphQL AST.
 * Port of Java QueryExpander using graphql-js visit() + print().
 */

import {
  parse, print, visit, Kind,
  type DocumentNode, type FieldNode, type SelectionSetNode,
} from 'graphql';

/**
 * Add a field to the selection set at the given path.
 * Returns a new Document (immutable).
 */
export function addField(
  doc: DocumentNode,
  parentPath: string[],
  fieldName: string,
): DocumentNode {
  return visit(doc, {
    SelectionSet: {
      enter(node, _key, parent) {
        // Check if this selection set is at the target path
        if (!isAtPath(node, parent, parentPath, doc)) return undefined;

        // Check if field already exists (idempotent)
        const exists = node.selections.some(
          s => s.kind === Kind.FIELD && s.name.value === fieldName
        );
        if (exists) return undefined;

        // Add new field
        const newField: FieldNode = {
          kind: Kind.FIELD,
          name: { kind: Kind.NAME, value: fieldName },
        };

        return {
          ...node,
          selections: [...node.selections, newField],
        };
      },
    },
  });
}

/**
 * Remove a field from its parent's selection set.
 * The last segment of fieldPath is the field to remove.
 */
export function removeField(
  doc: DocumentNode,
  fieldPath: string[],
): DocumentNode {
  if (fieldPath.length === 0) return doc;
  const fieldName = fieldPath[fieldPath.length - 1];
  const parentPath = fieldPath.slice(0, -1);

  return visit(doc, {
    SelectionSet: {
      enter(node, _key, parent) {
        if (!isAtPath(node, parent, parentPath, doc)) return undefined;

        const filtered = node.selections.filter(
          s => !(s.kind === Kind.FIELD && s.name.value === fieldName)
        );
        if (filtered.length === node.selections.length) return undefined;

        return { ...node, selections: filtered };
      },
    },
  });
}

/**
 * Serialize a Document back to a query string.
 */
export function serialize(doc: DocumentNode): string {
  return print(doc);
}

// --- Path matching ---

function isAtPath(
  _node: SelectionSetNode,
  parent: any,
  path: string[],
  _doc: DocumentNode,
): boolean {
  // Simple heuristic: match by walking up the parent chain
  // For the top-level operation, path is empty
  if (path.length === 0) {
    return parent?.kind === Kind.OPERATION_DEFINITION;
  }

  // For nested paths, check if the parent field name matches the last segment
  if (parent?.kind === Kind.FIELD) {
    const fieldName = (parent as FieldNode).name.value;
    return fieldName === path[path.length - 1];
  }

  return false;
}
