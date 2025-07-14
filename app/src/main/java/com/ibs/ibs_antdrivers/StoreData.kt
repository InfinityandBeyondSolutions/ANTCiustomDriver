
package com.ibs.ibs_antdrivers

import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val currentUserID = FirebaseAuth.getInstance().currentUser?.uid

data class StoreData(
    var StoreID: String = "",
    var StoreName: String ="",
    var StoreRegion: String ="",
    var StoreFranchise : String = "",
    var StoreOwner : String = "",
    var StoreManager : String = "",
    var StoreContactNum : String = "",
    var StoreAddress: String = ""
)
