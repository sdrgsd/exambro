package com.example

import com.example.model.ExamDefaults
import com.example.util.QrCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun normalizeUrl_empty_returnsDefaultGoogleForms() {
        val result = ExamDefaults.normalizeUrl("")
        assertEquals("https://forms.google.com", result)
    }

    @Test
    fun normalizeUrl_ipAddress_prependsHttp() {
        val result = ExamDefaults.normalizeUrl("192.168.1.100")
        assertEquals("http://192.168.1.100", result)
    }

    @Test
    fun normalizeUrl_localhost_prependsHttp() {
        val result = ExamDefaults.normalizeUrl("localhost:8080/cbt")
        assertEquals("http://localhost:8080/cbt", result)
    }

    @Test
    fun normalizeUrl_domain_prependsHttps() {
        val result = ExamDefaults.normalizeUrl("pusmendik.kemdikbud.go.id/anbk")
        assertEquals("https://pusmendik.kemdikbud.go.id/anbk", result)
    }

    @Test
    fun normalizeUrl_existingProtocol_preserved() {
        val result = ExamDefaults.normalizeUrl("http://cbt.sekolah.sch.id")
        assertEquals("http://cbt.sekolah.sch.id", result)
    }

    @Test
    fun normalizeUrl_fileProtocol_preserved() {
        val result = ExamDefaults.normalizeUrl("file:///data/user/0/com.example/cache/exam_page.html")
        assertEquals("file:///data/user/0/com.example/cache/exam_page.html", result)
    }

    @Test
    fun qrCodeGenerator_generatesDynamicToken() {
        val token = QrCodeGenerator.getCurrentExitToken()
        assertTrue(token.startsWith("EXIT_"))
        assertEquals(13, token.length)
        assertTrue(QrCodeGenerator.isTokenValid(token))
    }

    @Test
    fun qrCodeGenerator_rejectsInvalidTokensAndPin1234() {
        assertTrue(!QrCodeGenerator.isTokenValid("1234"))
        assertTrue(!QrCodeGenerator.isTokenValid("wrong_token"))
    }

    @Test
    fun qrCodeGenerator_generatesAndValidatesSixDigitCode() {
        val code = QrCodeGenerator.getSixDigitCode()
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
        assertTrue(QrCodeGenerator.isTokenValid(code))
        assertTrue(QrCodeGenerator.isTokenValid("${code.take(3)} ${code.takeLast(3)}"))
    }

    @Test
    fun totpManager_generatesAndValidatesCode() {
        val code = com.example.util.TotpManager.generateTotp()
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
        assertTrue(com.example.util.TotpManager.isCodeValid(code))
        assertTrue(com.example.util.TotpManager.isCodeValid("${code.take(3)} ${code.takeLast(3)}"))
        assertTrue(!com.example.util.TotpManager.isCodeValid("1234"))
        assertTrue(!com.example.util.TotpManager.isCodeValid("00000000"))
    }
}
