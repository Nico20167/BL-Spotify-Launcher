# BluetoothSpotifyTrigger — Spécification projet

## Objectif

Application Android qui, **dès qu'un téléphone se connecte à un appareil Bluetooth donné** (autoradio, casque, enceinte), **lance automatiquement la lecture d'un contenu Spotify** choisi par l'utilisateur (playlist, album, titre…), **au volume configuré** pour cet appareil.

Cas d'usage type : je monte en voiture → mon téléphone se connecte à l'autoradio → ma playlist conduite démarre seule.

Doit pouvoir être **partagée à des amis** (APK) sans qu'ils aient besoin d'un compte développeur Spotify.

---

## Contraintes & contexte

- **Plateforme** : Android natif, Kotlin. **Min SDK** 26, **target/compile SDK** 35.
- **UI** : Jetpack Compose, uniquement pour la configuration ; le cœur est un foreground service.
- **Architecture** : MVVM légère, pas de DI. **Pas de backend** : tout est local.
- **Développement** : natif sur Windows (pas de container : l'USB/adb n'y passe pas de façon fiable). Le développeur est ingénieur mécatronique (à l'aise avec l'embarqué et la logique système, moins avec les spécificités Android — être explicite sur les pièges Android).
- **Tests** : tests unitaires JVM pour la logique pure ; tests fonctionnels pilotés via adb (émulateur ou téléphone réel). Sur le téléphone personnel du développeur : **toujours vérifier que l'app visée est au premier plan avant toute action d'UI adb**.

---

## Choix techniques

### Lancement de Spotify : intent `:play` (sans SDK)
- Intent `ACTION_VIEW` sur `spotify:<type>:<id>:play`, package `com.spotify.music`. Comportement **non documenté** de Spotify, validé sur Pixel 9 Pro XL / Android 17 / Spotify 9.2.
- **Pourquoi pas le Spotify App Remote SDK** (abandonné, voir historique git) : exige un Client ID, un compte Premium pour le développeur, et limite l'app à 5 utilisateurs déclarés (mode Développement, règles de février 2026) ; le mode étendu est réservé aux entreprises. Incompatible avec le partage à des amis.
- **Ouverture depuis l'arrière-plan** (Android 10+ l'interdit) : exemption via la permission spéciale « Afficher par-dessus les autres apps » (`SYSTEM_ALERT_WINDOW`, accordée par l'utilisateur).
- **Spotify ignore parfois `:play`** (contenu déjà en mémoire) et, téléphone verrouillé, ne peut pas démarrer son service de lecture seul : l'app envoie alors la touche média `PLAY` (qui accorde à Spotify une autorisation temporaire de démarrer) toutes les 3 s jusqu'à ce qu'un son soit détecté (`AudioManager.isMusicActive`).
- **Volume** : `STREAM_MUSIC` réglé avant le lancement puis réappliqué après confirmation (Android peut restaurer le volume mémorisé de l'appareil à la connexion).

### Détection Bluetooth
- Receiver **enregistré dynamiquement** dans un **foreground service** (`connectedDevice`) : `ACTION_ACL_CONNECTED` + A2DP `CONNECTION_STATE_CHANGED`.
- **Attente de l'A2DP** : l'ACL arrive avant que l'audio soit prêt ; on déclenche à la connexion A2DP, ou 8 s après l'ACL si l'A2DP ne vient pas. **Cooldown 60 s** par appareil (doublons, micro-coupures).
- Appareils affichés sous leur **alias** (nom donné par l'utilisateur dans Android).

### Persistance
- DataStore : par adresse MAC, URI Spotify + volume optionnel.
- SharedPreferences (lecture synchrone) : service activé (relance au boot), permissions déjà demandées.

---

## Architecture

```
app/src/main/java/com/bluetoothspotifytrigger/
├── bluetooth/
│   ├── BluetoothMonitorService.kt   # Foreground service, héberge les receivers
│   ├── BtConnectionReceiver.kt      # Events bruts ACL / A2DP (+ simulations debug)
│   ├── ConnectionCoordinator.kt     # Quand déclencher (attente A2DP, cooldown)
│   └── BootReceiver.kt              # Relance après reboot / mise à jour
├── trigger/TriggerHandler.kt        # Appareil connecté → réglages → lecture
├── spotify/
│   ├── SpotifyIntentPlayer.kt       # Intent :play + touche média + volume
│   ├── SpotifyLink.kt               # Lien de partage / URI → URI canonique
│   └── PlayResult.kt
├── data/                            # TriggerRepository (DataStore), AppPrefs
├── debug/DebugActions.kt            # Commandes adb de simulation (debug only)
└── ui/                              # Onglets Permissions / Appareils / Service
app/src/debug/                       # Receiver de commandes adb (build debug uniquement)
```

**Flux runtime** : `BtConnectionReceiver` → `ConnectionCoordinator` → `TriggerHandler` → `SpotifyIntentPlayer` ; le service met à jour sa notification et alerte en cas d'échec.

---

## Permissions

| Permission | Quand | Note |
|---|---|---|
| `BLUETOOTH_CONNECT` | Runtime, API 31+ | Appareils appairés, events |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | Manifest, ≤ API 30 | Legacy |
| `POST_NOTIFICATIONS` | Runtime, API 33+ | Notification du service |
| `FOREGROUND_SERVICE(_CONNECTED_DEVICE)` | Manifest | Type FGS |
| `RECEIVE_BOOT_COMPLETED` | Manifest | Relance au boot |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Manifest | Demande d'exemption batterie (à justifier si publication Play Store) |
| `SYSTEM_ALERT_WINDOW` | Réglage spécial | Ouvrir Spotify depuis l'arrière-plan |

---

## Critères de « fait » (V1)

- [x] Build et installation via Gradle CLI ; APK release signé.
- [x] Permissions demandées et gérées (refus et refus définitif inclus, persistants).
- [x] Appareils appairés listés (alias) ; association lien/URI Spotify + volume.
- [x] Réglages persistants (redémarrage de l'app, mise à jour).
- [x] Foreground service avec notification ; relance après reboot et mise à jour.
- [x] Connexion réelle de l'appareil → bon contenu lancé, téléphone verrouillé (validé : JBL, Pixel 9 Pro XL).
- [x] Spotify absent / lecture non confirmée → notification d'alerte explicite.
- [ ] Volume au démarrage validé sur téléphone réel.
- [ ] Validé sur un second téléphone (autre marque / version d'Android).
- [ ] Validé en voiture (autoradio).

### Hors scope V1
- Pause automatique à la déconnexion.
- Conditions (heure, jour), multi-URI aléatoire.
- Remplacer `SYSTEM_ALERT_WINDOW` par une association CompanionDeviceManager (plus explicite pour l'utilisateur).
