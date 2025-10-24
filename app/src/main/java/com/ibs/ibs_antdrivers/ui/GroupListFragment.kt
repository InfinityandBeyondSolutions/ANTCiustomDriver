package com.ibs.ibs_antdrivers.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ibs.ibs_antdrivers.HomeFragment
import com.ibs.ibs_antdrivers.MainActivity
import com.ibs.ibs_antdrivers.R
import com.ibs.ibs_antdrivers.data.FirebaseRepo
import com.ibs.ibs_antdrivers.data.Group
import kotlinx.coroutines.launch

class GroupListFragment : Fragment() {

    private val repo = FirebaseRepo()

    private lateinit var list: RecyclerView
    private lateinit var spin: ProgressBar
    private val items = mutableListOf<Group>()
    private lateinit var adapter: GroupAdapter
    private lateinit var btnBack: ImageView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_group_list, container, false).also {
            list = it.findViewById(R.id.recycler)
            spin = it.findViewById(R.id.progress)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = GroupAdapter(items) { g ->
            // ✅ pass BOTH id and name so ChatHome can show the name immediately
            (activity as? MainActivity)?.openChat(g.id, g.name)
        }

        btnBack = view.findViewById(R.id.btnBackSettings)

        btnBack.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, HomeFragment())
                .addToBackStack(null)
                .commit()

        }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                spin.visibility = View.VISIBLE
                val groups = repo.myGroups()
                items.clear()
                items.addAll(groups)
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load groups: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                spin.visibility = View.GONE
            }
        }
    }
}

/* --- tiny adapter/vh matching your row_group.xml --- */
private class GroupAdapter(
    private val data: List<Group>,
    private val onClick: (Group) -> Unit
) : RecyclerView.Adapter<GroupVH>() {
    override fun onCreateViewHolder(p: ViewGroup, vt: Int): GroupVH {
        val v = LayoutInflater.from(p.context).inflate(R.layout.row_group, p, false)
        return GroupVH(v, onClick)
    }
    override fun getItemCount() = data.size
    override fun onBindViewHolder(h: GroupVH, i: Int) = h.bind(data[i])
}

private class GroupVH(
    v: View,
    private val onClick: (Group) -> Unit
) : RecyclerView.ViewHolder(v) {
    private val title: android.widget.TextView = v.findViewById(R.id.title)
    fun bind(g: Group) {
        title.text = g.name ?: g.id
        itemView.setOnClickListener { onClick(g) }
    }
}
