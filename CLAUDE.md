# BluetoothSpotifyTrigger — Spécification projet

## Objectif

Application Android qui, **dès qu'un téléphone se connecte à un appareil Bluetooth donné** (ex. autoradio, casque, enceinte), **déclenche automatiquement la lecture d'un contenu Spotify** configurable par l'utilisateur (playlist, album, titre).

Cas d'usage type : je monte en voiture → mon téléphone s'appaire à l'autoradio → ma playlist conduite démarre seule.

---

## Contraintes & contexte

- **Plateforme** : Android natif, Kotlin.
- **Min SDK** : 26 (Android 8.0). **Target SDK** : 34+.
- **UI** : Jetpack Compose. L'UI sert uniquement à la configuration ; le cœur fonctionnel est un service en arrière-plan.
- **Architecture** : MVVM.
- **Pas de backend serveur** : tout est local sur le téléphone.
- **Développement** : projet piloté par Claude Code dans un container Docker. Le développeur est ingénieur mécatronique (à l'aise avec l'embarqué, le hardware, la logique système ; moins familier des spécificités du framework Android — sois explicite sur les pièges Android).

---

## Fonctionnalités (scope V1)

1. Lister les appareils Bluetooth **appairés** (bonded devices).
2. Pour chaque appareil, permettre d'associer un **URI Spotify** (`spotify:playlist:...`, `spotify:album:...`, `spotify:track:...`).
3. Persister ces associations `adresse MAC → URI Spotify`.
4. Un service en arrière-plan détecte la **connexion** d'un appareil Bluetooth (`ACTION_ACL_CONNECTED`).
5. Si l'appareil connecté correspond à une association enregistrée → **lancer la lecture Spotify** de l'URI associé.
6. Gérer proprement les permissions runtime et la survie du service en arrière-plan.

### Hors scope V1 (à noter pour plus tard, ne pas implémenter)
- Déclenchement sur déconnexion (pause auto).
- Délai/conditions (heure, jour).
- Multi-URI aléatoire par appareil.

---

## Choix techniques imposés

### Contrôle de Spotify : Spotify App Remote SDK
- Utiliser le **Spotify App Remote SDK** (`spotify-app-remote`), **pas** la Web API REST.
- Raison : on veut piloter l'app Spotify native déjà installée (`play(uri)`, pause, skip) sans gérer un flux OAuth complet ni refresh tokens côté lecture.
- Le développeur a déjà créé une app sur le Spotify Developer Dashboard et récupéré le **Client ID**. Prévoir un emplacement clair pour le configurer (ex. `local.properties` ou `BuildConfig`, **jamais commité en dur**).
- Le **redirect URI** déclaré sur le dashboard doit correspondre à celui du code (ex. `bluetoothspotifytrigger://auth`).
- L'authentification App Remote nécessite l'app Spotify officielle installée et un utilisateur connecté. Gérer le cas où elle est absente (message clair).

### Détection Bluetooth
- `BroadcastReceiver` sur `BluetoothDevice.ACTION_ACL_CONNECTED`.
- **Enregistrement dynamique** du receiver dans un **foreground service**, PAS dans le manifest (les broadcasts Bluetooth ne sont plus délivrés de façon fiable via manifest depuis API 26+).
- Récupérer le `BluetoothDevice` depuis l'intent, extraire l'**adresse MAC** pour filtrer.
- Implémenter un **débounce** : les events ACL peuvent arriver en double.

---

## Architecture cible

```
app/
├── di/                          # (optionnel) Hilt si tu en mets
├── data/
│   ├── TriggerRepository.kt     # CRUD des associations MAC -> URI
│   └── local/                   # DataStore (ou Room si multi-trigger évolué)
├── bluetooth/
│   ├── BluetoothMonitorService.kt   # Foreground service, host du receiver
│   └── BtConnectionReceiver.kt      # BroadcastReceiver ACL_CONNECTED
├── spotify/
│   └── SpotifyController.kt      # Wrapper SpotifyAppRemote (connect, play)
├── ui/
│   ├── MainActivity.kt
│   ├── DeviceListScreen.kt      # Liste appareils appairés + sélection URI
│   └── *ViewModel.kt
└── MainApplication.kt
```

**Flux runtime** :
`BluetoothMonitorService` (foreground, notification persistante) enregistre `BtConnectionReceiver` → à la connexion d'un device, le receiver interroge `TriggerRepository` → si match, appelle `SpotifyController.connectAndPlay(uri)`.

---

## Permissions (point sensible — sois rigoureux)

| Permission | Quand | Note |
|---|---|---|
| `BLUETOOTH_CONNECT` | Runtime, **API 31+** | Indispensable pour lire les bonded devices et les events |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | Manifest, **≤ API 30** | Legacy |
| `POST_NOTIFICATIONS` | Runtime, **API 33+** | Requis pour la notif du foreground service |
| `FOREGROUND_SERVICE` | Manifest | |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Manifest, **API 34+** | Type de FGS approprié |

- Gérer le **flow de demande runtime** proprement (cas refusé, "ne plus demander").
- Le foreground service doit déclarer un `foregroundServiceType` cohérent (`connectedDevice`).

---

## Pièges Android à anticiper (important)

1. **Survie du service** : Android tue agressivement les services en arrière-plan. Foreground service + notification persistante = la seule voie fiable. Tester en mode **Doze** et après redémarrage.
2. **Relance au boot** : prévoir (optionnel V1) un receiver `BOOT_COMPLETED` pour relancer le service, avec la permission `RECEIVE_BOOT_COMPLETED`.
3. **Manifest vs dynamic receiver** : ne PAS tenter d'écouter `ACL_CONNECTED` via le manifest. Enregistrement dynamique only.
4. **Restrictions constructeurs** (Xiaomi, Samsung, etc.) : certains OEM tuent les services malgré tout — documenter dans le README que l'utilisateur devra peut-être exempter l'app de l'optimisation batterie.
5. **Débounce des events ACL** pour éviter de relancer la musique plusieurs fois.
6. **SpotifyAppRemote.connect** est async : gérer le callback de connexion avant d'appeler `play()`, et déconnecter proprement après usage pour ne pas fuiter.

---

## Étapes de réalisation (ordre suggéré)

1. **Bootstrap** : projet Compose vide qui build et s'installe (`./gradlew installDebug`). Vérifier l'environnement Docker/CLI.
2. **Permissions** : écran qui demande et affiche l'état des permissions BT + notifications.
3. **Liste des appareils appairés** affichée dans l'UI.
4. **Persistance** des associations MAC → URI (DataStore).
5. **Foreground service** + receiver dynamique qui **log** simplement la connexion (sans Spotify encore).
6. **Intégration Spotify App Remote** : `connectAndPlay(uri)` testé manuellement via un bouton.
7. **Câblage final** : la connexion BT déclenche le play du bon URI.
8. **Robustesse** : débounce, gestion app Spotify absente, test Doze.

> Avance étape par étape. À chaque étape, l'app doit compiler et être testable. Ne pas tout coder d'un bloc.

---

## Critères de "fait" (V1)

- [ ] L'app build et s'installe via Gradle CLI.
- [ ] Les permissions sont demandées et gérées (refus inclus).
- [ ] Les appareils appairés s'affichent et on peut leur associer un URI Spotify.
- [ ] Les associations survivent au redémarrage de l'app.
- [ ] Le foreground service tourne avec notification persistante.
- [ ] Connecter l'appareil BT configuré lance le bon contenu Spotify.
- [ ] Comportement correct si l'app Spotify est absente ou l'utilisateur non connecté.

---

## Infos à fournir par le développeur (placeholders)

- `SPOTIFY_CLIENT_ID` = `<à renseigner dans local.properties>`
- `SPOTIFY_REDIRECT_URI` = `bluetoothspotifytrigger://auth` (à déclarer aussi sur le dashboard Spotify)
- Adresse(s) MAC de l'appareil cible : récupérées dynamiquement via l'UI, rien à coder en dur.

---

## Documentation de référence à consulter

- Spotify App Remote SDK (Android) — guide d'auth et `playerApi.play(uri)`.
- Android : foreground services, types de FGS (API 34), permissions Bluetooth runtime (API 31+).
- Vérifier les versions à jour du SDK Spotify et des dépendances Gradle au démarrage (peuvent avoir changé).
