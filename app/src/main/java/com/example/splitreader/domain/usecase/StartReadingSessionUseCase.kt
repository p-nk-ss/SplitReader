package com.example.splitreader.domain.usecase

import javax.inject.Inject

class StartReadingSessionUseCase @Inject constructor() {
    operator fun invoke(): Long = System.currentTimeMillis()
}
