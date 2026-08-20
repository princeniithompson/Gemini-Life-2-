package com.example.favorites

import java.util.UUID

data class SavedPrayer(
    val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val memoryVerse: String,
    val transcript: String
)
