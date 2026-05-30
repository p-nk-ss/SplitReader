package com.example.splitreader.domain.usecase

import javax.inject.Inject

/** Marks the start of a reading session, returning the start timestamp in epoch millis. */
class StartReadingSessionUseCase @Inject constructor() {
    operator fun invoke(): Long = System.currentTimeMillis()
}
