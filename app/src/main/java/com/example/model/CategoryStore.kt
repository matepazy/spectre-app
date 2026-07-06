package com.matepazy.spectre.model

import com.matepazy.spectre.support.InferenceResult

data class CategoryStore(
    val isScanning: Boolean = false,
    val selectedCategory: SignalCategory = SignalCategory.PASSIVE,
    val signals: List<FingerprintSignal> = emptyList(),
    val inferenceResult: InferenceResult? = null,
    val permissionStatus: Map<String, Boolean> = emptyMap()
)
