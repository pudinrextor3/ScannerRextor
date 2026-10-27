package com.pudin.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onClick: (ScanItem) -> Unit
) : ListAdapter<ScanItem, HistoryAdapter.VH>(DIFF) {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.ivThumb)
        val name: TextView = v.findViewById(R.id.tvName)
        val meta: TextView = v.findViewById(R.id.tvMeta)
        val badgePdf: TextView = v.findViewById(R.id.badgePdf)
        val badgePng: TextView = v.findViewById(R.id.badgePng)
        val more: ImageView = v.findViewById(R.id.ivMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = getItem(position)
        h.name.text = item.name
        h.meta.text = "${formatDate(item.time)}  •  ${item.pages} hlm"
        h.badgePdf.visibility = if (item.pdfUri != null) View.VISIBLE else View.GONE
        h.badgePng.visibility = if (item.pngUris.isNotEmpty()) View.VISIBLE else View.GONE
        val bmp = loadThumb(item.thumb)
        if (bmp != null) h.thumb.setImageBitmap(bmp) else h.thumb.setImageResource(R.drawable.ic_pdf)
        h.itemView.setOnClickListener { onClick(item) }
        h.more.setOnClickListener { onClick(item) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ScanItem>() {
            override fun areItemsTheSame(a: ScanItem, b: ScanItem) = a.id == b.id
            override fun areContentsTheSame(a: ScanItem, b: ScanItem) = a == b
        }

        private val cache = LruCache<String, Bitmap>(40)

        fun loadThumb(path: String?): Bitmap? {
            if (path == null) return null
            cache.get(path)?.let { return it }
            if (!File(path).exists()) return null
            val bmp = BitmapFactory.decodeFile(path) ?: return null
            cache.put(path, bmp)
            return bmp
        }

        fun forget(path: String?) {
            if (path != null) cache.remove(path)
        }

        fun formatDate(time: Long): String =
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(time))
    }
}
