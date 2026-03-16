// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for Kramer-6g1: LayoutDecisionKey record and cache infrastructure.
 */
class LayoutDecisionCacheTest {

    // ---- LayoutDecisionKey equality ----

    @Test
    void samePathBucketCardinalityAreEqual() {
        var path = new SchemaPath("root").child("items");
        var k1 = LayoutDecisionKey.of(path, 250.0, 5);
        var k2 = LayoutDecisionKey.of(path, 255.0, 5); // same bucket (250/10=25)
        assertEquals(k1, k2, "Keys with the same width bucket must be equal");
        assertEquals(k1.hashCode(), k2.hashCode());
    }

    @Test
    void differentWidthBucketProducesDifferentKey() {
        var path = new SchemaPath("root");
        var k1 = LayoutDecisionKey.of(path, 100.0, 3); // bucket 10
        var k2 = LayoutDecisionKey.of(path, 110.0, 3); // bucket 11
        assertNotEquals(k1, k2, "Different width buckets must produce different keys");
    }

    @Test
    void differentPathProducesDifferentKey() {
        var path1 = new SchemaPath("root").child("a");
        var path2 = new SchemaPath("root").child("b");
        var k1 = LayoutDecisionKey.of(path1, 200.0, 4);
        var k2 = LayoutDecisionKey.of(path2, 200.0, 4);
        assertNotEquals(k1, k2);
    }

    @Test
    void differentCardinalityProducesDifferentKey() {
        var path = new SchemaPath("root");
        var k1 = LayoutDecisionKey.of(path, 200.0, 4);
        var k2 = LayoutDecisionKey.of(path, 200.0, 5);
        assertNotEquals(k1, k2);
    }

    @Test
    void keyRecordAccessors() {
        var path = new SchemaPath("catalog");
        var key = LayoutDecisionKey.of(path, 350.0, 10);
        assertEquals(path, key.path());
        assertEquals((int) (350.0 / 10), key.widthBucket());
        assertEquals(10, key.dataCardinality());
    }

    // ---- Cache store/retrieve ----

    @Test
    void cacheStoresAndRetrievesLayoutResult() {
        Map<LayoutDecisionKey, LayoutResult> cache = new HashMap<>();

        var path = new SchemaPath("root");
        var key = LayoutDecisionKey.of(path, 400.0, 7);
        var result = new LayoutResult(RelationRenderMode.TABLE, PrimitiveRenderMode.TEXT, false, 120.0, 0.0, 100.0, List.of());

        cache.put(key, result);

        var retrieved = cache.get(LayoutDecisionKey.of(path, 405.0, 7)); // same bucket
        assertNotNull(retrieved);
        assertSame(result, retrieved);
    }

    @Test
    void cacheMissReturnsNull() {
        Map<LayoutDecisionKey, LayoutResult> cache = new HashMap<>();

        var path = new SchemaPath("root");
        var key = LayoutDecisionKey.of(path, 400.0, 7);

        assertNull(cache.get(key), "Empty cache must return null");
    }

    @Test
    void cacheClearInvalidatesAllEntries() {
        Map<LayoutDecisionKey, LayoutResult> cache = new HashMap<>();

        var path = new SchemaPath("root");
        cache.put(LayoutDecisionKey.of(path, 100.0, 2),
                  new LayoutResult(RelationRenderMode.OUTLINE, PrimitiveRenderMode.TEXT, false, 50.0, 0.0, 40.0, List.of()));
        cache.put(LayoutDecisionKey.of(path, 200.0, 3),
                  new LayoutResult(RelationRenderMode.TABLE, PrimitiveRenderMode.TEXT, false, 80.0, 0.0, 70.0, List.of()));
        assertEquals(2, cache.size());

        // Simulates stylesheet change or autoLayout() reset
        cache.clear();

        assertTrue(cache.isEmpty(), "Cache must be empty after clear (stylesheet invalidation)");
    }

    @Test
    void cacheInvalidatedOnCardinalityChange() {
        Map<LayoutDecisionKey, LayoutResult> cache = new HashMap<>();

        var path = new SchemaPath("root");
        var width = 300.0;
        int oldCardinality = 5;
        int newCardinality = 10;

        var oldKey = LayoutDecisionKey.of(path, width, oldCardinality);
        cache.put(oldKey, new LayoutResult(RelationRenderMode.OUTLINE, PrimitiveRenderMode.TEXT, false, 60.0, 0.0, 50.0, List.of()));

        // When data changes cardinality the caller must re-measure; old key is stale.
        var newKey = LayoutDecisionKey.of(path, width, newCardinality);
        assertNull(cache.get(newKey),
                   "A key with different cardinality must not hit the old cache entry");
    }

    // ---- widthBucket boundary conditions ----

    @Test
    void widthBucketTruncates() {
        var path = new SchemaPath("x");
        // 99.9 / 10 = 9.99 → (int) = 9
        // 100.0 / 10 = 10.0 → (int) = 10
        var k1 = LayoutDecisionKey.of(path, 99.9, 1);
        var k2 = LayoutDecisionKey.of(path, 100.0, 1);
        assertNotEquals(k1, k2, "Boundary between buckets 9 and 10 must be distinct");
    }

    @Test
    void usableAsMapKey() {
        // Verify the record is a valid map key (equals+hashCode contract)
        var path = new SchemaPath("root");
        Map<LayoutDecisionKey, String> map = new HashMap<>();
        var k = LayoutDecisionKey.of(path, 500.0, 3);
        map.put(k, "value");
        assertEquals("value", map.get(LayoutDecisionKey.of(path, 509.9, 3)));
    }
}
