package com.example.util

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TotpManager {
    // 16-character valid Base32 secret for Google Authenticator (Characters: A-Z, 2-7)
    const val SETUP_KEY = "EXAMBROWSERKEY23"

    /**
     * Decodes standard RFC 4648 Base32 string into ByteArray.
     */
    fun base32Decode(base32: String): ByteArray {
        val clean = base32.trim().replace(" ", "").replace("-", "").uppercase()
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bitsLeft = 0
        val bytes = ArrayList<Byte>()

        for (c in clean) {
            val valIndex = alphabet.indexOf(c)
            if (valIndex < 0) continue
            buffer = (buffer shl 5) or (valIndex and 0x1f)
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bytes.add(((buffer shr (bitsLeft - 8)) and 0xff).toByte())
                bitsLeft -= 8
            }
        }
        return bytes.toByteArray()
    }

    /**
     * Generates standard 6-digit RFC 6238 TOTP for a given 30-second time step.
     */
    fun generateTotp(secretKey: String = SETUP_KEY, timeStep: Long = System.currentTimeMillis() / 1000L / 30L): String {
        val keyBytes = base32Decode(secretKey)
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA1"))

        val data = ByteBuffer.allocate(8).putLong(timeStep).array()
        val hash = mac.doFinal(data)

        val offset = (hash.last().toInt() and 0x0f)
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)

        val otp = binary % 1_000_000
        return otp.toString().padStart(6, '0')
    }

    /**
     * Validates if user input matches the current Google Authenticator TOTP code,
     * allowing a +/- 1 step (30s) tolerance window for clock skew.
     */
    fun isCodeValid(inputCode: String): Boolean {
        val clean = inputCode.trim().replace(" ", "").replace("-", "")

        val currentStep = System.currentTimeMillis() / 1000L / 30L
        for (step in listOf(currentStep, currentStep - 1, currentStep + 1)) {
            val expectedCode = generateTotp(SETUP_KEY, step)
            if (clean == expectedCode || clean.equals("EXIT_$expectedCode", ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
