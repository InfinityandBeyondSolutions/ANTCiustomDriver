package com.ibs.ibs_antdrivers.data

// Matches Firebase Realtime Database schema under: orders/{orderId}

data class OrderItem(
    val id: String = "",
    val orderId: String = "",

    val productId: String = "",
    val productCode: String = "",
    val productName: String = "",

    val brand: String = "",
    val size: String = "",

    val unitBarcode: String = "",
    val outerBarcode: String = "",

    val unitPriceExVat: Double = 0.0,
    val casePriceExVat: Double = 0.0,

    val quantity: Int = 0,
    val totalPrice: Double = 0.0,
)

data class Order(
    val id: String = "",

    val orderNumber: String = "",
    val notes: String = "",

    val storeId: String = "",
    val storeName: String = "",

    val priceListId: String = "",
    val priceListName: String = "",

    val driverId: String = "",
    val driverName: String = "",

    val createdByUserId: String = "",
    val createdByUserName: String = "",
    val createdByFirstName: String = "",
    val createdByLastName: String = "",

    val completedByUserId: String = "",
    val completedByUserName: String = "",
    val completedByFirstName: String = "",
    val completedByLastName: String = "",

    /** ISO-8601 string from Firebase completedOrders.completedAt */
    val completedAt: String = "",

    val priority: String = "normal",
    val status: String = "pending",

    val totalAmount: Double = 0.0,

    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,

    val items: List<OrderItem> = emptyList(),
)
