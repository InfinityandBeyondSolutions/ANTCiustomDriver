package com.ibs.ibs_antdrivers.data

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.cache.entities.OrderEntity
import com.ibs.ibs_antdrivers.cache.entities.OrderItemEntity
import com.ibs.ibs_antdrivers.cache.entities.OrderWithItems
import com.ibs.ibs_antdrivers.offlineupload.AppDatabase
import com.ibs.ibs_antdrivers.rtdbqueue.RtdbPath
import com.ibs.ibs_antdrivers.rtdbqueue.RtdbWriteQueue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class OrdersRepository(
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference,
) {

    /**
     * Get all orders submitted by a specific driver
     */
    suspend fun getOrdersByDriver(driverId: String): List<Order> {
        return try {
            val snap = db.child("orders")
                .orderByChild("driverId")
                .equalTo(driverId)
                .get()
                .await()

            if (!snap.exists()) return emptyList()

            snap.children.mapNotNull { it.toOrderOrNull() }
                .sortedByDescending { it.createdAt }
        } catch (t: Throwable) {
            // If the database rules haven't been updated with an index, Firebase throws:
            // "Index not defined, add '.indexOn': 'driverId'..."
            // As a resilience fallback, fetch orders and filter on the client.
            val message = t.message.orEmpty()
            if (message.contains("Index not defined", ignoreCase = true) ||
                message.contains(".indexOn", ignoreCase = true)
            ) {
                getOrdersByDriverClientFilter(driverId)
            } else {
                throw t
            }
        }
    }

    private suspend fun getOrdersByDriverClientFilter(driverId: String): List<Order> {
        val snap = db.child("orders").get().await()
        if (!snap.exists()) return emptyList()

        return snap.children.mapNotNull { it.toOrderOrNull() }
            .filter { it.driverId == driverId }
            .sortedByDescending { it.createdAt }
    }

    /**
     * Get a single order by ID
     */
    suspend fun getOrderById(orderId: String): Order? {
        val snap = db.child("orders").child(orderId).get().await()
        if (!snap.exists()) return null
        return snap.toOrderOrNull()
    }

    /**
     * Create a new order.
     *
     * Offline behavior:
     * - If the write fails (no internet), queue it for background upload.
     */
    suspend fun createOrder(order: Order, appContext: Context? = null): String {
        val ctx = appContext?.applicationContext
        if (ctx == null) {
            // Without context we can't cache/queue safely.
            throw IllegalArgumentException("App context is required for offline order submission")
        }

        val orderRef = db.child("orders").push()
        val orderId = orderRef.key ?: throw Exception("Failed to generate order ID")

        val now = System.currentTimeMillis()
        val orderWithId = order.copy(
            id = orderId,
            createdAt = if (order.createdAt > 0) order.createdAt else now,
            updatedAt = now,
            items = order.items.map { it.copy(orderId = orderId) },
        )

        // 1) Always write to Room cache immediately so the order appears offline.
        runCatching {
            val db = AppDatabase.get(ctx)
            db.ordersDao().upsertOrders(listOf(orderWithId.toEntity()))
            db.ordersDao().upsertItems(orderWithId.items.map { it.toEntity(orderId) })
        }

        val map = orderToMap(orderWithId)

        // 2) Try network write; if it fails, enqueue for later and return success.
        try {
            orderRef.setValue(map).await()
        } catch (_: Throwable) {
            val path = RtdbPath.child("orders", orderId)
            val dedupeKey = "order:$orderId"
            RtdbWriteQueue.enqueueSetMap(
                context = ctx,
                path = path,
                value = map.filterKeys { it != "id" } + ("id" to orderId),
                priority = 1,
                dedupeKey = dedupeKey
            )
        }

        return orderId
    }

    /**
     * Update an existing order
     */
    suspend fun updateOrder(order: Order) {
        val updatedOrder = order.copy(updatedAt = System.currentTimeMillis())
        db.child("orders").child(order.id).setValue(orderToMap(updatedOrder)).await()
    }

    /**
     * Delete an order
     */
    suspend fun deleteOrder(orderId: String) {
        db.child("orders").child(orderId).removeValue().await()
    }

    fun observeOrdersByDriver(context: Context, driverId: String): Flow<List<Order>> {
        return AppDatabase.get(context).ordersDao()
            .observeOrdersByDriver(driverId)
            .map { list -> list.map { it.toDomain(emptyList()) } }
    }

    fun observeOrderWithItems(context: Context, orderId: String): Flow<Order?> {
        return AppDatabase.get(context).ordersDao()
            .observeOrderWithItems(orderId)
            .map { rel -> rel?.toDomain() }
    }

    private fun OrderEntity.toDomain(items: List<OrderItemEntity>): Order {
        return Order(
            id = orderId,
            orderNumber = orderNumber,
            notes = notes,
            storeId = storeId,
            storeName = storeName,
            priceListId = priceListId,
            priceListName = priceListName,
            driverId = driverId,
            driverName = driverName,
            createdByUserId = createdByUserId,
            createdByUserName = createdByUserName,
            createdByFirstName = createdByFirstName,
            createdByLastName = createdByLastName,
            completedByUserId = completedByUserId,
            completedByUserName = completedByUserName,
            completedByFirstName = completedByFirstName,
            completedByLastName = completedByLastName,
            priority = priority,
            status = status,
            totalAmount = totalAmount,
            createdAt = createdAt,
            updatedAt = updatedAt,
            items = items.map { it.toDomain() },
        )
    }

    private fun OrderWithItems.toDomain(): Order {
        return order.toDomain(items)
    }

    private fun OrderItemEntity.toDomain(): OrderItem {
        return OrderItem(
            id = itemId,
            orderId = orderId,
            productId = productId,
            productCode = productCode,
            productName = productName,
            brand = brand,
            size = size,
            unitBarcode = unitBarcode,
            outerBarcode = outerBarcode,
            unitPriceExVat = unitPriceExVat,
            casePriceExVat = casePriceExVat,
            quantity = quantity,
            totalPrice = totalPrice,
        )
    }

    private fun orderToMap(order: Order): Map<String, Any?> {
        return mapOf(
            "id" to order.id,
            "orderNumber" to order.orderNumber,
            "notes" to order.notes,

            "storeId" to order.storeId,
            "storeName" to order.storeName,

            "priceListId" to order.priceListId,
            "priceListName" to order.priceListName,

            "driverId" to order.driverId,
            "driverName" to order.driverName,

            "createdByUserId" to order.createdByUserId,
            "createdByUserName" to order.createdByUserName,
            "createdByFirstName" to order.createdByFirstName,
            "createdByLastName" to order.createdByLastName,

            "completedByUserId" to order.completedByUserId,
            "completedByUserName" to order.completedByUserName,
            "completedByFirstName" to order.completedByFirstName,
            "completedByLastName" to order.completedByLastName,

            "priority" to order.priority,
            "status" to order.status,

            "totalAmount" to order.totalAmount,

            "createdAt" to order.createdAt,
            "updatedAt" to order.updatedAt,

            // Firebase screenshot shows items as an array with numeric keys, so we store a list.
            "items" to order.items.map { itemToMap(it) },
        )
    }

    private fun itemToMap(item: OrderItem): Map<String, Any?> {
        return mapOf(
            "id" to item.id,
            "orderId" to item.orderId,

            "productId" to item.productId,
            "productCode" to item.productCode,
            "productName" to item.productName,

            "brand" to item.brand,
            "size" to item.size,

            "unitBarcode" to item.unitBarcode,
            "outerBarcode" to item.outerBarcode,

            "unitPriceExVat" to item.unitPriceExVat,
            "casePriceExVat" to item.casePriceExVat,

            "quantity" to item.quantity,
            "totalPrice" to item.totalPrice,
        )
    }

    private fun DataSnapshot.toOrderOrNull(): Order? {
        val keyId = key ?: return null

        val itemsNode = child("items")
        val itemsList: List<OrderItem> = when {
            !itemsNode.exists() -> emptyList()
            itemsNode.childrenCount > 0 -> itemsNode.children.mapNotNull { it.toOrderItemOrNull() }.toList()
            else -> emptyList()
        }

        fun numDouble(childName: String): Double {
            val n = child(childName).value
            return when (n) {
                is Number -> n.toDouble()
                is String -> n.replace(",", "").trim().toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
        }

        fun numLong(childName: String): Long {
            val n = child(childName).value
            return when (n) {
                is Number -> n.toLong()
                is String -> n.trim().toLongOrNull() ?: 0L
                else -> 0L
            }
        }

        return Order(
            id = child("id").getValue(String::class.java) ?: keyId,
            orderNumber = child("orderNumber").getValue(String::class.java) ?: "",
            notes = child("notes").getValue(String::class.java) ?: "",

            storeId = child("storeId").getValue(String::class.java) ?: "",
            storeName = child("storeName").getValue(String::class.java) ?: "",

            priceListId = child("priceListId").getValue(String::class.java) ?: "",
            priceListName = child("priceListName").getValue(String::class.java) ?: "",

            driverId = child("driverId").getValue(String::class.java) ?: "",
            driverName = child("driverName").getValue(String::class.java) ?: "",

            createdByUserId = child("createdByUserId").getValue(String::class.java) ?: "",
            createdByUserName = child("createdByUserName").getValue(String::class.java) ?: "",
            createdByFirstName = child("createdByFirstName").getValue(String::class.java) ?: "",
            createdByLastName = child("createdByLastName").getValue(String::class.java) ?: "",

            completedByUserId = child("completedByUserId").getValue(String::class.java) ?: "",
            completedByUserName = child("completedByUserName").getValue(String::class.java) ?: "",
            completedByFirstName = child("completedByFirstName").getValue(String::class.java) ?: "",
            completedByLastName = child("completedByLastName").getValue(String::class.java) ?: "",

            priority = child("priority").getValue(String::class.java) ?: "normal",
            status = child("status").getValue(String::class.java) ?: "pending",

            totalAmount = numDouble("totalAmount"),
            createdAt = numLong("createdAt"),
            updatedAt = numLong("updatedAt"),

            items = itemsList,
        )
    }

    private fun DataSnapshot.toOrderItemOrNull(): OrderItem? {
        fun numDoubleNode(node: DataSnapshot, childName: String): Double {
            val v = node.child(childName).value
            return when (v) {
                is Number -> v.toDouble()
                is String -> v.replace(",", "").trim().toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
        }

        fun numIntNode(node: DataSnapshot, childName: String): Int {
            val v = node.child(childName).value
            return when (v) {
                is Number -> v.toInt()
                is String -> v.trim().toIntOrNull() ?: 0
                else -> 0
            }
        }

        val keyId = child("id").getValue(String::class.java) ?: key ?: ""

        return OrderItem(
            id = keyId,
            orderId = child("orderId").getValue(String::class.java) ?: "",

            productId = child("productId").getValue(String::class.java) ?: "",
            productCode = child("productCode").getValue(String::class.java) ?: "",
            productName = child("productName").getValue(String::class.java) ?: "",

            brand = child("brand").getValue(String::class.java) ?: "",
            size = child("size").getValue(String::class.java) ?: "",

            unitBarcode = child("unitBarcode").getValue(String::class.java) ?: "",
            outerBarcode = child("outerBarcode").getValue(String::class.java) ?: "",

            unitPriceExVat = numDoubleNode(this, "unitPriceExVat"),
            casePriceExVat = numDoubleNode(this, "casePriceExVat"),

            quantity = numIntNode(this, "quantity"),
            totalPrice = numDoubleNode(this, "totalPrice"),
        )
    }

    private fun Order.toEntity(): OrderEntity {
        return OrderEntity(
            orderId = id,
            orderNumber = orderNumber,
            notes = notes,
            storeId = storeId,
            storeName = storeName,
            priceListId = priceListId,
            priceListName = priceListName,
            driverId = driverId,
            driverName = driverName,
            createdByUserId = createdByUserId,
            createdByUserName = createdByUserName,
            createdByFirstName = createdByFirstName,
            createdByLastName = createdByLastName,
            completedByUserId = completedByUserId,
            completedByUserName = completedByUserName,
            completedByFirstName = completedByFirstName,
            completedByLastName = completedByLastName,
            priority = priority,
            status = status,
            totalAmount = totalAmount,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun OrderItem.toEntity(orderId: String): OrderItemEntity {
        return OrderItemEntity(
            orderId = orderId,
            itemId = id,
            productId = productId,
            productCode = productCode,
            productName = productName,
            brand = brand,
            size = size,
            unitBarcode = unitBarcode,
            outerBarcode = outerBarcode,
            unitPriceExVat = unitPriceExVat,
            casePriceExVat = casePriceExVat,
            quantity = quantity,
            totalPrice = totalPrice,
        )
    }

    /**
     * Get all orders (pending + completed) submitted by a specific driver.
     *
     * Data sources:
     * - /orders (recent / open)
     * - /completedOrders (historic completed)
     */
    suspend fun getAllOrdersByDriver(driverId: String): List<Order> {
        val pending = getOrdersByDriverFromNode(nodeName = "orders", driverId = driverId)
        val completed = getOrdersByDriverFromNode(nodeName = "completedOrders", driverId = driverId)

        // Merge by order id. If an order exists in both nodes, prefer the completed version.
        // Sort newest first using updatedAt fallback to createdAt.
        val merged = LinkedHashMap<String, Order>()
        pending.forEach { o ->
            if (o.id.isNotBlank()) merged[o.id] = o
        }
        completed.forEach { o ->
            if (o.id.isNotBlank()) merged[o.id] = o.copy(
                // Ensure completed orders show as completed in the UI even if status is stale.
                status = o.status.ifBlank { "completed" }.let { s -> if (s.equals("new", true) || s.equals("pending", true) || s.equals("submitted", true)) "completed" else s }
            )
        }

        return merged.values
            .sortedByDescending { if (it.updatedAt > 0) it.updatedAt else it.createdAt }
    }

    private suspend fun getOrdersByDriverFromNode(nodeName: String, driverId: String): List<Order> {
        return try {
            val snap = db.child(nodeName)
                .orderByChild("driverId")
                .equalTo(driverId)
                .get()
                .await()

            if (!snap.exists()) return emptyList()

            snap.children.mapNotNull { it.toOrderOrNull() }
                .sortedByDescending { if (it.updatedAt > 0) it.updatedAt else it.createdAt }
        } catch (t: Throwable) {
            // Resilience fallback when missing indexes.
            val message = t.message.orEmpty()
            if (message.contains("Index not defined", ignoreCase = true) ||
                message.contains(".indexOn", ignoreCase = true)
            ) {
                val snap = db.child(nodeName).get().await()
                if (!snap.exists()) return emptyList()

                snap.children.mapNotNull { it.toOrderOrNull() }
                    .filter { it.driverId == driverId }
                    .sortedByDescending { if (it.updatedAt > 0) it.updatedAt else it.createdAt }
            } else {
                throw t
            }
        }
    }
}
