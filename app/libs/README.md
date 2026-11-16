# app/libs

Contient l'archive du **Spotify App Remote SDK** :

    spotify-app-remote-release-0.8.0.aar

Source : https://github.com/spotify/android-sdk/releases (v0.8.0, dernière
version publiée, licence Apache 2.0). Elle est versionnée dans le dépôt pour
que le projet compile directement après un clone.

Le `build.gradle.kts` consomme automatiquement tout `*.aar` présent dans ce
dossier via `fileTree("libs")`.
