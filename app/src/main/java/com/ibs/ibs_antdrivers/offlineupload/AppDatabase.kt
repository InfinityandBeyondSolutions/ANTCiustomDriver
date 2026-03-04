package com.ibs.ibs_antdrivers.offlineupload

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ibs.ibs_antdrivers.cache.dao.CallCyclesDao
import com.ibs.ibs_antdrivers.cache.dao.CatalogueDao
import com.ibs.ibs_antdrivers.cache.dao.OrdersDao
import com.ibs.ibs_antdrivers.cache.dao.PriceListDao
import com.ibs.ibs_antdrivers.cache.dao.StoreDao
import com.ibs.ibs_antdrivers.cache.entities.CatalogueCategoryEntity
import com.ibs.ibs_antdrivers.cache.entities.CcActiveWeekEntity
import com.ibs.ibs_antdrivers.cache.entities.CcSpontaneousCallEntity
import com.ibs.ibs_antdrivers.cache.entities.CcTemplateDayEntity
import com.ibs.ibs_antdrivers.cache.entities.CcTemplateDayStoreEntity
import com.ibs.ibs_antdrivers.cache.entities.CcTemplateEntity
import com.ibs.ibs_antdrivers.cache.entities.CcVisitedEntity
import com.ibs.ibs_antdrivers.cache.entities.CcWeekDayEntity
import com.ibs.ibs_antdrivers.cache.entities.CcWeekDayStoreEntity
import com.ibs.ibs_antdrivers.cache.entities.CcWeekEntity
import com.ibs.ibs_antdrivers.cache.entities.OrderEntity
import com.ibs.ibs_antdrivers.cache.entities.OrderItemEntity
import com.ibs.ibs_antdrivers.cache.entities.PriceListEntity
import com.ibs.ibs_antdrivers.cache.entities.PriceListItemEntity
import com.ibs.ibs_antdrivers.cache.entities.StoreEntity
import com.ibs.ibs_antdrivers.rtdbqueue.PendingRtdbAction
import com.ibs.ibs_antdrivers.rtdbqueue.PendingRtdbActionDao

@Database(
    entities = [
        UploadImageEntity::class,
        PendingRtdbAction::class,
        // Offline caches
        StoreEntity::class,
        PriceListEntity::class,
        PriceListItemEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        // Calling cycles
        CcActiveWeekEntity::class,
        CcWeekEntity::class,
        CcWeekDayEntity::class,
        CcWeekDayStoreEntity::class,
        CcTemplateEntity::class,
        CcTemplateDayEntity::class,
        CcTemplateDayStoreEntity::class,
        CcSpontaneousCallEntity::class,
        CcVisitedEntity::class,
        com.ibs.ibs_antdrivers.cache.entities.CcTodayPlannedStoreEntity::class,
        CatalogueCategoryEntity::class,
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun uploadImageDao(): UploadImageDao
    abstract fun pendingRtdbActionDao(): PendingRtdbActionDao

    // Offline cache DAOs
    abstract fun storeDao(): StoreDao
    abstract fun priceListDao(): PriceListDao
    abstract fun ordersDao(): OrdersDao

    // Calling cycles DAO
    abstract fun callCyclesDao(): CallCyclesDao

    abstract fun catalogueDao(): CatalogueDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "antcustom_driver.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
