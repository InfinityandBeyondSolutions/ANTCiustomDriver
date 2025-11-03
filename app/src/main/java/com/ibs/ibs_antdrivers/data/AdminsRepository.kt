// AdminsRepository.kt
package com.ibs.ibs_antdrivers.data

import com.google.firebase.database.*

class AdminsRepository {

    private val ref: DatabaseReference =
        FirebaseDatabase.getInstance().getReference("admins")

    private var listener: ValueEventListener? = null

    fun startListening(onUpdate: (List<AdminContact>) -> Unit, onError: (Exception) -> Unit) {
        stopListening()

        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<AdminContact>()
                for (child in snapshot.children) {
                    val id = child.key ?: continue
                    val username = child.child("Username").getValue(String::class.java) ?: ""
                    val email = child.child("Email").getValue(String::class.java)
                    val phone = child.child("PhoneNumber").getValue(String::class.java)

                    list += AdminContact(
                        id = id,
                        username = username,
                        email = email,
                        phoneNumber = phone
                    )
                }
                // sort by name (cute, predictable)
                onUpdate(list.sortedBy { it.username.lowercase() })
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.toException())
            }
        }

        ref.addValueEventListener(listener as ValueEventListener)
    }

    fun stopListening() {
        listener?.let { ref.removeEventListener(it) }
        listener = null
    }
}
