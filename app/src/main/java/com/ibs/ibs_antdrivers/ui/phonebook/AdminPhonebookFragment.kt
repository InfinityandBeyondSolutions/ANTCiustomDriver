package com.ibs.ibs_antdrivers.ui.phonebook

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.ibs.ibs_antdrivers.HomeFragment
import com.ibs.ibs_antdrivers.R
import com.ibs.ibs_antdrivers.data.AdminContact
import com.ibs.ibs_antdrivers.data.AdminsRepository

class AdminPhonebookFragment : Fragment() {

    private val repo = AdminsRepository()

    private lateinit var progress: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var list: RecyclerView
    private lateinit var adapter: AdminContactsAdapter
    private lateinit var searchEdit: TextInputEditText
    private lateinit var alphaIndex: AlphabetIndexView
    private lateinit var btnBack: ImageView

    private var allAdmins: List<AdminContact> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_admin_phonebook, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        progress = view.findViewById(R.id.progress)
        emptyText = view.findViewById(R.id.emptyText)
        list = view.findViewById(R.id.adminList)
        searchEdit = view.findViewById(R.id.searchEdit)
        alphaIndex = view.findViewById(R.id.alphaIndex)
        btnBack = view.findViewById(R.id.btnBackSettings)

        adapter = AdminContactsAdapter(requireContext())
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Sticky header
        list.addItemDecoration(StickyHeaderItemDecoration(list, adapter))

        // Side index scroll
        alphaIndex.setOnLetterSelected { ch ->
            adapter.findPositionForSection(ch)?.let { pos ->
                (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0)
            }
        }

        progress.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        repo.startListening(
            onUpdate = { data ->
                progress.visibility = View.GONE
                allAdmins = data
                applyFilter(searchEdit.text?.toString().orEmpty())
            },
            onError = {
                progress.visibility = View.GONE
                emptyText.visibility = View.VISIBLE
                emptyText.text = "Failed to load admins."
                adapter.submitContacts(emptyList())
                alphaIndex.setSections(emptyList())
            }
        )

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s?.toString().orEmpty())
            }
        })
    }

    private fun applyFilter(queryRaw: String) {
        val q = queryRaw.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            allAdmins
        } else {
            allAdmins.filter { a ->
                val name = a.username.orEmpty().lowercase()
                val email = a.email.orEmpty().lowercase()
                val phone = a.phoneNumber.orEmpty().lowercase()
                name.contains(q) || email.contains(q) || phone.contains(q)
            }
        }

        if (filtered.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            emptyText.text = if (q.isEmpty()) "No admins yet." else "No admins match \"$q\"."
        } else {
            emptyText.visibility = View.GONE
        }

        adapter.submitContacts(filtered)
        alphaIndex.setSections(adapter.getAvailableSections())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        repo.stopListening()
    }
}
