/**
 * Copyright (c) 2016 Chiral Behaviors, LLC, all rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chiralbehaviors.layout.query;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for PaginationContext (RDR-027 Phase 3).
 *
 * @author hhildebrand
 */
class PaginationContextTest {

    @Test
    void noneIsNotPageLocal() {
        assertFalse(PaginationContext.NONE.isPageLocal());
        assertFalse(PaginationContext.NONE.paginated());
        assertNull(PaginationContext.NONE.currentPage());
    }

    @Test
    void paginatedIsPageLocal() {
        var ctx = new PaginationContext(true, "cursor123");
        assertTrue(ctx.isPageLocal());
        assertTrue(ctx.paginated());
        assertEquals("cursor123", ctx.currentPage());
    }

    @Test
    void nonPaginatedIsNotPageLocal() {
        var ctx = new PaginationContext(false, null);
        assertFalse(ctx.isPageLocal());
    }

    @Test
    void menuFactoryPaginationAwareness() {
        var style = new com.chiralbehaviors.layout.style.Style(
            new com.chiralbehaviors.layout.ConfiguredMeasurementStrategy());
        var queryState = new LayoutQueryState(style);
        var handler = new InteractionHandler(queryState);
        var factory = new InteractionMenuFactory(handler, queryState);

        // Default: no pagination
        assertEquals(PaginationContext.NONE, factory.getPaginationContext());
        assertFalse(factory.getPaginationContext().isPageLocal());

        // Set paginated
        factory.setPaginationContext(new PaginationContext(true, "abc"));
        assertTrue(factory.getPaginationContext().isPageLocal());

        // Reset to none
        factory.setPaginationContext(null);
        assertFalse(factory.getPaginationContext().isPageLocal());
    }

    @Test
    void columnSortHandlerAcceptsPaginationContext() {
        var style = new com.chiralbehaviors.layout.style.Style(
            new com.chiralbehaviors.layout.ConfiguredMeasurementStrategy());
        var queryState = new LayoutQueryState(style);
        var handler = new InteractionHandler(queryState);
        var sortHandler = new ColumnSortHandler(handler, queryState);

        // Should not throw
        sortHandler.setPaginationContext(new PaginationContext(true, "cursor"));
        sortHandler.setPaginationContext(PaginationContext.NONE);
        sortHandler.setPaginationContext(null);
    }
}
