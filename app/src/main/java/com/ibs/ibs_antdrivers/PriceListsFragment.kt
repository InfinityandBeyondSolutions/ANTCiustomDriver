package com.ibs.ibs_antdrivers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.ibs.ibs_antdrivers.data.PriceListsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PriceListsFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var emptyText: TextView

    private val repo = PriceListsRepository()
    private lateinit var adapter: PriceListsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_price_lists, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler = view.findViewById(R.id.priceListsRecycler)
        progress = view.findViewById(R.id.priceListsProgress)
        emptyText = view.findViewById(R.id.priceListsEmpty)

        adapter = PriceListsAdapter(
            onItemClick = { pl ->
                val bundle = Bundle().apply {
                    putString(PriceListDetailFragment.ARG_PRICE_LIST_ID, pl.id)
                }
                findNavController().navigate(R.id.priceListDetailFragment, bundle)
            }
        )

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        load()
    }

    private fun load() {
        progress.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { repo.getAllPriceLists() }
                progress.visibility = View.GONE

                if (data.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                    adapter.submitList(emptyList())
                } else {
                    emptyText.visibility = View.GONE
                    adapter.submitList(data)
                }
            } catch (t: Throwable) {
                progress.visibility = View.GONE
                emptyText.visibility = View.VISIBLE
                emptyText.text = getString(R.string.price_lists_failed)
                Snackbar.make(requireView(), t.message ?: "Failed to load price lists", Snackbar.LENGTH_LONG).show()
            }
        }
    }
}
