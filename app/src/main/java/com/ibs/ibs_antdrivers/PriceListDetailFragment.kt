package com.ibs.ibs_antdrivers

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.ibs.ibs_antdrivers.data.PriceList
import com.ibs.ibs_antdrivers.data.PriceListsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PriceListDetailFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var emptyText: TextView

    private lateinit var detailTitle: TextView
    private lateinit var detailSubtitle: TextView

    private val repo = PriceListsRepository()
    private lateinit var adapter: PriceListItemsAdapter

    private var priceListId: String? = null
    private var loaded: PriceList? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        priceListId = arguments?.getString(ARG_PRICE_LIST_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_pricelist_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler = view.findViewById(R.id.priceListItemsRecycler)
        progress = view.findViewById(R.id.priceListItemsProgress)
        emptyText = view.findViewById(R.id.priceListItemsEmpty)

        detailTitle = view.findViewById(R.id.detailTitle)
        detailSubtitle = view.findViewById(R.id.detailSubtitle)

        adapter = PriceListItemsAdapter()
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        setupMenu()
        load()
    }

    private fun setupMenu() {
        val host: MenuHost = requireActivity()
        host.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_pricelist_detail, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_share_pricelist -> {
                        shareLoaded()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun load() {
        val id = priceListId
        if (id.isNullOrBlank()) {
            emptyText.visibility = View.VISIBLE
            emptyText.text = "Missing price list id"
            return
        }

        progress.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { repo.getAllPriceLists() }
                val pl = list.firstOrNull { it.id == id }

                progress.visibility = View.GONE

                if (pl == null) {
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = "Price list not found"
                    return@launch
                }

                loaded = pl

                val title = pl.title.ifBlank { pl.name }.ifBlank { "Price List" }
                requireActivity().title = title
                detailTitle.text = title

                val subtitleParts = buildList {
                    if (pl.companyName.isNotBlank()) add(pl.companyName)
                    if (pl.effectiveDate.isNotBlank()) add("Effective: ${pl.effectiveDate}")
                    if (pl.includeVat) add("VAT included")
                }
                detailSubtitle.text = subtitleParts.joinToString(" • ").ifBlank { "Tap share to send" }

                if (pl.items.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = "No items"
                    adapter.submitList(emptyList())
                } else {
                    emptyText.visibility = View.GONE
                    adapter.submitList(pl.items)
                }
            } catch (t: Throwable) {
                progress.visibility = View.GONE
                emptyText.visibility = View.VISIBLE
                emptyText.text = getString(R.string.price_lists_failed)
                Snackbar.make(requireView(), t.message ?: "Failed to load price list", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun shareLoaded() {
        val pl = loaded
        if (pl == null) {
            Snackbar.make(requireView(), "Nothing to share", Snackbar.LENGTH_SHORT).show()
            return
        }

        // Simple CSV for sharing (opens email/WhatsApp/etc).
        val csv = buildString {
            appendLine("ItemNo,Description,Size,Brand,UnitBarcode,OuterBarcode,UnitPrice,CasePrice")
            for (it in pl.items) {
                fun esc(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""
                append(esc(it.itemNo))
                append(',')
                append(esc(it.description))
                append(',')
                append(esc(it.size))
                append(',')
                append(esc(it.brand))
                append(',')
                append(esc(it.unitBarcode))
                append(',')
                append(esc(it.outerBarcode))
                append(',')
                append(esc(it.unitPrice))
                append(',')
                appendLine(esc(it.casePrice))
            }
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, pl.title.ifBlank { pl.name }.ifBlank { "Price List" })
            putExtra(Intent.EXTRA_TEXT, csv)
        }
        startActivity(Intent.createChooser(intent, "Share price list"))
    }

    companion object {
        const val ARG_PRICE_LIST_ID = "priceListId"
    }
}
