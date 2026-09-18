package com.example.qrcodegenerator

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

class MainActivity : AppCompatActivity() {

    // Stores the QR image currently generated in memory.
    private var generatedBitmap: Bitmap? = null

    // Stores the location of the QR image after it has been saved.
    private var savedImageUri: Uri? = null

    companion object {
        private const val QR_SIZE = 500
        private const val QR_FOLDER = "Pictures/QR Code Generator"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // Find the views from the XML layout.
        val inputEditText = findViewById<EditText>(R.id.inputEditText)
        val generateButton = findViewById<Button>(R.id.generateButton)
        val qrImageView = findViewById<ImageView>(R.id.qrImageView)
        val saveButton = findViewById<Button>(R.id.saveButton)
        val shareButton = findViewById<Button>(R.id.shareButton)

        // --------------------------------------------------
        // GENERATE QR CODE
        // --------------------------------------------------

        generateButton.setOnClickListener {

            val text = inputEditText.text.toString().trim()

            // Validate input.
            if (text.isEmpty()) {
                inputEditText.error = getString(R.string.empty_input_error)
                return@setOnClickListener
            }

            // Remove the error if the user has entered valid text.
            inputEditText.error = null

            try {

                // Convert the user's text into a QR matrix.
                val bitMatrix = MultiFormatWriter().encode(
                    text,
                    BarcodeFormat.QR_CODE,
                    QR_SIZE,
                    QR_SIZE
                )

                // Create an empty bitmap.
                val bitmap = Bitmap.createBitmap(
                    QR_SIZE,
                    QR_SIZE,
                    Bitmap.Config.RGB_565
                )

                // Convert the QR matrix into black/white pixels.
                for (x in 0 until QR_SIZE) {
                    for (y in 0 until QR_SIZE) {

                        val pixelColor = if (bitMatrix[x, y]) {
                            Color.BLACK
                        } else {
                            Color.WHITE
                        }

                        bitmap.setPixel(x, y, pixelColor)
                    }
                }

                // Store the generated bitmap.
                generatedBitmap = bitmap

                // A newly generated QR has not been saved yet.
                savedImageUri = null

                // Display the QR code.
                qrImageView.setImageBitmap(bitmap)
                qrImageView.visibility = ImageView.VISIBLE

                // The user can now save it.
                saveButton.isEnabled = true

                // But they must save this new QR before sharing it.
                shareButton.isEnabled = false

                Toast.makeText(
                    this,
                    getString(R.string.qr_generated),
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {

                // Prevent an unexpected QR-generation failure
                // from crashing the application.
                qrImageView.visibility = ImageView.GONE
                saveButton.isEnabled = false
                shareButton.isEnabled = false

                Toast.makeText(
                    this,
                    getString(R.string.qr_generation_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // --------------------------------------------------
        // SAVE QR CODE
        // --------------------------------------------------

        saveButton.setOnClickListener {

            val bitmap = generatedBitmap

            // Make sure a QR code actually exists.
            if (bitmap == null) {

                Toast.makeText(
                    this,
                    getString(R.string.generate_first),
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val imageUri = saveBitmapToGallery(bitmap)

            if (imageUri != null) {

                savedImageUri = imageUri

                // Sharing is now allowed because we have
                // a valid saved image URI.
                shareButton.isEnabled = true

                Toast.makeText(
                    this,
                    getString(R.string.qr_saved),
                    Toast.LENGTH_SHORT
                ).show()

            } else {

                shareButton.isEnabled = false

                Toast.makeText(
                    this,
                    getString(R.string.save_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // --------------------------------------------------
        // SHARE QR CODE
        // --------------------------------------------------

        shareButton.setOnClickListener {

            val imageUri = savedImageUri

            // We cannot share something that has not been saved.
            if (imageUri == null) {

                Toast.makeText(
                    this,
                    getString(R.string.save_before_share),
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            try {

                val shareIntent = Intent(Intent.ACTION_SEND).apply {

                    type = "image/png"

                    putExtra(
                        Intent.EXTRA_STREAM,
                        imageUri
                    )

                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

                val chooser = Intent.createChooser(
                    shareIntent,
                    getString(R.string.share_qr_code)
                )

                startActivity(chooser)

            } catch (e: Exception) {

                Toast.makeText(
                    this,
                    getString(R.string.share_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ------------------------------------------------------
    // SAVE BITMAP TO DEVICE GALLERY
    // ------------------------------------------------------

    private fun saveBitmapToGallery(bitmap: Bitmap): Uri? {

        val fileName = "QR_${System.currentTimeMillis()}.png"

        val contentValues = ContentValues().apply {

            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                fileName
            )

            put(
                MediaStore.Images.Media.MIME_TYPE,
                "image/png"
            )

            /*
             * Android 10 (API 29) and above:
             * tells Android where the image should be stored.
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    QR_FOLDER
                )

                /*
                 * Keep the file private while it is being written.
                 */
                put(
                    MediaStore.Images.Media.IS_PENDING,
                    1
                )
            }
        }

        /*
         * Android 10+ uses the primary external media volume.
         * Older Android versions use the older external URI.
         */
        val collection = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        /*
         * For Android versions below 10, provide a traditional
         * filesystem path.
         */
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {

            val picturesDirectory =
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                )

            val qrDirectory =
                java.io.File(
                    picturesDirectory,
                    "QR Code Generator"
                )

            if (!qrDirectory.exists()) {
                qrDirectory.mkdirs()
            }

            val file = java.io.File(
                qrDirectory,
                fileName
            )

            contentValues.put(
                MediaStore.Images.Media.DATA,
                file.absolutePath
            )
        }

        val resolver = contentResolver

        val uri = resolver.insert(
            collection,
            contentValues
        ) ?: return null

        try {

            /*
             * Open a stream to the new image.
             */
            resolver.openOutputStream(uri).use { outputStream ->

                if (outputStream == null) {
                    resolver.delete(uri, null, null)
                    return null
                }

                /*
                 * Convert our Bitmap into a PNG file.
                 *
                 * compress() returns false if the image could
                 * not be successfully compressed.
                 */
                val successful = bitmap.compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    outputStream
                )

                if (!successful) {
                    resolver.delete(uri, null, null)
                    return null
                }
            }

            /*
             * Android 10+:
             * mark the file as complete so other applications
             * can see/use it.
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                val completedValues = ContentValues().apply {
                    put(
                        MediaStore.Images.Media.IS_PENDING,
                        0
                    )
                }

                resolver.update(
                    uri,
                    completedValues,
                    null,
                    null
                )
            }

            return uri

        } catch (e: Exception) {

            /*
             * If anything goes wrong while writing,
             * remove the incomplete media entry.
             */
            resolver.delete(uri, null, null)

            return null
        }
    }
}