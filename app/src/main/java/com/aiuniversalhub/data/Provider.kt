package com.aiuniversalhub.data

data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val isOpenAiCompatible: Boolean = true
)