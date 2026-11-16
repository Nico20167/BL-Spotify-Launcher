package com.bluetoothspotifytrigger.spotify

import com.bluetoothspotifytrigger.spotify.SpotifyLink.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyLinkTest {

    private val id = "37i9dQZF1DXcBWIGoYBM5M"

    private fun valid(input: String) = (SpotifyLink.parse(input) as Result.Valid).uri

    @Test fun `URI canonique accepte tel quel`() =
        assertEquals("spotify:playlist:$id", valid("spotify:playlist:$id"))

    @Test fun `espaces autour ignores`() =
        assertEquals("spotify:album:$id", valid("  spotify:album:$id \n"))

    @Test fun `tous les types jouables`() {
        for (type in SpotifyLink.PLAYABLE_TYPES) {
            assertEquals("spotify:$type:$id", valid("spotify:$type:$id"))
        }
    }

    @Test fun `lien de partage avec parametre si`() =
        assertEquals("spotify:playlist:$id", valid("https://open.spotify.com/playlist/$id?si=a1b2c3d4"))

    @Test fun `lien localise intl`() =
        assertEquals("spotify:track:$id", valid("https://open.spotify.com/intl-fr/track/$id"))

    @Test fun `lien http et slash final`() =
        assertEquals("spotify:album:$id", valid("http://open.spotify.com/album/$id/"))

    @Test fun `ancien format user playlist`() =
        assertEquals("spotify:playlist:$id", valid("spotify:user:someone:playlist:$id"))

    @Test fun `URI tronque refuse`() =
        assertTrue(SpotifyLink.parse("spotify:pla") is Result.Invalid)

    @Test fun `ID de mauvaise longueur refuse`() =
        assertTrue(SpotifyLink.parse("spotify:playlist:abc123") is Result.Invalid)

    @Test fun `type non jouable refuse`() =
        assertTrue(SpotifyLink.parse("spotify:user:$id") is Result.Invalid)

    @Test fun `lien court refuse avec message dedie`() {
        val r = SpotifyLink.parse("https://spotify.link/AbCdEf") as Result.Invalid
        assertTrue(r.reason.contains("Lien court"))
    }

    @Test fun `texte quelconque refuse`() {
        assertTrue(SpotifyLink.parse("ma playlist") is Result.Invalid)
        assertTrue(SpotifyLink.parse("") is Result.Invalid)
    }

    @Test fun `autre domaine refuse`() =
        assertTrue(SpotifyLink.parse("https://evil.com/playlist/$id") is Result.Invalid)
}
