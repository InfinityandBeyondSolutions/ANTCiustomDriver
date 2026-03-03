package com.ibs.ibs_antdrivers.data

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.cache.dao.PriceListWithCount
import com.ibs.ibs_antdrivers.cache.entities.PriceListEntity
import com.ibs.ibs_antdrivers.cache.entities.PriceListItemEntity
import com.ibs.ibs_antdrivers.offlineupload.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    fun observeAllPriceLists(context: Context): Flow<List<PriceList>> {
        return AppDatabase.get(context).priceListDao().observeAllWithItemCount()
            .map { list ->
                list.map { it.toDomainWithCount() }
            }
    }

    /**
     * Returns the price list with items from Room if cached, otherwise falls back to Firebase.
     * Used for PDF generation/share/download so we always have real items.
     */
    suspend fun getPriceListWithItems(context: Context, id: String): PriceList? {
        val rel = AppDatabase.get(context).priceListDao().observeWithItems(id).first()
        if (rel != null) {
            return rel.priceList.toDomain(rel.items)
        }
        // fallback to Firebase (best effort)
        return runCatching { getAllPriceLists().firstOrNull { it.id == id } }.getOrNull()
    }

    suspend fun getAllPriceLists(): List<PriceList> {
        val snap = db.child("priceLists").get().await()
        if (!snap.exists()) return emptyList()

        return snap.children.mapNotNull { it.toPriceListOrNull() }
            .sortedBy { it.title.ifBlank { it.name } }
    }

    private fun PriceListEntity.toDomain(items: List<PriceListItemEntity>): PriceList {
        return PriceList(
            id = priceListId,
            title = title,
            name = name,
            companyName = companyName,
            effectiveDate = effectiveDate,
            status = status,
            includeVat = includeVat,
            updatedAt = updatedAt,
            createdAt = createdAt,
            items = items.map { it.toDomain() },
        )
    }

    private fun PriceListItemEntity.toDomain(): PriceListItem {
        return PriceListItem(
            id = itemId,
            itemNo = itemNo,
            description = description,
            brand = brand,
            size = size,
            unitBarcode = unitBarcode,
            outerBarcode = outerBarcode,
            unitPrice = unitPrice,
            casePrice = casePrice,
        )
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

    private fun PriceListWithCount.toDomainWithCount(): PriceList {
        // NOTE: list screen needs a count, not the full items.
        // We create a placeholder list sized to itemCount so existing UI using items.size works.
        val placeholderItems = if (itemCount <= 0) emptyList() else List(itemCount) { PriceListItem() }
        return PriceList(
            id = priceListId,
            title = title,
            name = name,
            companyName = companyName,
            effectiveDate = effectiveDate,
            status = status,
            includeVat = includeVat,
            updatedAt = updatedAt,
            createdAt = createdAt,
            items = placeholderItems,
        )
    }
}
