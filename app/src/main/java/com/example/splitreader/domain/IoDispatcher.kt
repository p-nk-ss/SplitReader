package com.example.splitreader.domain

import javax.inject.Qualifier

/** Qualifies the [kotlinx.coroutines.CoroutineDispatcher] used for IO-bound work (injectable so tests can substitute a virtual-time dispatcher). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
