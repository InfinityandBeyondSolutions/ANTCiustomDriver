package com.ibs.ibs_antdrivers.data

import org.junit.Assert.*
import org.junit.Test

class StoreCachePolicyTest {
    @Test fun `complete cache is reused during the refresh window`() {
        assertFalse(storeCacheNeedsRefresh(1000, 2000, 1315, 1315))
        assertFalse(storeCacheNeedsRefresh(1000, 1000 + STORE_CACHE_MAX_AGE_MS - 1, 1315, 1315))
    }

    @Test fun `expiry clock rollback and first installation require refresh`() {
        assertTrue(storeCacheNeedsRefresh(1000, 1000 + STORE_CACHE_MAX_AGE_MS, 1315, 1315))
        assertTrue(storeCacheNeedsRefresh(1000, 999, 1315, 1315))
        assertTrue(storeCacheNeedsRefresh(0, 2000, 10, -1))
    }

    @Test fun `cleared or partially populated database cannot masquerade as a full cache`() {
        assertTrue(storeCacheNeedsRefresh(1000, 2000, 0, 1315))
        assertTrue(storeCacheNeedsRefresh(1000, 2000, 1, 1315))
        assertTrue(storeCacheNeedsRefresh(1000, 2000, 10, -1))
    }

    @Test fun `an empty successful directory is cached without repeatedly downloading it`() {
        assertFalse(storeCacheNeedsRefresh(1000, 2000, 0, 0))
    }
}
