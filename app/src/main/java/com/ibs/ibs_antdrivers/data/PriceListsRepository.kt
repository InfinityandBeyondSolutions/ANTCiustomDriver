package com.ibs.ibs_antdrivers.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

data class PriceListItem(
    val id: String = "",
    val itemNo: String = "",
    val description: String = "",
    val brand: String = "",
    val size: String = "",
    val unitBarcode: String = "",
    val outerBarcode: String = "",
    val unitPrice: String = "",
    val casePrice: String = "",
)

data class PriceList(
    val id: String = "",
    val title: String = "",
    val name: String = "",
    val companyName: String = "",
    val effectiveDate: String = "",
    val status: String = "",
    val includeVat: Boolean = false,
    val updatedAt: String = "",
    val createdAt: String = "",
    val items: List<PriceListItem> = emptyList(),
)

class PriceListsRepository(
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference,
) {
    suspend fun getAllPriceLists(): List<PriceList> {
        val snap = db.child("priceLists").get().await()
        if (!snap.exists()) return emptyList()

        return snap.children.mapNotNull { it.toPriceListOrNull() }
            .sortedBy { it.title.ifBlank { it.name } }
    }

    private fun DataSnapshot.toPriceListOrNull(): PriceList? {
        val keyId = key ?: return null

        val itemsNode = child("items")
        val itemsList: List<PriceListItem> = when {
            !itemsNode.exists() -> emptyList()
            // items stored as array-like list
            itemsNode.childrenCount > 0 -> itemsNode.children.mapNotNull { it.toPriceListItemOrNull() }.toList()
            else -> emptyList()
        }

        return PriceList(
            id = child("id").getValue(String::class.java) ?: keyId,
            title = child("title").getValue(String::class.java) ?: "",
            name = child("name").getValue(String::class.java) ?: "",
            companyName = child("companyName").getValue(String::class.java) ?: "",
            effectiveDate = child("effectiveDate").getValue(String::class.java) ?: "",
            status = child("status").getValue(String::class.java) ?: "",
            includeVat = child("includeVat").getValue(Boolean::class.java) ?: false,
            updatedAt = child("updatedAt").getValue(String::class.java) ?: "",
            createdAt = child("createdAt").getValue(String::class.java) ?: "",
            items = itemsList,
        )
    }

    private fun DataSnapshot.toPriceListItemOrNull(): PriceListItem? {
        val keyId = key ?: ""
        return PriceListItem(
            id = child("id").getValue(String::class.java) ?: keyId,
            itemNo = child("itemNo").getValue(String::class.java) ?: "",
            description = child("description").getValue(String::class.java) ?: "",
            brand = child("brand").getValue(String::class.java) ?: "",
            size = child("size").getValue(String::class.java) ?: "",
            unitBarcode = child("unitBarcode").getValue(String::class.java) ?: "",
            outerBarcode = child("outerBarcode").getValue(String::class.java) ?: "",
            unitPrice = child("unitPrice").getValue(String::class.java) ?: "",
            casePrice = child("casePrice").getValue(String::class.java) ?: "",
        )
    }
}
