package com.kalotrapezis.books.data

import java.io.InputStream
import java.security.MessageDigest

data class BookHashes(val sha256: String, val foliateMd5: String)

object BookIdentifiers {
    const val FOLIATE_HASH_BYTES = 10_000_000L

    fun calculate(input: InputStream): BookHashes {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val md5 = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var md5BytesRemaining = FOLIATE_HASH_BYTES

        input.use {
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                sha256.update(buffer, 0, count)
                val md5Count = minOf(count.toLong(), md5BytesRemaining).toInt()
                if (md5Count > 0) {
                    md5.update(buffer, 0, md5Count)
                    md5BytesRemaining -= md5Count
                }
            }
        }
        return BookHashes(sha256.digest().hex(), md5.digest().hex())
    }

    fun foliateKey(metadataIdentifier: String?, fallbackMd5: String): String =
        metadataIdentifier?.trim()?.takeIf { it.isNotEmpty() }
            ?: "foliate:${fallbackMd5.lowercase()}"

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
