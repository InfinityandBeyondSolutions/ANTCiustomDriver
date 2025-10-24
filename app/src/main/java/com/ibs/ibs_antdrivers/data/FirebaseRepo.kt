package com.ibs.ibs_antdrivers.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

data class Attachment(
    val url: String? = null,
    val name: String? = null,
    val contentType: String? = null
)

data class Message(
    val senderId: String? = null,
    val senderRole: String? = null,
    val text: String? = null,
    val type: String? = "text",
    val attachment: Attachment? = null,
    val createdAt: Long? = null,
    val deleted: Boolean? = false,

    // Added for ticks + reactions
    val seen: Map<String, Long>? = null,                // uid -> timestamp
    val reactions: Map<String, Map<String, Long>>? = null // emoji -> (uid -> ts)
)

data class Group(val id: String, val name: String? = null, val createdAt: Long? = null)

class FirebaseRepo {
    private val auth = FirebaseAuth.getInstance()
    private val db: DatabaseReference = Firebase.database.reference
    private val storage = FirebaseStorage.getInstance()

    fun uid(): String? = auth.currentUser?.uid

    suspend fun myGroups(): List<Group> {
        val me = uid() ?: return emptyList()
        val membersSnap = db.child("groupMembers").get().await()
        if (!membersSnap.exists()) return emptyList()

        val myGroupIds = membersSnap.children
            .mapNotNull { g ->
                val gid = g.key ?: return@mapNotNull null
                val members = g.value as? Map<*, *> ?: return@mapNotNull null
                if (members.keys.any { (it as? String) == me }) gid else null
            }

        if (myGroupIds.isEmpty()) return emptyList()

        val groupsSnap = db.child("groups").get().await()
        return myGroupIds.map { gid ->
            val g = groupsSnap.child(gid).value as? Map<*, *>
            Group(
                id = gid,
                name = g?.get("name") as? String,
                createdAt = (g?.get("createdAt") as? Number)?.toLong()
            )
        }.sortedByDescending { it.createdAt ?: 0 }
    }

    fun messagesRef(groupId: String): Query =
        db.child("messages").child(groupId).orderByChild("createdAt").limitToLast(500)

    suspend fun markSeen(groupId: String, messageId: String) {
        val me = uid() ?: return
        db.child("messages").child(groupId).child(messageId)
            .child("seen").child(me)
            .setValue(ServerValue.TIMESTAMP)
            .await()
    }

    suspend fun toggleReaction(groupId: String, messageId: String, emoji: String) {
        val me = uid() ?: return
        val key = java.net.URLEncoder.encode(emoji, "UTF-8")
        val node = db.child("messages").child(groupId).child(messageId)
            .child("reactions").child(key).child(me)

        val exists = node.get().await().exists()
        if (exists) node.removeValue().await()
        else node.setValue(ServerValue.TIMESTAMP).await()
    }

    suspend fun sendText(groupId: String, text: String) {
        val me = uid() ?: return
        val now = System.currentTimeMillis()
        val payload = Message(
            senderId = me, senderRole = "driver", text = text, type = "text",
            createdAt = now, deleted = false
        )
        val ref = db.child("messages").child(groupId).push()
        ref.setValue(payload).await()
        ref.child("seen").child(me).setValue(ServerValue.TIMESTAMP).await()

        db.child("lastMessage").child(groupId).setValue(
            mapOf("text" to text, "type" to "text", "createdAt" to now)
        ).await()
    }

    suspend fun sendImage(groupId: String, name: String, bytes: ByteArray, contentType: String) {
        val me = uid() ?: return
        val path = "chat/$groupId/${System.currentTimeMillis()}_$name"
        val ref = storage.reference.child(path)
        ref.putBytes(bytes, com.google.firebase.storage.StorageMetadata.Builder().setContentType(contentType).build()).await()
        val url = ref.downloadUrl.await().toString()
        val now = System.currentTimeMillis()
        val payload = Message(
            senderId = me, senderRole = "driver", text = "",
            type = "image", attachment = Attachment(url, name, contentType),
            createdAt = now, deleted = false
        )
        val mref = db.child("messages").child(groupId).push()
        mref.setValue(payload).await()
        mref.child("seen").child(me).setValue(ServerValue.TIMESTAMP).await()

        db.child("lastMessage").child(groupId).setValue(
            mapOf("text" to "image", "type" to "image", "createdAt" to now)
        ).await()
    }

    suspend fun sendFile(groupId: String, name: String, bytes: ByteArray, contentType: String) {
        val me = uid() ?: return
        val path = "chat/$groupId/${System.currentTimeMillis()}_$name"
        val ref = storage.reference.child(path)
        ref.putBytes(
            bytes,
            com.google.firebase.storage.StorageMetadata.Builder().setContentType(contentType).build()
        ).await()
        val url = ref.downloadUrl.await().toString()
        val now = System.currentTimeMillis()
        val payload = Message(
            senderId = me,
            senderRole = "driver",
            text = "",
            type = "file",
            attachment = Attachment(url, name, contentType),
            createdAt = now,
            deleted = false
        )
        db.child("messages").child(groupId).push().setValue(payload).await()
        db.child("lastMessage").child(groupId).setValue(
            mapOf("text" to (name.ifBlank { "file" }), "type" to "file", "createdAt" to now)
        ).await()
    }

}
