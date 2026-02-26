package com.ibs.ibs_antdrivers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.ibs.ibs_antdrivers.data.PriceList
import java.io.File

/**
 * Generates a PDF for a [PriceList] whose layout exactly mirrors the on-screen table:
 *
 * Columns (matching row_pricelist_item.xml & fragment_pricelist_detail.xml):
 *  Item No | Description | Brand | Size | Unit Barcode | Outer Barcode | Unit Price | Case Price | ID
 *
 * Colours (matching colors.xml):
 *  webnavy  = #0A1F44   webgold  = #D4AF37   grey = #667788
 *  LIGHT row = #F5F7FB  WHITE row = #FFFFFF
 */
object PriceListPdfGenerator {

    // ── A4 landscape at 72 dpi ─────────────────────────────────────────────
    private const val PAGE_WIDTH  = 842   // pts
    private const val PAGE_HEIGHT = 595
    private const val MARGIN      = 20f

    // ── Exact dp widths from row_pricelist_item.xml (1 dp ≈ 2.0 pt at 72 dpi on mdpi) ──
    // We scale the dp values so the total fits within the page width minus margins.
    // Layout dp totals: 96+200+90+90+150+160+90+90+80 = 1046 dp
    // Available page width: 842 - 2*20 = 802 pt → scale ≈ 802/1046 ≈ 0.766
    private const val SCALE = 0.766f

    private data class Col(
        val label: String,
        val widthDp: Float,
        val align: Paint.Align = Paint.Align.LEFT,
    ) {
        val widthPt: Float get() = widthDp * SCALE
    }

    private val COLUMNS = listOf(
        Col("Item No",       96f),
        Col("Description",  200f),
        Col("Brand",         90f),
        Col("Size",          90f),
        Col("Unit Barcode", 150f),
        Col("Outer Barcode",160f),
        Col("Unit Price",    90f, Paint.Align.RIGHT),
        Col("Case Price",    90f, Paint.Align.RIGHT),
        Col("ID",            80f, Paint.Align.RIGHT),
    )

    // ── Colours ────────────────────────────────────────────────────────────
    private val C_NAVY   = Color.rgb(0x0A, 0x1F, 0x44)   // webnavy
    private val C_GOLD   = Color.rgb(0xD4, 0xAF, 0x37)   // webgold
    private val C_GREY   = Color.rgb(0x66, 0x77, 0x88)
    private val C_WHITE  = Color.WHITE
    private val C_LIGHT  = Color.rgb(0xF5, 0xF7, 0xFB)   // bubble
    private val C_FOOTER = Color.rgb(0x88, 0x99, 0xAA)

    // ── Row heights ────────────────────────────────────────────────────────
    private const val HDR_BANNER_H = 72f   // top header card
    private const val COL_HDR_H   = 24f   // column header row
    private const val ROW_H       = 22f   // data row
    private const val FOOTER_H    = 16f

    fun generate(context: Context, pl: PriceList): File {
        val doc  = PdfDocument()
        val items = pl.items

        val tableWidth = COLUMNS.sumOf { it.widthPt.toDouble() }.toFloat()

        // Rows that fit per page
        val usableFirst  = PAGE_HEIGHT - MARGIN - HDR_BANNER_H - 4f - COL_HDR_H - FOOTER_H - MARGIN
        val usableOther  = PAGE_HEIGHT - MARGIN - COL_HDR_H - FOOTER_H - MARGIN
        val rowsFirst    = (usableFirst  / ROW_H).toInt().coerceAtLeast(1)
        val rowsOther    = (usableOther  / ROW_H).toInt().coerceAtLeast(1)

        // ── Paints ──────────────────────────────────────────────────────────
        val bgPaint = Paint().apply { style = Paint.Style.FILL }

        fun textPaint(
            size: Float,
            color: Int,
            bold: Boolean = false,
            align: Paint.Align = Paint.Align.LEFT,
        ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = align
        }

        val pTitle    = textPaint(14f, C_WHITE, bold = true)
        val pSubtitle = textPaint( 9f, Color.argb(0xCC, 0xFF, 0xFF, 0xFF))
        val pColHdr   = textPaint( 9f, C_GOLD,  bold = true)
        val pColHdrR  = textPaint( 9f, C_GOLD,  bold = true, align = Paint.Align.RIGHT)

        // Per-column data paints (matching the XML textColors)
        //  colItemNo   – navy bold
        //  colDesc     – navy
        //  colBrand    – grey
        //  colSize     – grey
        //  colUnitBarcode  – navy
        //  colOuterBarcode – navy
        //  colUnitPrice – navy bold right
        //  colCasePrice – navy bold right
        //  colId        – grey right
        val dataPaints = listOf(
            textPaint(8f, C_NAVY, bold = true),                              // Item No
            textPaint(8f, C_NAVY),                                           // Description
            textPaint(8f, C_GREY),                                           // Brand
            textPaint(8f, C_GREY),                                           // Size
            textPaint(8f, C_NAVY),                                           // Unit Barcode
            textPaint(8f, C_NAVY),                                           // Outer Barcode
            textPaint(8f, C_NAVY, bold = true, align = Paint.Align.RIGHT),   // Unit Price
            textPaint(8f, C_NAVY, bold = true, align = Paint.Align.RIGHT),   // Case Price
            textPaint(8f, C_GREY, align = Paint.Align.RIGHT),                // ID
        )

        val pFooter = textPaint(7f, C_FOOTER)
        val linePaint = Paint().apply {
            color = Color.rgb(0xDD, 0xDD, 0xDD)
            strokeWidth = 0.5f
        }
        val divPaint = Paint().apply {
            color = Color.rgb(0xDD, 0xDD, 0xDD)
            strokeWidth = 0.5f
        }

        var itemIndex = 0
        var pageNumber = 1

        while (true) {
            val isFirst  = pageNumber == 1
            val maxRows  = if (isFirst) rowsFirst else rowsOther

            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page     = doc.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            var y = MARGIN

            // ── Header banner (first page only) ──────────────────────────────
            if (isFirst) {
                // Navy card background
                bgPaint.color = C_NAVY
                canvas.drawRoundRect(
                    MARGIN, y, MARGIN + tableWidth, y + HDR_BANNER_H,
                    12f, 12f, bgPaint
                )

                // Gold accent line at bottom of banner
                bgPaint.color = C_GOLD
                canvas.drawRect(MARGIN, y + HDR_BANNER_H - 3f, MARGIN + tableWidth, y + HDR_BANNER_H, bgPaint)

                // Gold circle icon placeholder (left)
                bgPaint.color = C_GOLD
                canvas.drawCircle(MARGIN + 28f, y + HDR_BANNER_H / 2f, 18f, bgPaint)

                // Title & subtitle
                val title = pl.title.ifBlank { pl.name }.ifBlank { "Price List" }
                canvas.drawText(title, MARGIN + 58f, y + HDR_BANNER_H / 2f - 2f, pTitle)

                val subtitleParts = buildList {
                    if (pl.companyName.isNotBlank())   add(pl.companyName)
                    if (pl.effectiveDate.isNotBlank())  add("Effective: ${pl.effectiveDate}")
                    if (pl.includeVat)                  add("Incl. VAT")
                    add("${items.size} items")
                }
                canvas.drawText(
                    subtitleParts.joinToString("  •  "),
                    MARGIN + 58f,
                    y + HDR_BANNER_H / 2f + 14f,
                    pSubtitle
                )

                y += HDR_BANNER_H + 4f
            }

            // ── Column header row ─────────────────────────────────────────────
            bgPaint.color = C_NAVY
            canvas.drawRect(MARGIN, y, MARGIN + tableWidth, y + COL_HDR_H, bgPaint)

            var cx = MARGIN
            for (col in COLUMNS) {
                val textY = y + COL_HDR_H - 7f
                when (col.align) {
                    Paint.Align.RIGHT -> canvas.drawText(col.label, cx + col.widthPt - 4f, textY, pColHdrR)
                    else              -> canvas.drawText(col.label, cx + 4f,              textY, pColHdr)
                }
                // Vertical divider between columns (subtle)
                if (col !== COLUMNS.last()) {
                    canvas.drawLine(cx + col.widthPt, y + 3f, cx + col.widthPt, y + COL_HDR_H - 3f, divPaint)
                }
                cx += col.widthPt
            }
            y += COL_HDR_H

            // ── Data rows ─────────────────────────────────────────────────────
            var rowCount = 0
            while (rowCount < maxRows && itemIndex < items.size) {
                val item = items[itemIndex]

                // Alternating bg (white / light — matching XML defaults)
                bgPaint.color = if (rowCount % 2 == 0) C_WHITE else C_LIGHT
                canvas.drawRect(MARGIN, y, MARGIN + tableWidth, y + ROW_H, bgPaint)

                // Bottom divider
                canvas.drawLine(MARGIN, y + ROW_H, MARGIN + tableWidth, y + ROW_H, linePaint)

                val values = listOf(
                    item.itemNo.ifBlank { item.id },
                    item.description,
                    item.brand,
                    item.size,
                    item.unitBarcode,
                    item.outerBarcode,
                    item.unitPrice,
                    item.casePrice,
                    item.id,
                )

                cx = MARGIN
                val textY = y + ROW_H - 7f
                for (i in COLUMNS.indices) {
                    val col  = COLUMNS[i]
                    val raw  = values.getOrElse(i) { "" }
                    val paint = dataPaints[i]

                    when (col.align) {
                        Paint.Align.RIGHT -> {
                            val clipped = clipText(raw, col.widthPt - 4f, paint)
                            canvas.drawText(clipped, cx + col.widthPt - 4f, textY, paint)
                        }
                        else -> {
                            val clipped = clipText(raw, col.widthPt - 6f, paint)
                            canvas.drawText(clipped, cx + 4f, textY, paint)
                        }
                    }
                    cx += col.widthPt
                }

                y += ROW_H
                rowCount++
                itemIndex++
            }

            // ── Footer ────────────────────────────────────────────────────────
            canvas.drawText(
                "Page $pageNumber  •  Generated by ANT Drivers",
                MARGIN,
                PAGE_HEIGHT - 4f,
                pFooter
            )

            doc.finishPage(page)
            pageNumber++

            if (itemIndex >= items.size) break
        }

        // ── Write to cache ────────────────────────────────────────────────────
        val safeTitle = (pl.title.ifBlank { pl.name }.ifBlank { "pricelist" })
            .replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            .take(40)
        val file = File(context.filesDir, "pricelist_${safeTitle}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** Truncate [text] with "…" so it fits within [maxWidth] points. */
    private fun clipText(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return if (end <= 0) "" else text.substring(0, end) + "…"
    }
}

