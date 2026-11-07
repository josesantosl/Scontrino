package com.pepesantos.scontrino.data.model

/** One item bought in a store in the past, used to prefill and compare new entries. */
data class PastPurchase(
    val productName: String,
    val price: Double,
    val weight: Double?,
    val date: Long,
)
