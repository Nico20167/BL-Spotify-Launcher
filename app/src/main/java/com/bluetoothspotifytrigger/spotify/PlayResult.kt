package com.bluetoothspotifytrigger.spotify

/** Issue d'une tentative de lancement de la lecture Spotify. */
sealed interface PlayResult {
    /** Un son a été détecté après le lancement. */
    data object Success : PlayResult

    data object SpotifyNotInstalled : PlayResult

    /** Spotify a été ouvert mais aucun son n'a été détecté. */
    data object NotConfirmed : PlayResult

    data class Error(val message: String) : PlayResult
}

/** Message lisible pour l'utilisateur. */
fun PlayResult.describe(): String = when (this) {
    PlayResult.Success -> "Lecture lancée ✅"
    PlayResult.SpotifyNotInstalled -> "L'app Spotify n'est pas installée."
    PlayResult.NotConfirmed ->
        "Lecture non confirmée (aucun son détecté). Vérifie que Spotify est connecté " +
            "et l'autorisation « Afficher par-dessus les autres apps »."
    is PlayResult.Error -> "Erreur : $message"
}
