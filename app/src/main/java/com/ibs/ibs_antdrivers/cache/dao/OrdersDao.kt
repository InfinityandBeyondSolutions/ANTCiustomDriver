package com.ibs.ibs_antdrivers.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ibs.ibs_antdrivers.cache.entities.OrderEntity
import com.ibs.ibs_antdrivers.cache.entities.OrderItemEntity
import com.ibs.ibs_antdrivers.cache.entities.OrderWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface OrdersDao {

    @Query("SELECT * FROM orders WHERE driverId = :driverId ORDER BY createdAt DESC")
    fun observeOrdersByDriver(driverId: String): Flow<List<OrderEntity>>

    @Transaction
    @Query("SELECT * FROM orders WHERE orderId = :orderId LIMIT 1")
    fun observeOrderWithItems(orderId: String): Flow<OrderWithItems?>

    @Query("SELECT * FROM orders WHERE orderId = :orderId LIMIT 1")
    suspend fun getOrder(orderId: String): OrderEntity?

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun getItems(orderId: String): List<OrderItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrders(items: List<OrderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<OrderItemEntity>)

    @Query("DELETE FROM orders WHERE driverId = :driverId")
    suspend fun deleteOrdersForDriver(driverId: String)

    @Query("DELETE FROM order_items WHERE orderId IN (SELECT orderId FROM orders WHERE driverId = :driverId)")
    suspend fun deleteItemsForDriver(driverId: String)
}

