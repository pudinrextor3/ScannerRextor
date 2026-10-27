package com.pudin.scanner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Satu dokumen hasil scan yang tercatat di riwayat. */
data class ScanItem(
    val id: String,
    val name: String,
    val time: Long,
    val pages: Int,
    val pdfUri: String?,
    val pngUris: List<String>,
    val thumb: String?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("time", time)
        put("pages", pages)
        put("pdf", pdfUri ?: "")
        put("png", JSONArray(pngUris))
        put("thumb", thumb ?: "")
    }

    companion object {
        fun fromJson(o: JSONObject): ScanItem {
            val arr = o.optJSONArray("png") ?: JSONArray()
            val pngs = (0 until arr.length()).map { arr.getString(it) }
            return ScanItem(
                id = o.getString("id"),
                name = o.optString("name", "Dokumen"),
                time = o.optLong("time", 0L),
                pages = o.optInt("pages", 0),
                pdfUri = o.optString("pdf").ifEmpty { null },
                pngUris = pngs,
                thumb = o.optString("thumb").ifEmpty { null }
            )
        }
    }
}

/** Menyimpan riwayat scan dalam file JSON di penyimpanan internal aplikasi. */
class HistoryStore(context: Context) {
    private val file = File(context.filesDir, "history.json")
    val thumbDir: File = File(context.filesDir, "thumbs").apply { mkdirs() }

    fun load(): MutableList<ScanItem> {
        if (!file.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length())
                .map { ScanItem.fromJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.time }
                .toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(items: List<ScanItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString())
    }
}
