package com.ibs.ibs_antdrivers.rtdbqueue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueuedOrderWriteGuardTest {
    @Test
    fun `recognizes only order creation sets`() {
        assertEquals("order-123", queuedOrderIdOrNull("orders/order-123", RtdbActionType.SET))
        assertEquals("order-123", queuedOrderIdOrNull("/orders/order-123/", RtdbActionType.SET))

        assertNull(queuedOrderIdOrNull("orders/order-123", RtdbActionType.UPDATE))
        assertNull(queuedOrderIdOrNull("orders/order-123", RtdbActionType.DELETE))
        assertNull(queuedOrderIdOrNull("completedOrders/order-123", RtdbActionType.SET))
        assertNull(queuedOrderIdOrNull("orders/order-123/status", RtdbActionType.SET))
        assertNull(queuedOrderIdOrNull("orders/", RtdbActionType.SET))
    }
}
