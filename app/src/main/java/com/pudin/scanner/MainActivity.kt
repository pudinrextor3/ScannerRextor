package com.pudin.scanner

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    // ---- View ----
    private lateinit var root: CoordinatorLayout
    private lateinit var pageHome: NestedScrollView
    private lateinit var pageHistory: LinearLayout
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var tvGreeting: TextView
    private lateinit var tvStatDocs: TextView
    private lateinit var tvStatPages: TextView
    private lateinit var pulse: View
    private lateinit var btnScan: FrameLayout
    private lateinit var scanContent: View
    private lateinit var progress: CircularProgressIndicator
    private lateinit var tvScanHint: TextView
    private lateinit var etName: TextInputEditText
    private lateinit var chipPdf: Chip
    private lateinit var chipPng: Chip
    private lateinit var swGallery: MaterialSwitch
    private lateinit var rvRecent: RecyclerView
    private lateinit var tvRecentEmpty: TextView
    private lateinit var rvHistory: RecyclerView
    private lateinit var emptyHistory: View
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptySub: TextView
    private lateinit var tvHistoryCount: TextView
    private lateinit var etSearch: TextInputEditText

    // ---- Data ----
    private lateinit var store: HistoryStore
    private lateinit var items: MutableList<ScanItem>
    private val recentAdapter = HistoryAdapter { showSheet(it, false) }
    private val historyAdapter = HistoryAdapter { showSheet(it, false) }
    private var query = ""
    private var shownDocs = 0
    private var shownPages = 0

    private lateinit var scanLauncher: ActivityResultLauncher<IntentSenderRequest>
    private val io = Executors.newSingleThreadExecutor()
    private var pulseAnim: AnimatorSet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()

        store = HistoryStore(this)
        items = store.load()

        rvRecent.layoutManager = LinearLayoutManager(this)
        rvRecent.adapter = recentAdapter
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter
        attachSwipeToDelete()

        scanLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { res ->
            if (res.resultCode == RESULT_OK) {
                val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
                if (result != null) saveResult(result)
            }
        }

        setupScanButton()
        findViewById<MaterialButton>(R.id.btnSeeAll).setOnClickListener {
            bottomNav.selectedItemId = R.id.nav_history
        }
        findViewById<MaterialButton>(R.id.btnEmptyScan).setOnClickListener {
            if (query.isNotEmpty()) etSearch.setText("") else startScan()
        }
        etSearch.doAfterTextChanged {
            query = it?.toString()?.trim().orEmpty()
            refresh()
        }

        bottomNav.setOnItemSelectedListener { item ->
            showPage(item.itemId == R.id.nav_history)
            true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pageHistory.visibility == View.VISIBLE) {
                    bottomNav.selectedItemId = R.id.nav_home
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        tvGreeting.text = greeting()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        startPulse()
    }

    override fun onPause() {
        super.onPause()
        pulseAnim?.cancel()
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        pageHome = findViewById(R.id.pageHome)
        pageHistory = findViewById(R.id.pageHistory)
        bottomNav = findViewById(R.id.bottomNav)
        tvGreeting = findViewById(R.id.tvGreeting)
        tvStatDocs = findViewById(R.id.tvStatDocs)
        tvStatPages = findViewById(R.id.tvStatPages)
        pulse = findViewById(R.id.pulse)
        btnScan = findViewById(R.id.btnScan)
        scanContent = findViewById(R.id.scanContent)
        progress = findViewById(R.id.progress)
        tvScanHint = findViewById(R.id.tvScanHint)
        etName = findViewById(R.id.etName)
        chipPdf = findViewById(R.id.chipPdf)
        chipPng = findViewById(R.id.chipPng)
        swGallery = findViewById(R.id.swGallery)
        rvRecent = findViewById(R.id.rvRecent)
        tvRecentEmpty = findViewById(R.id.tvRecentEmpty)
        rvHistory = findViewById(R.id.rvHistory)
        emptyHistory = findViewById(R.id.emptyHistory)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvEmptySub = findViewById(R.id.tvEmptySub)
        tvHistoryCount = findViewById(R.id.tvHistoryCount)
        etSearch = findViewById(R.id.etSearch)
    }

    // ================= Tampilan =================

    private fun greeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val waktu = when {
            h < 11 -> "pagi"
            h < 15 -> "siang"
            h < 18 -> "sore"
            else -> "malam"
        }
        return "Selamat $waktu 👋"
    }

    private fun showPage(history: Boolean) {
        val show: View = if (history) pageHistory else pageHome
        val hide: View = if (history) pageHome else pageHistory
        if (show.visibility == View.VISIBLE) return
        hide.visibility = View.GONE
        show.alpha = 0f
        show.translationY = 40f
        show.visibility = View.VISIBLE
        show.animate().alpha(1f).translationY(0f).setDuration(260)
            .setInterpolator(AccelerateDecelerateInterpolator()).start()
        if (history) rvHistory.scheduleLayoutAnimation() else rvRecent.scheduleLayoutAnimation()
    }

    private fun refresh() {
        recentAdapter.submitList(items.take(3))
        tvRecentEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        val filtered = if (query.isEmpty()) items.toList()
        else items.filter { it.name.contains(query, ignoreCase = true) }
        historyAdapter.submitList(filtered)
        tvHistoryCount.text = "${items.size} dokumen"

        val empty = filtered.isEmpty()
        emptyHistory.visibility = if (empty) View.VISIBLE else View.GONE
        rvHistory.visibility = if (empty) View.GONE else View.VISIBLE
        if (query.isNotEmpty()) {
            tvEmptyTitle.text = "Tidak ditemukan"
            tvEmptySub.text = "Tidak ada dokumen dengan nama \"$query\""
        } else {
            tvEmptyTitle.text = "Belum ada dokumen"
            tvEmptySub.text = "Hasil scan akan muncul di sini"
        }
        findViewById<MaterialButton>(R.id.btnEmptyScan).text =
            if (query.isNotEmpty()) "Hapus pencarian" else "Scan sekarang"

        animateCount(tvStatDocs, shownDocs, items.size)
        val pages = items.sumOf { it.pages }
        animateCount(tvStatPages, shownPages, pages)
        shownDocs = items.size
        shownPages = pages
    }

    private fun animateCount(tv: TextView, from: Int, to: Int) {
        if (from == to) {
            tv.text = to.toString(); return
        }
        ValueAnimator.ofInt(from, to).apply {
            duration = 700
            addUpdateListener { tv.text = (it.animatedValue as Int).toString() }
            start()
        }
    }

    private fun startPulse() {
        pulseAnim?.cancel()
        val sx = ObjectAnimator.ofFloat(pulse, View.SCALE_X, 1f, 1.28f)
        val sy = ObjectAnimator.ofFloat(pulse, View.SCALE_Y, 1f, 1.28f)
        val a = ObjectAnimator.ofFloat(pulse, View.ALPHA, 0.40f, 0f)
        listOf(sx, sy, a).forEach {
            it.repeatCount = ValueAnimator.INFINITE
            it.duration = 1600
        }
        pulseAnim = AnimatorSet().apply { playTogether(sx, sy, a); start() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupScanButton() {
        btnScan.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(110).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(320)
                        .setInterpolator(OvershootInterpolator(3f)).start()
            }
            false
        }
        btnScan.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startScan()
        }
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        scanContent.visibility = if (busy) View.INVISIBLE else View.VISIBLE
        btnScan.isEnabled = !busy
        tvScanHint.text = if (busy) "Menyimpan dokumen..." else "Ketuk untuk mulai memindai"
    }

    // ================= Scan & simpan =================

    private fun startScan() {
        if (!chipPdf.isChecked && !chipPng.isChecked) {
            toast("Pilih minimal satu format: PDF atau PNG")
            chipPdf.isChecked = true
            return
        }
        if (pageHistory.visibility == View.VISIBLE) bottomNav.selectedItemId = R.id.nav_home

        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(swGallery.isChecked)
            .setPageLimit(50)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { sender ->
                scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { e ->
                toast("Pemindai belum siap: ${e.message}. Pastikan Google Play Services terbaru dan ada internet saat pertama kali.")
            }
    }

    private fun saveResult(result: GmsDocumentScanningResult) {
        val wantPdf = chipPdf.isChecked
        val wantPng = chipPng.isChecked
        val typed = etName.text?.toString()?.trim().orEmpty()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val label = typed.ifEmpty { "Scan $stamp" }
        val base = safeName(if (typed.isEmpty()) "Scan_$stamp" else "${typed}_$stamp")
        val id = UUID.randomUUID().toString()
        setBusy(true)

        io.execute {
            try {
                var pdfUri: Uri? = null
                val pngUris = arrayListOf<String>()
                var thumbPath: String? = null

                if (wantPdf) {
                    result.pdf?.uri?.let { src ->
                        pdfUri = copyToMediaStore(
                            src, "$base.pdf", "application/pdf",
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            Environment.DIRECTORY_DOWNLOADS + "/Rextor Scanner"
                        )
                    }
                }

                val pages = result.pages.orEmpty()
                pages.forEachIndexed { i, page ->
                    val bmp = contentResolver.openInputStream(page.imageUri)?.use {
                        BitmapFactory.decodeStream(it)
                    } ?: return@forEachIndexed
                    if (wantPng) {
                        val name = if (pages.size == 1) "$base.png"
                        else "${base}_hal%02d.png".format(i + 1)
                        savePng(bmp, name)?.let { pngUris.add(it.toString()) }
                    }
                    if (i == 0) thumbPath = saveThumb(bmp, id)
                    bmp.recycle()
                }

                val item = ScanItem(
                    id = id,
                    name = label,
                    time = System.currentTimeMillis(),
                    pages = pages.size,
                    pdfUri = pdfUri?.toString(),
                    pngUris = pngUris,
                    thumb = thumbPath
                )

                runOnUiThread {
                    setBusy(false)
                    etName.setText("")
                    items.add(0, item)
                    persist()
                    refresh()
                    rvRecent.scheduleLayoutAnimation()
                    btnScan.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    showSheet(item, true)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setBusy(false)
                    toast("Gagal menyimpan: ${e.message}")
                }
            }
        }
    }

    private fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private fun copyToMediaStore(
        src: Uri, name: String, mime: String, collection: Uri, relPath: String
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val dest = contentResolver.insert(collection, values) ?: return null
        contentResolver.openInputStream(src)?.use { input ->
            contentResolver.openOutputStream(dest)?.use { out -> input.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        contentResolver.update(dest, values, null, null)
        return dest
    }

    private fun savePng(bmp: Bitmap, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Rextor Scanner")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val dest = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ) ?: return null
        contentResolver.openOutputStream(dest)?.use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        contentResolver.update(dest, values, null, null)
        return dest
    }

    private fun saveThumb(bmp: Bitmap, id: String): String? {
        return try {
            val maxSide = 420
            val ratio = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
            val small = if (ratio >= 1f) bmp else Bitmap.createScaledBitmap(
                bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true
            )
            val f = File(store.thumbDir, "$id.jpg")
            FileOutputStream(f).use { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            if (small !== bmp) small.recycle()
            f.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun persist() {
        val snapshot = items.toList()
        io.execute { store.save(snapshot) }
    }

    // ================= Lembar aksi =================

    private fun showSheet(item: ScanItem, isNew: Boolean) {
        val dialog = BottomSheetDialog(this)
        val v = LayoutInflater.from(this).inflate(R.layout.sheet_actions, null)
        dialog.setContentView(v)

        val thumb = HistoryAdapter.loadThumb(item.thumb)
        val iv = v.findViewById<ImageView>(R.id.ivSheetThumb)
        if (thumb != null) iv.setImageBitmap(thumb) else iv.setImageResource(R.drawable.ic_pdf)
        v.findViewById<TextView>(R.id.tvSheetTag).visibility = if (isNew) View.VISIBLE else View.GONE
        v.findViewById<TextView>(R.id.tvSheetTitle).text = item.name
        val formats = listOfNotNull(
            if (item.pdfUri != null) "PDF" else null,
            if (item.pngUris.isNotEmpty()) "${item.pngUris.size} PNG" else null
        ).joinToString(" + ")
        v.findViewById<TextView>(R.id.tvSheetMeta).text =
            "${HistoryAdapter.formatDate(item.time)}\n${item.pages} halaman  •  $formats"

        val hasPdf = item.pdfUri != null
        val hasPng = item.pngUris.isNotEmpty()

        fun row(id: Int, visible: Boolean, action: () -> Unit) {
            val r = v.findViewById<View>(id)
            r.visibility = if (visible) View.VISIBLE else View.GONE
            r.setOnClickListener { dialog.dismiss(); action() }
        }
        row(R.id.actOpenPdf, hasPdf) { openFile(Uri.parse(item.pdfUri!!), "application/pdf") }
        row(R.id.actSharePdf, hasPdf) { share(listOf(Uri.parse(item.pdfUri!!)), "application/pdf") }
        row(R.id.actViewImage, hasPng) { openFile(Uri.parse(item.pngUris.first()), "image/png") }
        row(R.id.actSharePng, hasPng) { share(item.pngUris.map { Uri.parse(it) }, "image/png") }
        row(R.id.actRename, true) { renameDialog(item) }
        row(R.id.actDelete, true) { confirmDelete(item) }

        dialog.show()
    }

    private fun renameDialog(item: ScanItem) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_rename, null)
        val et = v.findViewById<TextInputEditText>(R.id.etRename)
        et.setText(item.name)
        et.setSelection(item.name.length)
        MaterialAlertDialogBuilder(this)
            .setTitle("Ganti nama")
            .setView(v)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = et.text?.toString()?.trim().orEmpty()
                if (newName.isEmpty() || newName == item.name) return@setPositiveButton
                val idx = items.indexOfFirst { it.id == item.id }
                if (idx < 0) return@setPositiveButton
                items[idx] = item.copy(name = newName)
                persist()
                refresh()
                // Ikut ganti nama file PDF di folder Download (kalau masih bisa)
                item.pdfUri?.let { uri ->
                    io.execute {
                        try {
                            val cv = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName(newName) + ".pdf")
                            }
                            contentResolver.update(Uri.parse(uri), cv, null, null)
                        } catch (e: Exception) { }
                    }
                }
                toast("Nama diganti")
            }
            .show()
    }

    private fun confirmDelete(item: ScanItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus dokumen?")
            .setMessage("\"${item.name}\" beserta file PDF dan PNG-nya akan dihapus dari HP.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                items.removeAll { it.id == item.id }
                persist()
                refresh()
                deleteFiles(item)
                Snackbar.make(root, "Dokumen dihapus", Snackbar.LENGTH_SHORT)
                    .setAnchorView(bottomNav).show()
            }
            .show()
    }

    private fun deleteFiles(item: ScanItem) {
        io.execute {
            val uris = listOfNotNull(item.pdfUri) + item.pngUris
            uris.forEach { u ->
                try { contentResolver.delete(Uri.parse(u), null, null) } catch (e: Exception) { }
            }
            item.thumb?.let { HistoryAdapter.forget(it); File(it).delete() }
        }
    }

    // ================= Geser untuk hapus =================

    private fun attachSwipeToDelete() {
        val red = ContextCompat.getColor(this, R.color.ferrari)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = red }
        val icon = ContextCompat.getDrawable(this, R.drawable.ic_delete)?.mutate()
        icon?.setTint(Color.WHITE)
        val radius = dp(18f)
        val iconSize = dp(26f).toInt()
        val iconMargin = dp(24f).toInt()

        val cb = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val item = historyAdapter.currentList.getOrNull(vh.bindingAdapterPosition) ?: return
                val index = items.indexOfFirst { it.id == item.id }
                if (index < 0) return
                items.removeAt(index)
                persist()
                refresh()
                rvHistory.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                Snackbar.make(root, "\"${item.name}\" dihapus", Snackbar.LENGTH_LONG)
                    .setAnchorView(bottomNav)
                    .setActionTextColor(ContextCompat.getColor(this@MainActivity, R.color.ferrari_yellow))
                    .setAction("BATAL") {
                        items.add(index.coerceAtMost(items.size), item)
                        persist()
                        refresh()
                    }
                    .addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(sb: Snackbar?, event: Int) {
                            if (event != BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_ACTION) {
                                deleteFiles(item)
                            }
                        }
                    })
                    .show()
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isActive: Boolean
            ) {
                val v = vh.itemView
                if (dX != 0f) {
                    val rect = RectF(
                        v.left.toFloat(), v.top.toFloat(), v.right.toFloat(), v.bottom.toFloat()
                    )
                    paint.alpha = (minOf(1f, Math.abs(dX) / (v.width * 0.35f)) * 255).toInt()
                    c.drawRoundRect(rect, radius, radius, paint)
                    icon?.let {
                        val top = v.top + (v.height - iconSize) / 2
                        val left = if (dX > 0) v.left + iconMargin
                        else v.right - iconMargin - iconSize
                        it.setBounds(left, top, left + iconSize, top + iconSize)
                        it.draw(c)
                    }
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isActive)
            }
        }
        ItemTouchHelper(cb).attachToRecyclerView(rvHistory)
    }

    // ================= Utilitas =================

    private fun openFile(uri: Uri, mime: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast("File tidak bisa dibuka. Mungkin sudah dihapus dari HP.")
        }
    }

    private fun share(uris: List<Uri>, mime: String) {
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Kirim lewat"))
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
