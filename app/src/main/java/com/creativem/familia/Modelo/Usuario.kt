package com.creativem.familia.Modelo

// 1. Modelo Usuario.kt
data class Usuario(
    val uid: String = "",
    val nombre: String = "",
    val correo: String = "",
    val cuotas: Map<String, Int> = emptyMap()
)


// 2. Modelo Gasto.kt
data class Gasto(
    val valor: Int = 0,
    val detalle: String = "",
    val fecha: String = ""
)


