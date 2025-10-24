package com.ibs.ibs_antdrivers.data

import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object TopicSubscriber {
    fun subscribeToMyGroups(myUid: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = Firebase.database.reference
                val all = db.child("groupMembers").get().await()
                if (!all.exists()) return@launch

                for (g in all.children) {
                    val gid = g.key ?: continue
                    val members = g.value as? Map<*, *> ?: continue
                    val isMember = members.keys.any { (it as? String) == myUid }
                    val topic = "group_$gid"
                    if (isMember) {
                        FirebaseMessaging.getInstance().subscribeToTopic(topic).await()
                    } else {
                        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).await()
                    }
                }
            } catch (_: Exception) { }
        }
    }
}
