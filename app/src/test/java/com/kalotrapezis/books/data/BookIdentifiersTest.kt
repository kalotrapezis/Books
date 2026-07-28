package com.kalotrapezis.books.data

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BookIdentifiersTest {
    @Test fun `hashes an empty stream`() {
        val hashes = BookIdentifiers.calculate(ByteArrayInputStream(ByteArray(0)))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hashes.sha256)
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", hashes.foliateMd5)
    }

    @Test fun `hashes a small stream and uses metadata identifier`() {
        val hashes = BookIdentifiers.calculate(ByteArrayInputStream("abc".encodeToByteArray()))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hashes.sha256)
        assertEquals("900150983cd24fb0d6963f7d28e17f72", hashes.foliateMd5)
        assertEquals("urn:test:book", BookIdentifiers.foliateKey(" urn:test:book ", hashes.foliateMd5))
    }

    @Test fun `short reads produce the same hashes`() {
        val bytes = "a stream which is deliberately split into tiny pieces".encodeToByteArray()
        assertEquals(
            BookIdentifiers.calculate(ByteArrayInputStream(bytes)),
            BookIdentifiers.calculate(ChunkedInputStream(bytes, 2)),
        )
    }

    @Test fun `only the first ten megabytes affect the foliate fallback`() {
        val prefix = RepeatingInputStream(BookIdentifiers.FOLIATE_HASH_BYTES, 0x42)
        val first = BookIdentifiers.calculate(prefix.withSuffix(0x01))
        val second = BookIdentifiers.calculate(RepeatingInputStream(BookIdentifiers.FOLIATE_HASH_BYTES, 0x42).withSuffix(0x02))
        assertEquals(first.foliateMd5, second.foliateMd5)
        assertNotEquals(first.sha256, second.sha256)
        assertEquals("foliate:${first.foliateMd5}", BookIdentifiers.foliateKey(null, first.foliateMd5))
    }

    private class ChunkedInputStream(private val bytes: ByteArray, private val chunkSize: Int) : InputStream() {
        private var offset = 0
        override fun read(): Int = if (offset == bytes.size) -1 else bytes[offset++].toInt() and 0xff
        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            if (offset == bytes.size) return -1
            val count = minOf(chunkSize, len, bytes.size - offset)
            bytes.copyInto(buffer, off, offset, offset + count)
            offset += count
            return count
        }
    }

    private class RepeatingInputStream(private var remaining: Long, private val value: Int) : InputStream() {
        override fun read(): Int = if (remaining-- > 0) value else -1
        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            if (remaining == 0L) return -1
            val count = minOf(remaining, len.toLong()).toInt()
            buffer.fill(value.toByte(), off, off + count)
            remaining -= count
            return count
        }
    }

    private fun InputStream.withSuffix(value: Int) = object : InputStream() {
        private var suffixRead = false
        override fun read(): Int {
            val next = this@withSuffix.read()
            if (next >= 0) return next
            return if (suffixRead) -1 else value.also { suffixRead = true }
        }
        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            val count = this@withSuffix.read(buffer, off, len)
            if (count >= 0) return count
            if (suffixRead) return -1
            buffer[off] = value.toByte()
            suffixRead = true
            return 1
        }
    }
}
