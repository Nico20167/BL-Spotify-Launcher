# BluetoothSpotifyTrigger

Application Android qui déclenche automatiquement la lecture d'un contenu Spotify dès que le téléphone se connecte à un appareil Bluetooth configuré.

Voir [`CLAUDE.md`](./CLAUDE.md) pour la spécification fonctionnelle et technique complète.

---

## Prérequis

### Sur l'hôte
- Un **téléphone Android physique** (indispensable : ni l'émulateur ni le container ne simulent une vraie connexion Bluetooth + l'app Spotify native).
- L'app **Spotify officielle** installée sur le téléphone, avec un compte connecté.
- `adb` accessible depuis l'hôte pour installer/tester l'APK.
- Débogage USB activé sur le téléphone.

### Compte Spotify Developer
- Une app créée sur le [Spotify Developer Dashboard](https://developer.spotify.com/dashboard).
- **Client ID** récupéré.
- **Redirect URI** déclaré : `bluetoothspotifytrigger://auth`.
- Le **package name** et l'**empreinte SHA-1** du keystore de debug ajoutés dans les paramètres de l'app Spotify (requis par l'App Remote SDK).

---

## Configuration des secrets

Ne **jamais** committer ces valeurs. Renseigner dans `local.properties` (déjà gitignoré par défaut) :

```properties
sdk.dir=/opt/android-sdk
SPOTIFY_CLIENT_ID=ton_client_id_ici
SPOTIFY_REDIRECT_URI=bluetoothspotifytrigger://auth
```

Le build expose ces valeurs via `BuildConfig` (configuré dans `build.gradle`).

---

## Environnement Docker

Le container sert à **écrire et compiler** le code (Claude Code y tourne). Le **test se fait sur device physique depuis l'hôte**.

### Toolchain nécessaire dans le container
- JDK **17**
- Android SDK (command-line tools) + `platforms;android-34` + `build-tools;34.0.0`
- Licences SDK acceptées : `yes | sdkmanager --licenses`

### Exemple de `Dockerfile`
```dockerfile
FROM eclipse-temurin:17-jdk

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools

RUN apt-get update && apt-get install -y wget unzip git && rm -rf /var/lib/apt/lists/*

RUN mkdir -p $ANDROID_SDK_ROOT/cmdline-tools && \
    cd $ANDROID_SDK_ROOT/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O tools.zip && \
    unzip -q tools.zip && mv cmdline-tools latest && rm tools.zip

RUN yes | sdkmanager --licenses && \
    sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

WORKDIR /workspace
```

> Vérifier les numéros de version au démarrage (build-tools, plateforme, lien des cmdline-tools) — ils évoluent.

### Limite à connaître
Le build se fait dans le container, mais **l'installation et le test se font depuis l'hôte** (l'accès USB/adb depuis un container est fragile). Workflow : le container produit l'APK → tu l'installes depuis l'hôte.

---

## Commandes de build (CLI)

Depuis la racine du projet :

```bash
# Compiler l'APK debug
./gradlew assembleDebug

# Installer directement sur un device branché (depuis l'hôte, pas le container)
./gradlew installDebug

# Ou installer un APK produit dans le container, depuis l'hôte :
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Logs filtrés pour suivre le service et le déclenchement
adb logcat | grep -E "BluetoothMonitor|SpotifyController|BtConnection"

# Récupérer l'empreinte SHA-1 du keystore de debug (pour le dashboard Spotify)
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
  -storepass android -keypass android | grep SHA1
```

---

## Workflow de développement

1. Build dans le container : `./gradlew assembleDebug`.
2. APK récupéré côté hôte, installé via `adb install -r`.
3. Test manuel sur le téléphone (appairer/connecter l'appareil BT cible).
4. Lecture des logs via `adb logcat`.

Voir [`CLAUDE.md`](./CLAUDE.md) → section "Étapes de réalisation" pour l'ordre d'implémentation. **Chaque étape doit compiler et être testable avant de passer à la suivante.**

---

## Test du déclencheur sans bouger

Pour tester le `ACL_CONNECTED` sans avoir à débrancher/rebrancher physiquement l'appareil à chaque fois : depuis les paramètres Bluetooth du téléphone, déconnecter puis reconnecter l'appareil appairé suffit à émettre l'event. Garder `adb logcat` ouvert en parallèle.

---

## Statut

V1 en cours. Scope défini dans `CLAUDE.md`.
