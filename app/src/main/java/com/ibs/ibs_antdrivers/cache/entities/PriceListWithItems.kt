package com.ibs.ibs_antdrivers.cache.entities

import androidx.room.Embedded
import androidx.room.Relation

data class PriceListWithItems(
    @Embedded
    val priceList: PriceListEntity,
    @Relation(
        parentColumn = "priceListId",
        entityColumn = "priceListId"
    )
    val items: List<PriceListItemEntity>
)

