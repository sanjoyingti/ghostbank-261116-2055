package com.arin.ghostbank.model

import kotlinx.serialization.Serializable

/**
 * A frozen, per-account browser identity. Burned after the account's single paisa.
 * Two accounts never share one.
 */
@Serializable
data class GhostProfile(
    val id: String,
    val userAgent: String,
    val clientHints: String,
    val headerOrder: List<String>,
    val cipherOrderSeed: Int,
    val h2WindowUpdate: Int,
    val locale: String,
    val timeZone: String,
    val exitCountry: String,
    val acceptEncoding: String,
    val burned: Boolean = false
)
