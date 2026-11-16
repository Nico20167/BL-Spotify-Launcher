package com.bluetoothspotifytrigger.spotify

/**
 * Conversion de ce que l'utilisateur colle (URI ou lien de partage) en URI
 * Spotify canonique, seul format accepté par l'App Remote SDK (`play(uri)`).
 *
 * Formats acceptés :
 * - `spotify:playlist:37i9dQZF1DXcBWIGoYBM5M`
 * - `https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc` (lien « Partager »)
 * - `https://open.spotify.com/intl-fr/album/…` (variante localisée)
 * - `spotify:user:<x>:playlist:<id>` (ancien format de playlist)
 *
 * Kotlin pur (pas d'API Android) → testable en test unitaire JVM.
 */
object SpotifyLink {

    /** Types de contenu qu'on peut lancer en lecture. */
    val PLAYABLE_TYPES = setOf("track", "album", "playlist", "artist", "episode", "show")

    // Les IDs Spotify sont en base62, 22 caractères.
    private const val ID = "[0-9A-Za-z]{22}"
    private val TYPES = PLAYABLE_TYPES.joinToString("|")

    private val uriRegex = Regex("^spotify:($TYPES):($ID)$")
    private val legacyUserPlaylistRegex = Regex("^spotify:user:[^:]+:playlist:($ID)$")
    private val webRegex = Regex(
        "^https?://open\\.spotify\\.com/(?:intl-[a-zA-Z-]+/)?($TYPES)/($ID)(?:[/?#].*)?$",
    )

    sealed interface Result {
        data class Valid(val uri: String) : Result
        data class Invalid(val reason: String) : Result
    }

    fun parse(input: String): Result {
        val s = input.trim()
        if (s.isEmpty()) return Result.Invalid("Vide")

        uriRegex.matchEntire(s)?.let { return Result.Valid(s) }
        legacyUserPlaylistRegex.matchEntire(s)?.let {
            return Result.Valid("spotify:playlist:${it.groupValues[1]}")
        }
        webRegex.matchEntire(s)?.let {
            return Result.Valid("spotify:${it.groupValues[1]}:${it.groupValues[2]}")
        }

        return Result.Invalid(
            when {
                s.contains("spotify.link") || s.contains("spotify.app.link") ->
                    "Lien court non supporté : ouvre-le, puis copie le lien complet open.spotify.com"
                s.startsWith("spotify:") || s.contains("open.spotify.com") ->
                    "Type de contenu non supporté ou identifiant invalide"
                else -> "Colle un lien Spotify (Partager → Copier le lien) ou un URI spotify:…"
            },
        )
    }
}
