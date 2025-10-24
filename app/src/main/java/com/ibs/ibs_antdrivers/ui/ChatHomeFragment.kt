package com.ibs.ibs_antdrivers.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.firebase.database.*
import com.ibs.ibs_antdrivers.R
import com.ibs.ibs_antdrivers.data.FirebaseRepo
import com.ibs.ibs_antdrivers.data.Message
import com.ibs.ibs_antdrivers.viewer.FileViewerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class ChatHomeFragment : Fragment() {

    private val repo = FirebaseRepo()

    private lateinit var title: TextView
    private lateinit var list: RecyclerView
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var attach: ImageButton
    private lateinit var progress: ProgressBar
    private lateinit var btnBack: ImageView

    private val items = mutableListOf<Pair<String, Message>>() // id -> message
    private lateinit var adapter: ChatAdapter
    private var groupId: String = ""

    private var childListener: ChildEventListener? = null
    private var nameListener: ValueEventListener? = null

    // ---- Pickers ----
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadImage(it) }
    }

    private val pickDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            // keep read permission across restarts
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            uploadDocument(uri)
        }
    }

    companion object {
        private const val ARG_GROUP_ID = "groupId"
        private const val ARG_GROUP_NAME = "groupName"

        fun newInstance(groupId: String, groupName: String? = null) = ChatHomeFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_GROUP_ID, groupId)
                if (!groupName.isNullOrBlank()) putString(ARG_GROUP_NAME, groupName)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_chat_home, container, false).also {
            title = it.findViewById(R.id.title)
            list = it.findViewById(R.id.recycler)
            input = it.findViewById(R.id.input)
            send = it.findViewById(R.id.btnSend)
            attach = it.findViewById(R.id.btnAttach)
            progress = it.findViewById(R.id.progress)
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        groupId = arguments?.getString(ARG_GROUP_ID).orEmpty()
        if (groupId.isBlank()) {
            Toast.makeText(requireContext(), "Missing groupId", Toast.LENGTH_LONG).show()
            return
        }

        // 1) If caller passed a name, use it immediately
        val passedName = arguments?.getString(ARG_GROUP_NAME)
        if (!passedName.isNullOrBlank()) {
            title.text = passedName
        } else {
            title.text = "Group" // placeholder while we fetch
        }

        // 2) Always keep name in sync with RTDB (covers deep links & server updates)
        val nameRef = FirebaseDatabase.getInstance().reference
            .child("groups").child(groupId).child("name")
        nameListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val n = snapshot.getValue(String::class.java)
                if (!n.isNullOrBlank()) title.text = n
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        nameRef.addValueEventListener(nameListener!!)

        adapter = ChatAdapter(
            data = items,
            myUid = repo.uid(),
            onOpenUrl = { url -> openInAppViewer(url) } // change to openExternal(url) to use external apps
        )
        list.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        list.adapter = adapter

        btnBack = view.findViewById(R.id.btnBackToGroupPage)

        btnBack.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, GroupListFragment())
                .addToBackStack(null)
                .commit()

        }

        send.setOnClickListener {
            val t = input.text.toString().trim()
            if (t.isEmpty()) return@setOnClickListener
            send.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    repo.sendText(groupId, t)
                    input.setText("")
                    // don't scroll here; the listener will scroll when the item arrives
                } catch (e: Exception) {
                    toast("Send failed: ${e.message}")
                } finally {
                    send.isEnabled = true
                }
            }
        }

        attach.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Attach")
                .setItems(arrayOf("Photo", "Document")) { _, which ->
                    when (which) {
                        0 -> pickImage.launch("image/*")
                        1 -> pickDocument.launch(
                            arrayOf(
                                "application/pdf",
                                "application/msword",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-powerpoint",
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                "text/plain",
                                "application/zip",
                                "*/*" // fallback
                            )
                        )
                    }
                }
                .show()
        }

        // Live message stream
        val q = repo.messagesRef(groupId)
        childListener = object : ChildEventListener {
            override fun onChildAdded(ds: DataSnapshot, prev: String?) {
                val m = ds.getValue(Message::class.java) ?: return
                items.add(ds.key!! to m)
                adapter.notifyItemInserted(items.lastIndex)
                list.scrollToPosition(items.lastIndex)
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        q.addChildEventListener(childListener as ChildEventListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up listeners to avoid leaks/duplicates
        childListener?.let { repo.messagesRef(groupId).removeEventListener(it) }
        childListener = null

        nameListener?.let {
            FirebaseDatabase.getInstance().reference
                .child("groups").child(groupId).child("name")
                .removeEventListener(it)
        }
        nameListener = null
    }

    // ---- Upload helpers ----

    private fun uploadImage(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                progress.isVisible = true
                val (name, mime) = withContext(Dispatchers.IO) { queryNameAndType(uri) }
                val bytes = requireContext().contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                repo.sendImage(groupId, name ?: "image.jpg", bytes, mime ?: "image/jpeg")
            } catch (e: Exception) {
                toast("Upload failed: ${e.message}")
            } finally {
                progress.isVisible = false
            }
        }
    }

    private suspend fun readAllBytes(uri: Uri): ByteArray {
        return withContext(Dispatchers.IO) {
            requireContext().contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        }
    }

    private fun extFromName(name: String?): String {
        val n = name ?: return ""
        val dot = n.lastIndexOf('.')
        return if (dot >= 0) n.substring(dot) else ""
    }

    private fun uploadDocument(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                progress.isVisible = true
                val (name, mime) = withContext(Dispatchers.IO) { queryNameAndType(uri) }
                val bytes = readAllBytes(uri)
                repo.sendFile(
                    groupId,
                    name ?: "document${extFromName(name)}",
                    bytes,
                    mime ?: "application/octet-stream"
                )
            } catch (e: Exception) {
                toast("Upload failed: ${e.message}")
            } finally {
                progress.isVisible = false
            }
        }
    }

    private fun queryNameAndType(uri: Uri): Pair<String?, String?> {
        val c = requireContext().contentResolver.query(uri, null, null, null, null)
        val name = c?.use {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && idx >= 0) it.getString(idx) else null
        }
        val mime = requireContext().contentResolver.getType(uri)
        return name to mime
    }

    // ---- Openers ----

    /** In-app viewer (WebView + Google Docs Viewer) */
    private fun openInAppViewer(url: String) {
        startActivity(Intent(requireContext(), FileViewerActivity::class.java).apply {
            putExtra("url", url)
        })
    }

    /** External apps (Drive/Docs/Excel/Browser) */
    @Suppress("unused")
    private fun openExternal(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()

    /* ---------- Adapter & ViewHolder ---------- */

    private class ChatAdapter(
        private val data: List<Pair<String, Message>>,
        private val myUid: String?,
        private val onOpenUrl: (String) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            val m = data[position].second
            return if (m.senderId == myUid) 1 else 2
        }

        override fun onCreateViewHolder(p: ViewGroup, vt: Int): RecyclerView.ViewHolder {
            val layout = if (vt == 1) R.layout.row_msg_right else R.layout.row_msg_left
            val v = LayoutInflater.from(p.context).inflate(layout, p, false)
            return VH(v)
        }

        override fun getItemCount() = data.size

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, i: Int) {
            (h as VH).bind(data[i].second, onOpenUrl)
        }

        private class VH(v: View) : RecyclerView.ViewHolder(v) {
            private val txt: TextView = v.findViewById(R.id.txt)
            private val img: ImageView = v.findViewById(R.id.img)
            private val fileChip: TextView = v.findViewById(R.id.fileChip)
            private val meta: TextView = v.findViewById(R.id.meta)

            fun bind(m: Message, onOpenUrl: (String) -> Unit) {
                // --- IMAGE ---
                val isImage = m.type == "image" && !m.attachment?.url.isNullOrBlank()
                if (isImage) {
                    img.visibility = View.VISIBLE
                    img.adjustViewBounds = true
                    img.load(m.attachment!!.url) {
                        crossfade(true)
                        placeholder(android.R.color.transparent)
                    }
                } else {
                    img.visibility = View.GONE
                }

                // --- FILE ---
                val isFile = m.type == "file" && !m.attachment?.url.isNullOrBlank()
                if (isFile) {
                    fileChip.visibility = View.VISIBLE
                    val label = "📄 " + (m.attachment?.name ?: "Document")
                    fileChip.text = label
                    fileChip.setOnClickListener {
                        val url = m.attachment?.url ?: return@setOnClickListener
                        onOpenUrl(url)
                    }
                } else {
                    fileChip.visibility = View.GONE
                    fileChip.setOnClickListener(null)
                }

                // --- TEXT ---
                val hasText = !m.text.isNullOrBlank()
                txt.text = m.text ?: ""
                txt.visibility = if (hasText) View.VISIBLE else View.GONE

                // --- META ---
                meta.text = m.createdAt?.let {
                    DateFormat.getDateTimeInstance().format(Date(it))
                } ?: ""
            }
        }
    }
}
