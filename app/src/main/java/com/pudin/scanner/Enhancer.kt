package com.pudin.scanner

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Filter peningkat hasil scan.
 *
 * Inti "Warna Ajaib": memperkirakan warna latar kertas (termasuk bayangan dan
 * cahaya yang tidak rata), lalu setiap piksel dibagi dengan latarnya. Hasilnya
 * kertas menjadi putih rata, bayangan hilang, sedangkan tinta dan stempel tetap
 * berwarna. Setelah itu kontras dan saturasi dinaikkan.
 */
object Enhancer {

    enum class Mode(val key: String) {
        ORIGINAL("asli"), MAGIC("ajaib"), GRAY("abu"), BW("hitamputih");

        companion object {
            fun from(key: String?) = values().firstOrNull { it.key == key } ?: MAGIC
        }
    }

    // Tabel nada (0..255 -> 0..255) untuk tiap mode
    private val lutMagic = buildLut(black = 28, white = 232, gamma = 1.35)
    private val lutGray = buildLut(black = 40, white = 228, gamma = 1.5)
    private val lutBw = IntArray(256) { v ->
        // Ambang lembut supaya tepi huruf tetap halus
        val t = ((v - 125) / 85.0).coerceIn(0.0, 1.0)
        (255 * (t * t * (3 - 2 * t))).toInt()
    }

    private fun buildLut(black: Int, white: Int, gamma: Double) = IntArray(256) { v ->
        val t = ((v - black).toDouble() / (white - black)).coerceIn(0.0, 1.0)
        (255 * t.pow(gamma)).toInt().coerceIn(0, 255)
    }

    /** Mengolah bitmap. Bitmap sumber harus mutable; hasilnya ditulis ke bitmap yang sama. */
    fun apply(src: Bitmap, mode: Mode): Bitmap {
        if (mode == Mode.ORIGINAL) return src
        val bmp = if (src.isMutable && src.config == Bitmap.Config.ARGB_8888) src
        else src.copy(Bitmap.Config.ARGB_8888, true)

        val w = bmp.width
        val h = bmp.height
        val bg = estimateBackground(bmp)
        val row = IntArray(w)
        val bgRow = IntArray(w)

        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            bg.getPixels(bgRow, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val c = row[x]
                val b = bgRow[x]
                // Normalisasi terhadap latar; batas bawah 80 agar area gelap besar tidak meledak
                val r = min(255, ((c shr 16) and 0xFF) * 255 / max(80, (b shr 16) and 0xFF))
                val g = min(255, ((c shr 8) and 0xFF) * 255 / max(80, (b shr 8) and 0xFF))
                val bl = min(255, (c and 0xFF) * 255 / max(80, b and 0xFF))

                row[x] = when (mode) {
                    Mode.MAGIC -> magic(r, g, bl)
                    Mode.GRAY -> {
                        val l = lutGray[luma(r, g, bl)]
                        rgb(l, l, l)
                    }
                    else -> {
                        val l = lutBw[luma(r, g, bl)]
                        rgb(l, l, l)
                    }
                }
            }
            bmp.setPixels(row, 0, w, 0, y, w, 1)
        }
        bg.recycle()
        return bmp
    }

    private fun magic(r0: Int, g0: Int, b0: Int): Int {
        var r = lutMagic[r0].toFloat()
        var g = lutMagic[g0].toFloat()
        var b = lutMagic[b0].toFloat()
        // Saturasi naik ~45% agar tinta biru/merah dan stempel lebih hidup
        val l = 0.299f * r + 0.587f * g + 0.114f * b
        val s = 1.45f
        r = l + (r - l) * s
        g = l + (g - l) * s
        b = l + (b - l) * s
        return rgb(clamp(r), clamp(g), clamp(b))
    }

    private fun luma(r: Int, g: Int, b: Int) = (r * 77 + g * 150 + b * 29) shr 8

    private fun clamp(v: Float) = when {
        v < 0f -> 0
        v > 255f -> 255
        else -> v.toInt()
    }

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /**
     * Perkiraan warna latar: kecilkan gambar bertahap, ambil nilai paling terang di
     * sekitar tiap titik (menghapus tulisan), haluskan, lalu besarkan lagi ke ukuran asli.
     */
    private fun estimateBackground(src: Bitmap): Bitmap {
        var cur = src
        while (max(cur.width, cur.height) > 160) {
            val next = Bitmap.createScaledBitmap(
                cur, max(1, cur.width / 2), max(1, cur.height / 2), true
            )
            if (cur !== src) cur.recycle()
            cur = next
        }
        val sw = cur.width
        val sh = cur.height
        val px = IntArray(sw * sh)
        cur.getPixels(px, 0, sw, 0, 0, sw, sh)
        if (cur !== src) cur.recycle()

        val rad = max(2, max(sw, sh) / 45)
        val ch = Array(3) { k ->
            val shift = 16 - k * 8
            IntArray(px.size) { (px[it] shr shift) and 0xFF }
        }
        for (k in 0 until 3) {
            var a = ch[k]
            a = maxFilter(a, sw, sh, rad)
            a = boxBlur(a, sw, sh, rad * 2)
            a = boxBlur(a, sw, sh, rad * 2)
            ch[k] = a
        }
        val out = IntArray(px.size) { rgb(ch[0][it], ch[1][it], ch[2][it]) }
        val small = Bitmap.createBitmap(out, sw, sh, Bitmap.Config.ARGB_8888)
        val full = Bitmap.createScaledBitmap(small, src.width, src.height, true)
        if (full !== small) small.recycle()
        return full
    }

    private fun maxFilter(a: IntArray, w: Int, h: Int, r: Int): IntArray {
        val tmp = IntArray(a.size)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0
            for (dx in -r..r) {
                val xx = (x + dx).coerceIn(0, w - 1)
                val v = a[y * w + xx]
                if (v > m) m = v
            }
            tmp[y * w + x] = m
        }
        val out = IntArray(a.size)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0
            for (dy in -r..r) {
                val yy = (y + dy).coerceIn(0, h - 1)
                val v = tmp[yy * w + x]
                if (v > m) m = v
            }
            out[y * w + x] = m
        }
        return out
    }

    private fun boxBlur(a: IntArray, w: Int, h: Int, r: Int): IntArray {
        val tmp = IntArray(a.size)
        val n = 2 * r + 1
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0
            for (dx in -r..r) s += a[y * w + (x + dx).coerceIn(0, w - 1)]
            tmp[y * w + x] = s / n
        }
        val out = IntArray(a.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0
            for (dy in -r..r) s += tmp[(y + dy).coerceIn(0, h - 1) * w + x]
            out[y * w + x] = s / n
        }
        return out
    }
}
