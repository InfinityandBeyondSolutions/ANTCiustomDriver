// AdminContactsAdapter.kt
package com.ibs.ibs_antdrivers.ui.phonebook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.ibs.ibs_antdrivers.R
import com.ibs.ibs_antdrivers.data.AdminContact
import kotlin.math.abs

class AdminContactsAdapter(
    private val context: Context
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_HEADER = 1
        private const val VIEW_CONTACT = 2
    }

    // Public getters for index & decoration
    fun getAvailableSections(): List<Char> = sectionPositions.keys.toList()
    fun findPositionForSection(section: Char): Int? = sectionPositions[section]
    fun isHeader(position: Int): Boolean = position in rows.indices && rows[position] is Row.Header
    fun getSectionForPosition(position: Int): String? =
        (rows.getOrNull(position) as? Row.Header)?.title
            ?: (rows.getOrNull(position) as? Row.Contact)?.section

    // Input contacts (after filtering)
    private var contacts: List<AdminContact> = emptyList()

    // Rendered rows (headers + contacts)
    private val rows = mutableListOf<Row>()
    private val sectionPositions = linkedMapOf<Char, Int>() // section char -> adapter index

    sealed class Row {
        data class Header(val title: String) : Row()
        data class Contact(
            val data: AdminContact,
            val section: String
        ) : Row()
    }

    fun submitContacts(newContacts: List<AdminContact>) {
        contacts = newContacts
        rebuildRows()
    }

    private fun rebuildRows() {
        rows.clear()
        sectionPositions.clear()

        // Group by first letter (A–Z) else '#'
        val grouped = contacts.groupBy { initialOf(it.username) }
            .toSortedMap(compareBy { it })

        var index = 0
        grouped.forEach { (letter, list) ->
            val title = letter.toString()
            rows += Row.Header(title)
            sectionPositions[letter] = index
            index++

            list.sortedBy { it.username.lowercase() }.forEach { c ->
                rows += Row.Contact(c, title)
                index++
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Header -> VIEW_HEADER
        is Row.Contact -> VIEW_CONTACT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            VIEW_HEADER -> HeaderVH(
                LayoutInflater.from(parent.context).inflate(R.layout.item_admin_header, parent, false)
            )
            else -> ContactVH(
                LayoutInflater.from(parent.context).inflate(R.layout.item_admin_contact, parent, false)
            )
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderVH).bind(row.title)
            is Row.Contact -> (holder as ContactVH).bind(row.data)
        }
    }

    override fun getItemCount(): Int = rows.size

    inner class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.sectionTitle)
        fun bind(text: String) {
            title.text = text
        }
    }

    inner class ContactVH(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view as MaterialCardView
        private val avatar: TextView = view.findViewById(R.id.avatar)
        private val name: TextView = view.findViewById(R.id.name)
        private val btnCall: ImageButton = view.findViewById(R.id.btnCall)
        private val btnEmail: ImageButton = view.findViewById(R.id.btnEmail)

        fun bind(item: AdminContact) {
            val displayName = item.username.ifBlank { "Unknown Admin" }
            name.text = displayName

            val initials = displayName.trim().split(" ")
                .filter { it.isNotEmpty() }
                .take(2)
                .joinToString("") { it[0].uppercase() }
                .ifBlank { "A" }
            avatar.text = initials
            avatar.background.setTint(pastelFromId(item.id))

            val hasPhone = !item.phoneNumber.isNullOrBlank()
            val hasEmail = !item.email.isNullOrBlank()

            btnCall.isEnabled = hasPhone
            btnEmail.isEnabled = hasEmail
            btnCall.alpha = if (hasPhone) 1f else 0.3f
            btnEmail.alpha = if (hasEmail) 1f else 0.3f

            // Tap actions
            btnCall.setOnClickListener {
                item.phoneNumber?.let { phone ->
                    ContextCompat.startActivity(
                        context,
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}")),
                        null
                    )
                }
            }
            btnEmail.setOnClickListener {
                item.email?.let { email ->
                    ContextCompat.startActivity(
                        context,
                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${email.trim()}")),
                        null
                    )
                }
            }

            // Long-press copy + haptics
            btnCall.setOnLongClickListener {
                if (hasPhone) {
                    haptic()
                    copyToClipboard("Phone", item.phoneNumber!!.trim())
                    toast("Phone number copied")
                    true
                } else false
            }
            btnEmail.setOnLongClickListener {
                if (hasEmail) {
                    haptic()
                    copyToClipboard("Email", item.email!!.trim())
                    toast("Email address copied")
                    true
                } else false
            }
            card.setOnLongClickListener {
                haptic()
                val phonePart = item.phoneNumber?.takeIf { it.isNotBlank() } ?: "N/A"
                val emailPart = item.email?.takeIf { it.isNotBlank() } ?: "N/A"
                copyToClipboard("Contact", "$displayName — $phonePart — $emailPart")
                toast("Contact copied")
                true
            }
        }

        private fun haptic() = itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    private fun pastelFromId(id: String): Int {
        val base = abs(id.hashCode()) % 6
        val colors = listOf(
            0xFFE3F2FD.toInt(), // blue-50
            0xFFFCE4EC.toInt(), // pink-50
            0xFFE8F5E9.toInt(), // green-50
            0xFFFFF3E0.toInt(), // orange-50
            0xFFEDE7F6.toInt(), // purple-50
            0xFFFFEBEE.toInt()  // red-50
        )
        return colors[base]
    }

    private fun initialOf(name: String): Char {
        val c = name.trim().firstOrNull()?.uppercaseChar() ?: '#'
        return if (c in 'A'..'Z') c else '#'
    }
}
