package com.kalotrapezis.books.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverExtractorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun extractsEpub3CoverImage() {
        val uri = epub(
            "OEBPS/content.opf",
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest>
                <item id="c" href="images/cover.png" properties="cover-image"
                      media-type="image/png"/>
              </manifest>
            </package>
            """.trimIndent(),
            "OEBPS/images/cover.png",
        )
        val path = CoverExtractor.extract(context, uri, "epub3")
        assertNotNull(path)
        val cover = BitmapFactory.decodeFile(path)
        assertNotNull(cover)
        assertTrue(maxOf(cover.width, cover.height) <= 512)
        CoverExtractor.delete(path)
        assertTrue(!File(path!!).exists())
    }

    @Test fun extractsEpub2CoverByMetaId() {
        val uri = epub(
            "content.opf",
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <metadata><meta name="cover" content="c"/></metadata>
              <manifest><item id="c" href="cover.png" media-type="image/png"/></manifest>
            </package>
            """.trimIndent(),
            "cover.png",
        )
        assertNotNull(CoverExtractor.extract(context, uri, "epub2"))
    }

    @Test fun fallsBackToACoverNamedImageWhenTheMetaIdIsDangling() {
        val uri = epub(
            "content.opf",
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <metadata><meta name="cover" content="missing-id"/></metadata>
              <manifest>
                <item id="text" href="chapter.html" media-type="application/xhtml+xml"/>
                <item id="cover.jpg" href="cover.png" media-type="image/png"/>
              </manifest>
            </package>
            """.trimIndent(),
            "cover.png",
        )
        assertNotNull(CoverExtractor.extract(context, uri, "dangling"))
    }

    @Test fun returnsNullWhenCoverIsMissing() {
        val uri = epub(
            "content.opf",
            """<package xmlns="http://www.idpf.org/2007/opf"><manifest/></package>""",
            null,
        )
        assertNull(CoverExtractor.extract(context, uri, "nocover"))
    }

    @Test fun ignoresTraversalOutsideTheZip() {
        val uri = epub(
            "OEBPS/content.opf",
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest>
                <item id="c" href="../../../etc/hosts" properties="cover-image"
                      media-type="image/png"/>
              </manifest>
            </package>
            """.trimIndent(),
            "OEBPS/images/cover.png",
        )
        assertNull(CoverExtractor.extract(context, uri, "traversal"))
    }

    /** Builds a minimal EPUB zip and returns a readable file Uri for it. */
    private fun epub(opfPath: String, opf: String, coverPath: String?): Uri {
        val file = File.createTempFile("cover-test", ".epub", context.cacheDir)
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.write("META-INF/container.xml", """
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="$opfPath"/></rootfiles>
                </container>
            """.trimIndent().toByteArray())
            zip.write(opfPath, opf.toByteArray())
            if (coverPath != null) zip.write(coverPath, pngBytes())
        }
        return Uri.fromFile(file)
    }

    private fun ZipOutputStream.write(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun pngBytes(): ByteArray = ByteArrayOutputStream().also { out ->
        Bitmap.createBitmap(1200, 1800, Bitmap.Config.ARGB_8888)
            .compress(Bitmap.CompressFormat.PNG, 100, out)
    }.toByteArray()

    @Test fun samplesLargeCoversDown() {
        val uri = epub(
            "content.opf",
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest><item id="c" href="cover.png" properties="cover-image"
                    media-type="image/png"/></manifest>
            </package>
            """.trimIndent(),
            "cover.png",
        )
        val cover = BitmapFactory.decodeFile(CoverExtractor.extract(context, uri, "large"))
        assertEquals(1800 / 4, cover.height)
    }
}
