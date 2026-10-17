package com.clkyigit.toraksb.geometry

import kotlin.math.PI

/** Android'de java.io.Serializable (ekran döndürmede durum saklama); iOS'ta boş işaretleyici. */
expect interface JvmSerializable

fun toDeg(rad: Double): Double = rad * 180.0 / PI
fun toRad(deg: Double): Double = deg * PI / 180.0
