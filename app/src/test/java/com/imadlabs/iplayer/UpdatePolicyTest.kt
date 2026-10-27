package com.imadlabs.iplayer

import com.imadlabs.iplayer.update.ManifestParser
import com.imadlabs.iplayer.update.ManifestResult
import com.imadlabs.iplayer.update.UpdateDecision
import com.imadlabs.iplayer.update.UpdateManifest
import com.imadlabs.iplayer.update.UpdatePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private val trusted = setOf("github.com", "githubusercontent.com")
    private val sha = "a".repeat(64)

    private fun manifest(
        code: Long = 5, name: String = "1.2.0", url: String = "https://github.com/ImadKhan1999/iPlayer/releases/download/v1.2.0/app.apk",
        hash: String = sha, size: Long = 50_000_000, minSupported: Long = 0, mandatory: Boolean = false, schema: Int = 1,
    ) = """
        {"schema": $schema, "channel": "stable", "latest": {
          "versionName": "$name", "versionCode": $code, "releaseDate": "2026-10-01",
          "changelog": ["Faster startup"], "minSupportedVersionCode": $minSupported, "mandatory": $mandatory,
          "releasePage": "https://github.com/ImadKhan1999/iPlayer/releases/tag/v1.2.0",
          "apks": [{"abi": "universal", "url": "$url", "sha256": "$hash", "sizeBytes": $size}]
        }}
    """.trimIndent()

    private fun valid(text: String) = (ManifestParser.parse(text, trusted) as ManifestResult.Valid).manifest

    @Test fun acceptsValidManifest() {
        assertEquals(5L, valid(manifest()).latest.versionCode)
    }

    @Test fun rejectsInvalidFields() {
        fun invalid(t: String) = assertTrue(ManifestParser.parse(t, trusted) is ManifestResult.Invalid)
        invalid("not json")
        invalid(manifest(schema = 2))
        invalid(manifest(code = 0))
        invalid(manifest(name = "one point two"))
        invalid(manifest(url = "http://github.com/x.apk"))
        invalid(manifest(url = "https://evil.example.com/x.apk"))
        invalid(manifest(url = "https://user:pw@github.com/x.apk"))
        invalid(manifest(url = "https://github.com.evil.example/x.apk"))
        invalid(manifest(hash = "abc"))
        invalid(manifest(size = 0))
        invalid(manifest(size = ManifestParser.MAX_APK_BYTES + 1))
        invalid(manifest(minSupported = 99))
    }

    @Test fun subdomainOfTrustedHostIsTrusted() {
        assertTrue(ManifestParser.isTrustedHttps("https://objects.githubusercontent.com/a", trusted))
        assertFalse(ManifestParser.isTrustedHttps("https://notgithub.com/a", trusted))
    }

    @Test fun neverOffersSameOrOlderVersion() {
        val m = valid(manifest(code = 5))
        assertEquals(UpdateDecision.UpToDate, UpdatePolicy.decide(5, m, 0))
        assertEquals(UpdateDecision.UpToDate, UpdatePolicy.decide(9, m, 0))
        assertTrue(UpdatePolicy.decide(4, m, 0) is UpdateDecision.Available)
    }

    @Test fun mandatoryWhenBelowMinimumSupported() {
        val m = valid(manifest(code = 5, minSupported = 3))
        assertTrue((UpdatePolicy.decide(2, m, 0) as UpdateDecision.Available).mandatory)
        assertFalse((UpdatePolicy.decide(3, m, 0) as UpdateDecision.Available).mandatory)
    }

    @Test fun skippedVersionIsRememberedButMandatoryIgnoresSkip() {
        assertTrue((UpdatePolicy.decide(4, valid(manifest(code = 5)), 5) as UpdateDecision.Available).skipped)
        assertFalse((UpdatePolicy.decide(4, valid(manifest(code = 5, mandatory = true)), 5) as UpdateDecision.Available).skipped)
    }

    @Test fun picksAbiThenUniversal() {
        val apks = listOf(
            UpdateManifest.Apk("arm64-v8a", "https://github.com/a", sha, 1),
            UpdateManifest.Apk("universal", "https://github.com/u", sha, 1),
        )
        assertEquals("arm64-v8a", UpdatePolicy.pickApk(apks, listOf("arm64-v8a", "armeabi-v7a"))?.abi)
        assertEquals("universal", UpdatePolicy.pickApk(apks, listOf("x86_64"))?.abi)
        assertEquals(null, UpdatePolicy.pickApk(apks.take(1), listOf("x86_64")))
    }
}
