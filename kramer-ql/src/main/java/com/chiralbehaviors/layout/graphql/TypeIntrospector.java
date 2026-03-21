// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Parses a GraphQL {@code __schema} introspection response into a
 * browseable type tree. Filters out system types ({@code __*}) and
 * scalar types, keeping only user-defined OBJECT types.
 * <p>
 * Distinct from {@link SchemaIntrospector}, which inspects field arguments
 * for server capability detection.
 *
 * @author hhildebrand
 */
public final class TypeIntrospector {

    /** A GraphQL type from introspection. */
    public record IntrospectedType(String name, String kind,
                                    List<IntrospectedField> fields) {}

    /** A field within an introspected type. */
    public record IntrospectedField(String name, TypeRef type,
                                     String description) {}

    /** Type reference — handles scalar, LIST, NON_NULL wrapping. */
    public record TypeRef(String name, String kind, TypeRef ofType) {

        /** Unwrap LIST/NON_NULL to get the base type name. */
        public String baseName() {
            if (name != null) return name;
            return ofType != null ? ofType.baseName() : null;
        }
    }

    private final Map<String, IntrospectedType> index;

    private TypeIntrospector(Map<String, IntrospectedType> index) {
        this.index = index;
    }

    /** Parse a full introspection response (with data.__schema wrapper). */
    public static TypeIntrospector parse(JsonNode response) {
        JsonNode schema = response.path("data").path("__schema");
        JsonNode types = schema.path("types");

        Map<String, IntrospectedType> index = new LinkedHashMap<>();
        if (types.isArray()) {
            for (JsonNode typeNode : types) {
                String name = typeNode.path("name").asText(null);
                String kind = typeNode.path("kind").asText(null);
                if (name == null || isSystemType(name) || "SCALAR".equals(kind)) {
                    continue;
                }
                List<IntrospectedField> fields = parseFields(typeNode.path("fields"));
                index.put(name, new IntrospectedType(name, kind, fields));
            }
        }
        return new TypeIntrospector(index);
    }

    /** Name → type lookup. */
    public Map<String, IntrospectedType> typeIndex() {
        return Map.copyOf(index);
    }

    /** All user-defined types (excludes system and scalar). */
    public List<IntrospectedType> userTypes() {
        return List.copyOf(index.values());
    }

    /** Returns the standard introspection query string. */
    public static String introspectionQuery() {
        return """
            {
              __schema {
                types {
                  name
                  kind
                  fields {
                    name
                    description
                    type {
                      name
                      kind
                      ofType {
                        name
                        kind
                        ofType {
                          name
                          kind
                        }
                      }
                    }
                  }
                }
              }
            }
            """;
    }

    private static List<IntrospectedField> parseFields(JsonNode fieldsNode) {
        List<IntrospectedField> fields = new ArrayList<>();
        if (fieldsNode == null || !fieldsNode.isArray()) return fields;

        for (JsonNode fieldNode : fieldsNode) {
            String name = fieldNode.path("name").asText(null);
            String description = fieldNode.path("description").asText(null);
            TypeRef type = parseTypeRef(fieldNode.path("type"));
            if (name != null) {
                fields.add(new IntrospectedField(name, type, description));
            }
        }
        return List.copyOf(fields);
    }

    private static TypeRef parseTypeRef(JsonNode typeNode) {
        if (typeNode == null || typeNode.isMissingNode() || typeNode.isNull()) {
            return null;
        }
        String name = typeNode.path("name").isNull() ? null
            : typeNode.path("name").asText(null);
        String kind = typeNode.path("kind").asText(null);
        TypeRef ofType = parseTypeRef(typeNode.path("ofType"));
        return new TypeRef(name, kind, ofType);
    }

    private static boolean isSystemType(String name) {
        return name.startsWith("__");
    }
}
