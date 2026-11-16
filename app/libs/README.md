# app/libs

Déposer ici l'archive du **Spotify App Remote SDK** :

    spotify-app-remote-release-x.y.z.aar

Téléchargement : https://github.com/spotify/android-sdk/releases
(fichier `spotify-app-remote-release-*.aar` dans les assets de la dernière release).

Le `build.gradle.kts` consomme automatiquement tout `*.aar` présent dans ce
dossier via `fileTree("libs")`. Ce `.aar` n'est pas versionné (binaire).
