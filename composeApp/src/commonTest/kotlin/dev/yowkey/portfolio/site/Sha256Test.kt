package dev.yowkey.portfolio.site

import kotlin.test.Test
import kotlin.test.assertEquals

/** FIPS 180-4's own examples, and the edges of the padding: the CSP's hashes are only as right as this. */
class Sha256Test {

    @Test
    fun theStandardsExamples() {
        assertEquals("'sha256-47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU='", Sha256.cspSource(""))
        assertEquals("'sha256-ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0='", Sha256.cspSource("abc"))
        assertEquals(
            "'sha256-JI1qYdIGOLjlwCaTDD5gOaM85Flk/yFn9uzt1BnbBsE='",
            Sha256.cspSource("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }

    /** 55 bytes fit one block with the length; 56 need a second; 64 is a whole block before the padding. */
    @Test
    fun messagesAtTheBlockEdges() {
        assertEquals("'sha256-n0OQ+NMMLdkuyfCVtl4rmumwqSWlJY4kHJ8ekQ9zQxg='", Sha256.cspSource("a".repeat(55)))
        assertEquals("'sha256-s1Q5pKxvCUi21vnjxq8PX1kM4g8b3nCQ73lwaG7Gc4o='", Sha256.cspSource("a".repeat(56)))
        assertEquals("'sha256-/+BU/nrgy23GXDr5th1SCfQ5hR20PQulmXM33xVGaOs='", Sha256.cspSource("a".repeat(64)))
    }

    /** Hashed as UTF-8, as browsers hash a script: the page's scripts have ★ and … in them. */
    @Test
    fun textIsHashedAsUtf8() {
        assertEquals("'sha256-EfVGpc9xsQAdfTRS3LnJCbTXHZ8Ac5tmtG3kdOFVAAs='", Sha256.cspSource("★…"))
    }
}
