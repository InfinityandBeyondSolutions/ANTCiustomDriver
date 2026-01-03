package com.ibs.ibs_antdrivers.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.StoreData
import kotlinx.coroutines.tasks.await

class StoresRepository(
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference,
) {
    suspend fun getStoreById(storeId: String): StoreData? {
        val snap = db.child("Stores").child(storeId).get().await()
        if (!snap.exists()) return null

        // Prefer manual mapping so we don't blow up if the node contains unexpected numeric types.
        return snap.toStoreData().copy(StoreID = storeId)
    }

    private fun DataSnapshot.toStoreData(): StoreData {
        return StoreData(
            StoreID = key ?: "",
            StoreName = child("StoreName").getValue(String::class.java) ?: "",
            StoreRegion = child("StoreRegion").getValue(String::class.java) ?: "",
            StoreFranchise = child("StoreFranchise").getValue(String::class.java) ?: "",
            StoreContactNum = child("StoreContactNum").getValue(String::class.java) ?: "",
            RepName = child("RepName").getValue(String::class.java) ?: "",
            ContactPerson = child("ContactPerson").getValue(String::class.java) ?: "",
            StoreEmail = child("StoreEmail").getValue(String::class.java) ?: "",
            StoreAddress = child("StoreAddress").getValue(String::class.java) ?: "",
        )
    }
}

