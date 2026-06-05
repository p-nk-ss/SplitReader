package com.example.splitreader.domain.model

/**
 * A file the user selected from Google Drive via the Picker. With the `drive.file` scope the app
 * only ever sees files the user explicitly picks, so this carries just the id; the real [name] and
 * [mimeType] are resolved from Drive metadata at download time when the picker doesn't supply them.
 */
data class DrivePickedFile(
    val fileId: String,
    val name: String = "",
    val mimeType: String? = null,
)
