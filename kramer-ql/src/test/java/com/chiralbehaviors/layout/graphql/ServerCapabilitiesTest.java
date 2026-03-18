// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.SchemaPath;

/**
 * TDD tests for ServerCapabilities and SchemaIntrospector (RDR-028 Phase 1).
 */
class ServerCapabilitiesTest {

    // -------------------------------------------------------------------
    // ServerCapabilities.NONE
    // -------------------------------------------------------------------

    @Test
    void noneHasNoCapabilities() {
        SchemaPath path = new SchemaPath("users");
        assertFalse(ServerCapabilities.NONE.canPushFilter(path));
        assertFalse(ServerCapabilities.NONE.canPushSort(path));
        assertFalse(ServerCapabilities.NONE.canPushPagination(path));
    }

    // -------------------------------------------------------------------
    // SchemaIntrospector.discover — filter detection
    // -------------------------------------------------------------------

    @Test
    void discoverDetectsFilterArgument() {
        String query = "{ users(filter: $f) { name } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        ServerCapabilities caps = SchemaIntrospector.discover(ctx);

        SchemaPath usersPath = new SchemaPath("users");
        assertTrue(caps.canPushFilter(usersPath),
            "Field with 'filter' argument should be pushable for filter");
        assertFalse(caps.canPushSort(usersPath));
        assertFalse(caps.canPushPagination(usersPath));
    }

    @Test
    void discoverDetectsWhereArgument() {
        String query = "{ users(where: $w) { name } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        ServerCapabilities caps = SchemaIntrospector.discover(ctx);

        SchemaPath usersPath = new SchemaPath("users");
        assertTrue(caps.canPushFilter(usersPath),
            "Field with 'where' argument should be pushable for filter");
    }

    @Test
    void discoverDetectsOrderByArgument() {
        String query = "{ users(orderBy: $o) { name } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        ServerCapabilities caps = SchemaIntrospector.discover(ctx);

        SchemaPath usersPath = new SchemaPath("users");
        assertTrue(caps.canPushSort(usersPath),
            "Field with 'orderBy' argument should be pushable for sort");
        assertFalse(caps.canPushFilter(usersPath));
        assertFalse(caps.canPushPagination(usersPath));
    }

    @Test
    void discoverDetectsSortArgument() {
        String query = "{ users(sort: $s) { name } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        ServerCapabilities caps = SchemaIntrospector.discover(ctx);

        SchemaPath usersPath = new SchemaPath("users");
        assertTrue(caps.canPushSort(usersPath),
            "Field with 'sort' argument should be pushable for sort");
    }

    @Test
    void discoverDetectsPaginationArgs() {
        String query = "{ users(first: 10) { name } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        ServerCapabilities caps = SchemaIntrospector.discover(ctx);

        SchemaPath usersPath = new SchemaPath("users");
        assertTrue(caps.canPushPagination(usersPath),
            "Field with 'first' argument should be pushable for pagination");
        assertFalse(caps.canPushFilter(usersPath));
        assertFalse(caps.canPushSort(usersPath));
    }

    @Test
    void discoverNoRecognizedArgs() {
        String query = "{ users(id: 1) { name } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        ServerCapabilities caps = SchemaIntrospector.discover(ctx);

        SchemaPath usersPath = new SchemaPath("users");
        assertFalse(caps.canPushFilter(usersPath));
        assertFalse(caps.canPushSort(usersPath));
        assertFalse(caps.canPushPagination(usersPath));
    }

    @Test
    void discoverMultipleFieldsIndependent() {
        String query = "{ users(filter: $f) { name } posts { title } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        ServerCapabilities caps = SchemaIntrospector.discover(ctx);

        SchemaPath usersPath = new SchemaPath("users");
        SchemaPath postsPath = new SchemaPath("posts");

        assertTrue(caps.canPushFilter(usersPath),
            "users field with 'filter' should support filter push");
        assertFalse(caps.canPushFilter(postsPath),
            "posts field without filter argument should not support filter push");
    }
}
