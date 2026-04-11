package com.axion.tempus.data

data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val updatedAt: Long = System.currentTimeMillis()
)
