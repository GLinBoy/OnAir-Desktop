package com.glinboy.onair

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage

fun trayImage(inUse: Boolean): BufferedImage {
    val size = 32
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val argb = if (inUse) 0xFFE53935.toInt() else 0xFF9E9E9E.toInt()
        graphics.color = Color(argb, true)
        graphics.fillOval(2, 2, size - 4, size - 4)
    } finally {
        graphics.dispose()
    }
    return image
}
