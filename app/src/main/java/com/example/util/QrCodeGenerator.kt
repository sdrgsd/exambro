package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.security.MessageDigest

object QrCodeGenerator {
    /**
     * Generates a 6-digit dynamic numeric code (like Google Authenticator TOTP)
     * derived from the 30-second timeStep.
     */
    fun getSixDigitCode(timeStep: Long = System.currentTimeMillis() / 30000L): String {
        val raw = "EXAM_BROWSER_EXIT_$timeStep"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(raw.toByteArray())
        val offset = (digest.last().toInt() and 0x0f)
        val binary = ((digest[offset].toInt() and 0x7f) shl 24) or
                ((digest[offset + 1].toInt() and 0xff) shl 16) or
                ((digest[offset + 2].toInt() and 0xff) shl 8) or
                (digest[offset + 3].toInt() and 0xff)
        return (binary % 1_000_000).toString().padStart(6, '0')
    }

    /**
     * Generates a dynamic exit token that changes every 30 seconds.
     */
    fun getCurrentExitToken(): String {
        val timeStep = System.currentTimeMillis() / 30000L
        val raw = "EXAM_BROWSER_EXIT_$timeStep"
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        return "EXIT_${hash.take(8).uppercase()}"
    }

    /**
     * Generates an ImageBitmap QR Code from given content string.
     */
    fun generateQrBitmap(content: String, size: Int = 512): ImageBitmap? {
        return try {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Validates if a token matches the current or recent (last 30s) dynamic exit code, 6-digit numeric code, or master PIN.
     */
    fun isTokenValid(token: String): Boolean {
        val trimmed = token.trim().replace(" ", "").replace("-", "")
        if (TotpManager.isCodeValid(trimmed)) return true

        val currentStep = System.currentTimeMillis() / 30000L
        for (step in listOf(currentStep, currentStep - 1, currentStep + 1)) {
            // Validasi jika memasukkan 6 digit angka numerik
            val sixDigit = getSixDigitCode(step)
            if (trimmed == sixDigit || trimmed.equals("EXIT_$sixDigit", ignoreCase = true)) {
                return true
            }

            // Validasi jika memindai QR token
            val raw = "EXAM_BROWSER_EXIT_$step"
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
            val fullToken = "EXIT_${hash.take(8).uppercase()}"
            val shortToken = hash.take(8).uppercase()
            if (trimmed.equals(fullToken, ignoreCase = true) || trimmed.equals(shortToken, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
