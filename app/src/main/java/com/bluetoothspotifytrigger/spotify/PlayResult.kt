package com.bluetoothspotifytrigger.spotify

/** Issue d'une tentative de lancement de la lecture Spotify. */
sealed interface PlayResult {
    /** Un son a été détecté après le lancement. */
    data object Success : PlayResult

    data object SpotifyNotInstalled : PlayResult

    /** Spotify a été ouvert mais aucun son n'a été détecté. */
    data object NotConfirmed : PlayResult

    /** Volontairement rien fait (ex. une lecture est déjà en cours). Pas une erreur. */
    data class Skipped(val reason: String) : PlayResult

    data class Error(val message: String) : PlayResult
}

/** true si l'issue ne mérite pas d'alerte (lecture lancée, ou rien à faire). */
val PlayResult.isOk: Boolean get() = this == PlayResult.Success || this is PlayResult.Skipped

/** Message lisible pour l'utilisateur. */
fun PlayResult.describe(): String = when (this) {
    PlayResult.Success -> "Lecture lancée ✅"
    PlayResult.SpotifyNotInstalled -> "L'app Spotify n'est pas installée."
    PlayResult.NotConfirmed ->
        "Lecture non confirmée (aucun son détecté). Vérifie que Spotify est connecté " +
            "et l'autorisation « Afficher par-dessus les autres apps »."
    is PlayResult.Skipped -> "Rien fait : $reason"
    is PlayResult.Error -> "Erreur : $message"
}
