// SPDX-License-Identifier: Apache-2.0

/**
 * GraphQL query → Kramer schema tree.
 * Port of Java GraphQlUtil.buildContext().
 */

import { parse, type DocumentNode, type FieldNode, type SelectionSetNode, Kind } from 'graphql';
import { relation, primitive, type Relation, type SchemaNode } from '@kramer/core';

/**
 * Parse a GraphQL query string into a Kramer Relation schema tree.
 */
export function buildSchema(query: string, source?: string): Relation {
  const doc = parse(query);
  const opDef = doc.definitions.find(d => d.kind === Kind.OPERATION_DEFINITION);
  if (!opDef || opDef.kind !== Kind.OPERATION_DEFINITION) {
    throw new Error('No operation definition found in query');
  }

  const selections = opDef.selectionSet.selections.filter(
    (s): s is FieldNode => s.kind === Kind.FIELD
  );

  if (source) {
    // Find specific source field
    const sourceField = selections.find(f =>
      (f.alias?.value ?? f.name.value) === source
    );
    if (!sourceField) {
      throw new Error(`Source field '${source}' not found in query`);
    }
    return buildRelation(sourceField);
  }

  // Multi-root or single root
  if (selections.length === 1) {
    return buildRelation(selections[0]);
  }

  // QueryRoot equivalent
  const root = relation('query', ...selections.map(buildSchemaNode));
  return root;
}

function buildRelation(field: FieldNode): Relation {
  const name = field.alias?.value ?? field.name.value;
  const children = field.selectionSet
    ? field.selectionSet.selections
        .filter((s): s is FieldNode => s.kind === Kind.FIELD)
        .map(buildSchemaNode)
    : [];
  return relation(name, ...children);
}

function buildSchemaNode(field: FieldNode): SchemaNode {
  const name = field.alias?.value ?? field.name.value;
  if (field.selectionSet && field.selectionSet.selections.length > 0) {
    return buildRelation(field);
  }
  return primitive(name);
}

/**
 * Parse a GraphQL query and return the DocumentNode.
 */
export function parseQuery(query: string): DocumentNode {
  return parse(query);
}
