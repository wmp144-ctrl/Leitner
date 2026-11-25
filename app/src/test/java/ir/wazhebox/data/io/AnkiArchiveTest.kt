package ir.wazhebox.data.io

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * A real zstd frame, produced by the reference encoder, holding 6000 bytes that
 * start with the SQLite magic. This is the shape of `collection.anki21b` inside a
 * modern .apkg archive.
 */
class AnkiArchiveTest {

    private val frame = Base64.getDecoder().decode(
        "KLUv/WBwFtUIAAQRU1FMaXRlIGZvcm1hdCAzAAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fICEi" +
            "IyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj9AQUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVpbXF1eX2Bh" +
            "YmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5ent8fX5/gIGCg4SFhoeIiYqLjI2Oj5CRkpOUlZaXmJmam5ydnp+g" +
            "oaKjpKWmp6ipqqusra6vsLGys7S1tre4ubq7vL2+v8DBwsPExcbHyMnKy8zNzs/Q0dLT1NXW19jZ2tvc3d7f" +
            "4OHi4+Tl5ufo6err7O3u7/Dx8vP09fb3+Pn6+/z9/v8BABBdNtCvKQ==",
    )

    @Test
    fun `decompresses a zstd frame back into the sqlite file`() {
        val output = ByteArrayOutputStream()
        AnkiArchive.decompressZstd(ByteArrayInputStream(frame), output)
        val bytes = output.toByteArray()

        assertThat(bytes).hasLength(6000)
        assertThat(String(bytes, 0, 15, Charsets.ISO_8859_1)).isEqualTo("SQLite format 3")
    }

    @Test
    fun `rejects bytes that are not a zstd frame`() {
        val output = ByteArrayOutputStream()
        val failure = runCatching {
            AnkiArchive.decompressZstd(ByteArrayInputStream(byteArrayOf(0, 1, 2, 3)), output)
        }
        assertThat(failure.isFailure).isTrue()
    }
}
