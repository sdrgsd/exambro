package com.example.model

data class ExamSettings(
    val url: String = "https://forms.google.com",
    val isFullscreen: Boolean = true,
    val isKeepScreenOn: Boolean = true,
    val isLockBackPress: Boolean = true,
    val isAllowZoom: Boolean = true,
    val isDesktopMode: Boolean = false,
    val clearCacheOnStart: Boolean = false
)

data class PresetUrl(
    val title: String,
    val subtitle: String,
    val url: String,
    val category: String
)

data class SavedExamUrl(
    val id: String,
    val label: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

object ExamDefaults {
    val PRESETS = listOf(
        PresetUrl(
            title = "Google Forms",
            subtitle = "Kuis / Formulir Ujian Daring",
            url = "https://forms.google.com",
            category = "Google"
        ),
        PresetUrl(
            title = "Simulasi ANBK",
            subtitle = "Pusmendik Kemendikbudristek",
            url = "https://pusmendik.kemdikbud.go.id/anbk",
            category = "Pusat"
        ),
        PresetUrl(
            title = "Quizizz",
            subtitle = "Platform Kuis & Asesmen Siswa",
            url = "https://quizizz.com/join",
            category = "Kuis"
        ),
        PresetUrl(
            title = "Server CBT Lokal (IP 1)",
            subtitle = "Jaringan Lokal Lab Komputer",
            url = "http://192.168.1.100",
            category = "Lokal"
        ),
        PresetUrl(
            title = "Server CBT Lokal (IP 2)",
            subtitle = "Jaringan CBT Sekolah Subnet 0",
            url = "http://192.168.0.100",
            category = "Lokal"
        )
    )

    fun normalizeUrl(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return "https://forms.google.com"

        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }

        // If local IP address or localhost, default to http://
        if (trimmed.startsWith("192.168.") ||
            trimmed.startsWith("10.") ||
            trimmed.startsWith("172.") ||
            trimmed.startsWith("localhost") ||
            trimmed.startsWith("127.0.0.1")
        ) {
            return "http://$trimmed"
        }

        // Otherwise default to https://
        return "https://$trimmed"
    }
}
