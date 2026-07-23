package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.api.ColabApiClient
import com.example.data.db.AppDatabase
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class VideoRestoreWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_FILENAME = "filename"
        const val KEY_LOCAL_URI = "local_uri"
        const val KEY_BACKEND_URL = "backend_url"
        const val NOTIFICATION_ID = 9991
        const val CHANNEL_ID = "studio_restore_channel"
        const val CHANNEL_NAME = "Studio Media Restoration"
    }

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val filename = inputData.getString(KEY_FILENAME) ?: return Result.failure()
        val localUriStr = inputData.getString(KEY_LOCAL_URI) ?: ""
        var backendUrl = inputData.getString(KEY_BACKEND_URL) ?: ""

        if (backendUrl.isBlank()) {
            val db = AppDatabase.getDatabase(appContext)
            backendUrl = db.configDao().getConfig("backend_url")?.value ?: ""
        }

        if (backendUrl.isBlank()) {
            Log.e("VideoRestoreWorker", "Backend URL missing for restoration worker")
            return Result.failure()
        }

        createNotificationChannel()
        try {
            setForeground(getForegroundInfo(0))
        } catch (e: Exception) {
            Log.w("VideoRestoreWorker", "Failed to set foreground info: ${e.message}")
        }

        updateProgress(0, "Restoring $filename...")

        val mediaFile = resolveMediaFile(appContext, filename, localUriStr)
        if (mediaFile == null || !mediaFile.exists() || mediaFile.length() == 0L) {
            Log.e("VideoRestoreWorker", "Cannot find local media file for $filename at $localUriStr")
            updateNotification(100, "Restore failed: Local media file not found", false)
            return Result.failure()
        }

        try {
            updateProgress(25, "Uploading $filename to Colab...")
            updateNotification(25, "Restoring project media to Studio engine... [25%]", true)

            val service = ColabApiClient.getService(backendUrl)

            updateProgress(50, "Sending file stream...")
            updateNotification(50, "Restoring project media to Studio engine... [50%]", true)

            val requestFile = mediaFile.asRequestBody("video/*".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", filename, requestFile)
            val filenameBody = filename.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = service.uploadToLibrary(filePart, filenameBody)
            val responseString = response.string()

            updateProgress(90, "Finalizing restore...")
            updateNotification(90, "Restoring project media to Studio engine... [90%]", true)

            val db = AppDatabase.getDatabase(appContext)
            db.videoDao().updateVideoStatusByFilename(filename, "active")

            updateProgress(100, "Success")
            updateNotification(100, "Restoration complete! Media restored to Studio engine.", false)

            Log.d("VideoRestoreWorker", "Successfully restored $filename: $responseString")
            return Result.success(workDataOf("filename" to filename, "status" to "active"))
        } catch (e: Exception) {
            Log.e("VideoRestoreWorker", "Failed to restore $filename: ${e.message}")
            updateNotification(100, "Restore failed: ${e.localizedMessage}", false)
            return Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return getForegroundInfo(0)
    }

    private fun getForegroundInfo(progressPct: Int): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Studio Media Restoration")
            .setContentText("Restoring project media to Studio engine... [$progressPct%]")
            .setOngoing(true)
            .setProgress(100, progressPct, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun updateProgress(progressPct: Int, statusMsg: String) {
        setProgress(
            workDataOf(
                "progress" to progressPct,
                "status_msg" to statusMsg,
                "filename" to (inputData.getString(KEY_FILENAME) ?: "")
            )
        )
    }

    private fun updateNotification(progressPct: Int, text: String, isOngoing: Boolean) {
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(if (isOngoing) android.R.drawable.stat_sys_upload else android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Studio Media Restoration")
            .setContentText(text)
            .setOngoing(isOngoing)
            .setAutoCancel(!isOngoing)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (isOngoing) {
            builder.setProgress(100, progressPct, false)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress when restoring missing video assets to Colab backend"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun resolveMediaFile(context: Context, filename: String, localUriStr: String): File? {
        if (localUriStr.isNotBlank()) {
            if (localUriStr.startsWith("content://")) {
                try {
                    val uri = android.net.Uri.parse(localUriStr)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val tempFile = File(context.cacheDir, "restore_$filename")
                        tempFile.outputStream().use { output ->
                            inputStream.copyTo(output)
                        }
                        return tempFile
                    }
                } catch (e: Exception) {
                    Log.w("VideoRestoreWorker", "Failed to read content URI $localUriStr: ${e.message}")
                }
            } else {
                val directFile = File(localUriStr)
                if (directFile.exists()) return directFile
            }
        }

        val cacheFile = File(context.cacheDir, filename)
        if (cacheFile.exists()) return cacheFile

        val altCacheFile = File(context.cacheDir, "restore_$filename")
        if (altCacheFile.exists()) return altCacheFile

        val files = context.cacheDir.listFiles()
        files?.firstOrNull { it.name == filename || it.name.endsWith(filename) }?.let {
            return it
        }

        return null
    }
}
