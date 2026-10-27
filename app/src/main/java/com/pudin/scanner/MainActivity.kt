package com.pudin.scanner

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var etName: TextInputEditText
    private lateinit var cbPdf: MaterialCheckBox
    private lateinit var cbPng: MaterialCheckBox
    private lateinit var swGallery: MaterialSwitch
    private lateinit var btnScan: MaterialButton
    private lateinit var progress: ProgressBar
    private lateinit var cardResult: MaterialCardView
    private lateinit var ivPreview: ImageView
    private lateinit var tvResult: TextView
    private lateinit var btnOpenPdf: MaterialButton
    private lateinit var btnSharePdf: MaterialButton
    private lateinit var btnSharePng: MaterialButton

    private lateinit var scanLauncher: ActivityResultLauncher<IntentSenderRequest>
    private val io = Executors.newSingleThreadExecutor()

    private var lastPdf: Uri? = null
    private var lastPngs: ArrayList<Uri> = arrayListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etName = findViewById(R.id.etName)
        cbPdf = findViewById(R.id.cbPdf)
        cbPng = findViewById(R.id.cbPng)
        swGallery = findViewById(R.id.swGallery)
        btnScan = findViewById(R.id.btnScan)
        progress = findViewById(R.id.progress)
        cardResult = findViewById(R.id.cardResult)
        ivPreview = findViewById(R.id.ivPreview)
        tvResult = findViewById(R.id.tvResult)
        btnOpenPdf = findViewById(R.id.btnOpenPdf)
        btnSharePdf = findViewById(R.id.btnSharePdf)
        btnSharePng = findViewById(R.id.btnSharePng)

        scanLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { res ->
            if (res.resultCode == RESULT_OK) {
                val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
                if (result != null) saveResult(result)
            }
        }

        btnScan.setOnClickListener { startScan() }
        btnOpenPdf.setOnClickListener { lastPdf?.let { openPdf(it) } }
        btnSharePdf.setOnClickListener { lastPdf?.let { share(listOf(it), "application/pdf") } }
        btnSharePng.setOnClickListener { if (lastPngs.isNotEmpty()) share(lastPngs, "image/png") }
    }

    private fun startScan() {
        if (!cbPdf.isChecked && !cbPng.isChecked) {
            toast("Pilih minimal satu format: PDF atau PNG")
            return
        }
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
        val wantPdf = cbPdf.isChecked
        val wantPng = cbPng.isChecked
        val base = buildBaseName()
        setBusy(true)

        io.execute {
            try {
                var pdfUri: Uri? = null
                val pngUris = arrayListOf<Uri>()
                var firstPage: Bitmap? = null

                if (wantPdf) {
                    result.pdf?.uri?.let { src ->
                        pdfUri = copyToMediaStore(
                            src, "$base.pdf", "application/pdf",
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            Environment.DIRECTORY_DOWNLOADS + "/Scanner"
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
                        savePng(bmp, name)?.let { pngUris.add(it) }
                    }
                    if (i == 0) firstPage = scaleForPreview(bmp)
                    bmp.recycle()
                }

                runOnUiThread {
                    setBusy(false)
                    lastPdf = pdfUri
                    lastPngs = pngUris
                    showResult(base, pages.size, pdfUri, pngUris.size, firstPage)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setBusy(false)
                    toast("Gagal menyimpan: ${e.message}")
                }
            }
        }
    }

    private fun buildBaseName(): String {
        val typed = etName.text?.toString()?.trim().orEmpty()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return if (typed.isEmpty()) "Scan_$stamp" else "${typed}_$stamp"
    }

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
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Scanner")
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

    private fun scaleForPreview(bmp: Bitmap): Bitmap {
        val maxSide = 900
        val ratio = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        if (ratio >= 1f) return bmp.copy(Bitmap.Config.ARGB_8888, false)
        return Bitmap.createScaledBitmap(
            bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true
        )
    }

    private fun showResult(base: String, pages: Int, pdf: Uri?, pngCount: Int, preview: Bitmap?) {
        cardResult.visibility = View.VISIBLE
        preview?.let { ivPreview.setImageBitmap(it) }
        val lines = mutableListOf("$pages halaman")
        if (pdf != null) lines.add("PDF: $base.pdf (Download/Scanner)")
        if (pngCount > 0) lines.add("PNG: $pngCount file (Pictures/Scanner)")
        tvResult.text = lines.joinToString("\n")
        btnOpenPdf.isEnabled = pdf != null
        btnSharePdf.isEnabled = pdf != null
        btnSharePng.isEnabled = pngCount > 0
        toast("Tersimpan")
    }

    private fun openPdf(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast("Tidak ada aplikasi pembuka PDF")
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

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        btnScan.isEnabled = !busy
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        super.onDestroy()
        io.shutdown()
    }
}
