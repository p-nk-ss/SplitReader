package com.example.splitreader.domain.translator

/**
 * Thrown when the on-device ML Kit translation model could not be prepared within the timeout
 * (e.g. the model download stalls because Google Play services / the network is unavailable).
 * Surfaced to the user as a recoverable error with a Retry action rather than an endless spinner.
 */
class ModelDownloadException : Exception("Translation model download timed out")
