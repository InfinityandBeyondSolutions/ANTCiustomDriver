package com.ibs.ibs_antdrivers.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.cache.entities.OrderEntity
import com.ibs.ibs_antdrivers.cache.entities.OrderItemEntity
import com.ibs.ibs_antdrivers.data.Order
import com.ibs.ibs_antdrivers.data.OrderItem
import com.ibs.ibs_antdrivers.offlineupload.AppDatabase
import kotlinx.coroutines.tasks.await

/**
 * Refreshes the logged-in driver's orders into Room for offline-first access.
 */
class RefreshOrdersWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()

        return try {
            val ref = FirebaseDatabase.getInstance().reference

            suspend fun fetchNode(nodeName: String): List<Order> {
                val snap = try {
                    ref.child(nodeName)
                        .orderByChild("driverId")
                        .equalTo(uid)
                        .get()
                        .await()
                } catch (_: Throwable) {
                    // Fallback: if index missing, download all and filter client-side.
                    ref.child(nodeName).get().await()
                }

                if (!snap.exists()) return emptyList()

                return snap.children.mapNotNull { node ->
                    node.toOrderOrNull()
                }.filter { it.driverId == uid }
            }

            val pending = fetchNode("orders")
            val completed = fetchNode("completedOrders").map { o ->
                // Force a consistent completed status for dashboard display.
                if (o.status.equals("completed", ignoreCase = true)) o else o.copy(status = "completed")
            }

            // Merge by id; completed overwrites pending when duplicates exist.
            val merged = LinkedHashMap<String, Order>()
            pending.forEach { if (it.id.isNotBlank()) merged[it.id] = it }
            completed.forEach { if (it.id.isNotBlank()) merged[it.id] = it }

            val orders = merged.values.toList()

            if (orders.isEmpty()) {
                // Keep existing cache; don't clear on empty to avoid accidental data loss.
                return Result.success()
            }

            val orderEntities = orders.map { it.toEntity() }
            val itemEntities = orders.flatMap { o -> o.items.map { it.toEntity(o.id) } }

            val db = AppDatabase.get(applicationContext)
            // Replace driver's orders cache.
            db.ordersDao().deleteItemsForDriver(uid)
            db.ordersDao().deleteOrdersForDriver(uid)
            db.ordersDao().upsertOrders(orderEntities)
            db.ordersDao().upsertItems(itemEntities)

            Result.success()
        } catch (t: Throwable) {
            Log.w("RefreshOrdersWorker", "Failed: ${t.message}")
            Result.retry()
        }
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

    private fun com.google.firebase.database.DataSnapshot.toOrderOrNull(): Order? {
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

    private fun com.google.firebase.database.DataSnapshot.toOrderItemOrNull(): OrderItem? {
        fun numDoubleNode(node: com.google.firebase.database.DataSnapshot, childName: String): Double {
            val v = node.child(childName).value
            return when (v) {
                is Number -> v.toDouble()
                is String -> v.replace(",", "").trim().toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
        }

        fun numIntNode(node: com.google.firebase.database.DataSnapshot, childName: String): Int {
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
