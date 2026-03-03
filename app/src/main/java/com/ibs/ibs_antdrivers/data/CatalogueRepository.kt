package com.ibs.ibs_antdrivers.data

import android.content.Context
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.CatalogueCategory
import com.ibs.ibs_antdrivers.CatalogueFile
import com.ibs.ibs_antdrivers.cache.entities.CatalogueCategoryEntity
import com.ibs.ibs_antdrivers.offlineupload.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CatalogueRepository(
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference,
) {

    fun observeActiveCategories(context: Context): Flow<List<CatalogueCategory>> {
        return AppDatabase.get(context).catalogueDao().observeActiveCategories()
            .map { list ->
                list.map { it.toDomain() }
            }
    }

    suspend fun refreshCategoriesToRoom(context: Context) {
        val snap = db.child("catalogueCategories").get().await()
        if (!snap.exists()) return

        val entities = snap.children.mapNotNull { node ->
            val cat = node.getValue(CatalogueCategory::class.java) ?: return@mapNotNull null
            val file = cat.Catalogue
            CatalogueCategoryEntity(
                id = cat.Id,
                name = cat.Name ?: "Catalogue ${cat.Id}",
                isActive = cat.IsActive,
                fileName = file?.FileName,
                fileSizeBytes = file?.FileSizeBytes,
                fileUrl = file?.FileUrl,
                uploadedAtUnixMs = file?.UploadedAtUnixMs,
                uploadedBy = file?.UploadedBy,
            )
        }

        val dao = AppDatabase.get(context).catalogueDao()
        dao.deleteAll()
        dao.upsertAll(entities)
    }

    suspend fun getCategory(context: Context, id: Int): CatalogueCategory? {
        val cached = AppDatabase.get(context).catalogueDao().getById(id)
        if (cached != null) return cached.toDomain()

        // fallback to network
        val snap = db.child("catalogueCategories").child(id.toString()).get().await()
        return snap.getValue(CatalogueCategory::class.java)
    }

    private fun CatalogueCategoryEntity.toDomain(): CatalogueCategory {
        return CatalogueCategory(
            Id = id,
            Name = name,
            IsActive = isActive,
            Catalogue = CatalogueFile(
                FileName = fileName,
                FileSizeBytes = fileSizeBytes,
                FileUrl = fileUrl,
                UploadedAtUnixMs = uploadedAtUnixMs,
                UploadedBy = uploadedBy,
            )
        )
    }
}

