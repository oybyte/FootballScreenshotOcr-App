package com.fifa.ocr.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.StatFs
import android.os.storage.StorageManager
import com.fifa.ocr.core.contract.CaptureImageLimits
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

data class TempAsset(val assetId: String, val file: File)

data class ValidatedAsset(
    val assetId: String,
    val file: File,
    val sha256: String,
    val fileSize: Long,
    val width: Int,
    val height: Int,
)

data class FileScanResult(val pending: List<File>, val committed: List<File>)

data class StorageHealth(
    val availableBytes: Long,
    val systemReserveBytes: Long,
) {
    fun hasCapacity(requiredBytes: Long): Boolean {
        require(requiredBytes >= 0) { "requiredBytes must not be negative" }
        val reserve = systemReserveBytes.coerceAtLeast(0L)
        return availableBytes >= reserve && requiredBytes <= availableBytes - reserve
    }
}

class StorageLowException(message: String) : IOException(message)

interface CaptureFileStore {
    suspend fun storageHealth(): StorageHealth
    suspend fun writeBitmapTemp(assetId: String, bitmap: Bitmap): TempAsset
    suspend fun copyUriTemp(assetId: String, uri: Uri): TempAsset
    suspend fun validate(tempAsset: TempAsset): ValidatedAsset
    suspend fun commit(tempAsset: TempAsset, relativePath: String)
    suspend fun scan(): FileScanResult
    suspend fun discardSuccessfulDuplicate(tempAsset: TempAsset)
}

class AndroidCaptureFileStore(private val context: Context) : CaptureFileStore {
    private val root: File = File(context.filesDir, "captures")
    private val pendingDir: File = File(root, "pending")
    private val committedDir: File = File(root, "committed")

    override suspend fun storageHealth(): StorageHealth {
        ensureDirectories()
        val stat = StatFs(root.absolutePath)
        val storageManager = context.getSystemService(StorageManager::class.java)
        val allocatableBytes = storageManager?.let {
            it.getAllocatableBytes(it.getUuidForPath(root))
        } ?: stat.availableBytes
        val systemReserveBytes = (stat.availableBytes - allocatableBytes).coerceAtLeast(0L)
        return StorageHealth(stat.availableBytes, systemReserveBytes)
    }

    override suspend fun writeBitmapTemp(assetId: String, bitmap: Bitmap): TempAsset {
        require(!bitmap.isRecycled) { "Cannot persist a recycled bitmap" }
        ensureDirectories()
        val file = pendingFile(assetId)
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Bitmap PNG encoding failed" }
            output.flush()
            output.channel.force(true)
        }
        return TempAsset(assetId, file)
    }

    override suspend fun copyUriTemp(assetId: String, uri: Uri): TempAsset {
        ensureDirectories()
        val file = pendingFile(assetId)
        val resolver = context.contentResolver
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
                output.flush()
                output.channel.force(true)
            }
        } ?: throw IOException("Unable to read image URI")
        return TempAsset(assetId, file)
    }

    override suspend fun validate(tempAsset: TempAsset): ValidatedAsset {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        FileInputStream(tempAsset.file).use { input -> BitmapFactory.decodeStream(input, null, options) }
        val width = options.outWidth
        val height = options.outHeight
        require(width > 0 && height > 0) { "Image dimensions are invalid" }
        require(width <= CaptureImageLimits.MAX_SOURCE_EDGE && height <= CaptureImageLimits.MAX_SOURCE_EDGE) { "Image edge exceeds source limit" }
        require(width.toLong() * height <= CaptureImageLimits.MAX_SOURCE_PIXELS) { "Image pixels exceed source limit" }
        val sample = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(width, height)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        FileInputStream(tempAsset.file).use { input ->
            require(BitmapFactory.decodeStream(input, null, sample) != null) { "Image decode failed" }
        }
        return ValidatedAsset(
            assetId = tempAsset.assetId,
            file = tempAsset.file,
            sha256 = sha256(tempAsset.file),
            fileSize = tempAsset.file.length(),
            width = width,
            height = height,
        )
    }

    override suspend fun commit(tempAsset: TempAsset, relativePath: String) {
        ensureDirectories()
        val destination = File(root, relativePath)
        require(destination.toPath().normalize().startsWith(committedDir.toPath().normalize())) { "Invalid committed path" }
        destination.parentFile?.mkdirs()
        if (destination.exists()) throw IOException("Committed asset already exists: ${destination.name}")
        if (!tempAsset.file.renameTo(destination)) throw IOException("Atomic asset rename failed")
    }

    override suspend fun scan(): FileScanResult {
        ensureDirectories()
        val pending = pendingDir.listFiles()?.filter { it.isFile }.orEmpty()
        val committed = committedDir.walkTopDown().filter { it.isFile }.toList()
        return FileScanResult(pending, committed)
    }

    override suspend fun discardSuccessfulDuplicate(tempAsset: TempAsset) {
        if (tempAsset.file.exists() && !tempAsset.file.delete()) {
            throw IOException("Unable to remove duplicate staging file")
        }
    }

    private fun pendingFile(assetId: String) = File(pendingDir, "$assetId.tmp")

    private fun ensureDirectories() {
        check(pendingDir.mkdirs() || pendingDir.isDirectory)
        check(committedDir.mkdirs() || committedDir.isDirectory)
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > CaptureImageLimits.MAX_EDGE || height / sample > CaptureImageLimits.MAX_EDGE ||
            width.toLong() / sample * (height.toLong() / sample) > CaptureImageLimits.MAX_PIXELS
        ) sample *= 2
        return sample
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
