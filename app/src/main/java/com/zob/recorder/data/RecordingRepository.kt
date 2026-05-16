package com.zob.recorder.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.zob.recorder.model.RecordingSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver = context.contentResolver

    fun getRecordings(): Flow<List<RecordingSummary>> = flow {
        val recordings = mutableListOf<RecordingSummary>()
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.RESOLUTION,
            MediaStore.Video.Media.MIME_TYPE
        )

        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Video.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("%Zob%", "%zob%")

        contentResolver.query(
            collectionUri, projection, selection, selectionArgs,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)) ?: ""
                val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE))
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED))
                val resolution = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RESOLUTION)) ?: ""

                recordings.add(
                    RecordingSummary(
                        id = id.toString(),
                        fileName = name,
                        filePath = ContentUris.withAppendedId(collectionUri, id).toString(),
                        durationMs = duration,
                        fileSize = size,
                        dateCreated = date * 1000,
                        resolution = resolution,
                        codec = "" // Would need MediaMetadataRetriever for full codec info
                    )
                )
            }
        }
        emit(recordings)
    }.flowOn(Dispatchers.IO)

    suspend fun deleteRecording(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun getRecordingFileUri(fileName: String): Uri? {
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(MediaStore.Video.Media._ID)
        val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(fileName)

        contentResolver.query(collectionUri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                return ContentUris.withAppendedId(collectionUri, id)
            }
        }
        return null
    }
}
