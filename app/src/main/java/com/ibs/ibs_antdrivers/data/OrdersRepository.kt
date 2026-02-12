package com.ibs.ibs_antdrivers.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class OrdersRepository(
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference,
) {

    /**
     * Get all orders submitted by a specific driver
     */
    suspend fun getOrdersByDriver(driverId: String): List<Order> {
        val snap = db.child("orders")
            .orderByChild("driverId")
            .equalTo(driverId)
            .get()
            .await()

        if (!snap.exists()) return emptyList()

        return snap.children.mapNotNull { it.toOrderOrNull() }
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
     * Create a new order
     */
    suspend fun createOrder(order: Order): String {
        val orderRef = db.child("orders").push()
        val orderId = orderRef.key ?: throw Exception("Failed to generate order ID")

        val now = System.currentTimeMillis()
        val orderWithId = order.copy(
            id = orderId,
            createdAt = if (order.createdAt > 0) order.createdAt else now,
            updatedAt = now,
            items = order.items.map { it.copy(orderId = orderId) },
        )

        orderRef.setValue(orderToMap(orderWithId)).await()
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
}
