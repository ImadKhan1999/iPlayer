package com.imadlabs.iplayer.util

/**
 * Removes credentials and tokens from text before it reaches logs or diagnostics.
 * Covers userinfo in URLs (smb://user:pass@host), common token query parameters and
 * Authorization headers.
 */
object Redactor {
    private val userInfo = Regex("""([a-zA-Z][a-zA-Z0-9+.-]*://)([^/@\s]+)@""")
    private val queryToken = Regex(
        """([?&](?:access_token|token|auth|key|apikey|api_key|sig|signature|password|pwd|pass|secret)=)[^&\s#]+""",
        RegexOption.IGNORE_CASE,
    )
    private val authHeader = Regex("""(Authorization:\s*)(\S+\s+)?\S+""", RegexOption.IGNORE_CASE)
    private val vlcOptions = Regex("""(:(?:smb|ftp|sftp|http)-(?:pwd|password|user)=)\S+""", RegexOption.IGNORE_CASE)

    fun redact(text: String): String = text
        .replace(userInfo) { it.groupValues[1] + "***@" }
        .replace(queryToken) { it.groupValues[1] + "***" }
        .replace(authHeader) { it.groupValues[1] + "***" }
        .replace(vlcOptions) { it.groupValues[1] + "***" }
}
