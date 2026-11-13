package com.example.splitreader.domain.repository

import android.net.Uri
import com.example.splitreader.domain.model.DrivePickedFile

/**
 * Downloads books the user picked from Google Drive. The authorize + pick step lives in
 * the Drive auth client (it needs an Activity + ActivityResultLauncher); this repository owns only
 * the side-effect-free download so it slots into the existing
 * [com.example.splitreader.domain.usecase.ParseBookUseCase] pipeline.
 */
interface DriveRepository {
    /**
     * Streams the picked Drive file into app-private storage and returns a `file://` [Uri] ready for
     * the parser — mirroring `CatalogRepository.downloadEpub`. [accessToken] is the short-lived
     * `drive.file` token from the Authorization flow.
     */
    suspend fun downloadFile(file: DrivePickedFile, accessToken: String): Uri

    /**
     * The `file://` [Uri] of [file] if it was already downloaded (the target path is deterministic:
     * `drive/<fileId>[.ext]`), else null. Lets callers recognise an owned book before downloading.
     * A download still in flight (`<fileId>.part`) is not a book. Reads the folder off the main thread.
     */
    suspend fun localUriFor(file: DrivePickedFile): Uri?
}
