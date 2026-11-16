# BT Spotify Trigger

Android app that automatically starts a Spotify playlist as soon as your phone
connects to a chosen Bluetooth device (car stereo, speaker, headphones), with a
per-device start volume and optional shuffle.

> Get in the car → the phone connects to the car stereo → your driving playlist
> starts on its own, at the right volume.

No Spotify developer account is needed: the app drives the official Spotify app
already installed on the phone.

---

## Download

Get the latest `.apk` from the [**Releases**](../../releases/latest) page.

Requirements: Android 8.0+, official Spotify app installed and logged in.

## Install and set up

1. Open the downloaded `.apk` on the phone. Android asks you to allow installing
   unknown apps for the app you opened it from (Files, Drive, WhatsApp…):
   accept. Google Play Protect may warn about an unknown app: choose
   "Install anyway".
2. Open **BT Spotify Trigger** (the interface is in French; on-screen labels are
   quoted below with their translation). **Configuration** tab, in order:
   1. « Demander les permissions » (request permissions: Bluetooth,
      notifications);
   2. special access, each with an « Autoriser » (allow) button:
      - « Afficher par-dessus les autres apps » (display over other apps):
        **required**, it is what allows Spotify to be opened while the phone
        is locked;
      - « Ignorer l'optimisation batterie » (ignore battery optimization):
        recommended;
      - « Accès aux notifications » (notification access): optional, only
        needed for shuffle. If the option is greyed out: App info → ⋮ (top
        right) → "Allow restricted settings", then try again;
   3. « Démarrer la surveillance » (start monitoring).
3. **Appareils** (devices) tab → on the device you want, « Associer une
   playlist »: paste the Spotify link (in Spotify: Share → Copy link),
   optionally enable « Régler le volume au démarrage » (start volume) and
   « Lecture aléatoire » (shuffle), « Enregistrer » (save), then « Tester »
   (test).
4. Done. Monitoring runs in the background (persistent notification) and
   restarts on its own after a phone reboot.

### Behaviour

- **One trigger per connection.** A reconnection less than 5 s after a
  disconnection (radio glitch) is ignored; turning the device off and on again
  triggers again.
- **Never interrupts.** If something is already playing (song, podcast,
  video…) when the device connects, or during a call, the app does nothing:
  the current audio simply moves to the device.
- **Waits for the audio link.** The playlist starts once the Bluetooth audio
  profile (A2DP) is ready, so the music doesn't start on the phone speaker.

### Known limitations

- **Delay**: about 5–10 s between the Bluetooth connection and the music
  (Spotify has to start), a bit more when the phone is locked.
- **Spotify Free**: Spotify may force shuffle instead of the exact order.
- **Unofficial mechanism**: the app relies on an undocumented Spotify
  behaviour (`spotify:…:play` links). A future Spotify version could change it.
- **Aggressive manufacturers** (Samsung, Xiaomi, Huawei…): on top of the battery
  exemption, you may need to allow "auto-start" or disable "app sleeping" in the
  manufacturer's settings.

Tested on Pixel 9 Pro XL (Android 17) with Spotify 9.2.

---

## Security and privacy

**The app has no Internet access** (no `INTERNET` permission): it cannot send
anything anywhere. No account, no ads, no analytics. Everything stays in the
app's private storage: for each configured device, its Bluetooth address, the
playlist, the volume and the shuffle setting.

What each permission is used for, and nothing more:

| Permission | Actual use |
|---|---|
| Bluetooth (`BLUETOOTH_CONNECT`) | List paired devices, know when one of them connects. |
| Notifications | Show the persistent monitoring notification and failure alerts. |
| Display over other apps | **Only** the Android exemption that allows opening Spotify from the background. The app never draws anything over other apps. |
| Ignore battery optimization | Prevent the system from stopping the monitoring. |
| Notification access (optional) | **Only** the gateway Android requires to reach Spotify's playback controls (shuffle, next track). The corresponding service is empty: no notification is ever read. |
| Run at startup | Restart monitoring after a reboot, if it was running. |

What the app can do on the phone is limited to: opening **the official Spotify
app** (`com.spotify.music`) on content whose address has been validated
(`spotify:<type>:<id>` only), sending the "Play" media key, setting the media
volume, and enabling shuffle / skipping to the next track in Spotify. It does
nothing if audio is already playing or during a call.

**What another app cannot do** (verified on a phone, Android 17):
- fake a Bluetooth connection or a reboot to trigger the app: these events are
  *protected broadcasts* that Android reserves for the system (denied even to
  the `adb` shell);
- control the app: no component accepts commands from outside (the main screen
  reads no parameters; the notification-access service can only be bound by
  the system);
- use the debug build's simulation commands: absent from release builds, and
  restricted to `adb` (`DUMP` permission) in debug builds.

Known limitation: a malicious Bluetooth device in range spoofing the address of
one of your configured devices could, at worst, start **your own playlist**.
No other effect is possible.

### Verify that an APK is genuine

Official APKs are signed with this certificate:

```
SHA-256: 67:9D:D7:BC:AB:86:EA:E8:1F:BD:06:62:03:01:26:B0:41:1F:7E:9E:35:7A:A4:9A:66:B5:7A:D7:09:AD:CD:C1
```

```bash
apksigner verify --print-certs bt-spotify-trigger.apk   # "Signer #1 certificate SHA-256 digest"
```

An APK signed with any other certificate is not an official build (and cannot
be installed over an official one).

---

## How it works

- A **foreground service** (`connectedDevice` type, persistent notification)
  registers a receiver for `ACL_CONNECTED`, `ACL_DISCONNECTED` and A2DP
  connection-state broadcasts. Receivers must be registered at runtime: these
  broadcasts are no longer delivered to manifest receivers since Android 8.
- A **coordinator** decides when to trigger: on A2DP connection, or 8 s after
  the ACL link if A2DP never comes (e.g. LE Audio earbuds); once per connection;
  micro-disconnections ignored; triggers serialized.
- Playback: `ACTION_VIEW` intent on `spotify:<type>:<id>:play` targeted at
  `com.spotify.music`. Starting an activity from the background is blocked since
  Android 10, hence the "Display over other apps" exemption. When Spotify
  ignores `:play` (content already loaded) or cannot start its playback service
  while the phone is locked, the app sends the `MEDIA_PLAY` key every 3 s until
  audio is detected (`AudioManager.isMusicActive`). Each media key also grants
  Spotify a temporary right to start its foreground service.
- Shuffle goes through Spotify's media session (`MediaControllerCompat`).
- Settings are stored with DataStore (per Bluetooth address: URI, volume,
  shuffle).

The [Spotify App Remote SDK](https://github.com/spotify/android-sdk) was tried
and dropped: it requires a developer Client ID, a Premium developer account, and
limits an app to 5 registered users (Development Mode), which rules out sharing.

```
app/src/main/java/com/bluetoothspotifytrigger/
├── bluetooth/   # foreground service, receivers, connection coordinator, boot receiver
├── trigger/     # connected device → settings → playback
├── spotify/     # :play intent + media key + volume, link parsing, media session
├── data/        # DataStore repository, preferences
├── debug/       # adb simulation commands (debug builds only)
└── ui/          # Configuration / Devices tabs (Jetpack Compose)
```

---

## Build

JDK 17+ (tested with Temurin 21), Android SDK (platform 35, build-tools 35.0.0).

```bash
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew installDebug        # installs on the connected phone (adb)
```

### Signing

Release builds are signed with the key described in `keystore.properties`
(project root, **never committed**):

```properties
storeFile=/path/to/release-key.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Without `keystore.properties`, builds use the default Android debug key.

### Build chain integrity

- `gradle/wrapper/gradle-wrapper.jar` is identical to the official Gradle 8.10.2
  jar (SHA-256 `2db75c40…48046`, checkable on services.gradle.org).
- The downloaded Gradle distribution is verified by checksum
  (`distributionSha256Sum` in `gradle-wrapper.properties`).
- Dependencies: AndroidX / Kotlin only (Google Maven, Maven Central).

### Environment pitfall

Antivirus software with HTTPS scanning (e.g. Avast) makes Gradle fail with
`PKIX path building failed`. On Windows, add
`-Djavax.net.ssl.trustStoreType=Windows-ROOT` to `org.gradle.jvmargs` in your
**user-level** `~/.gradle/gradle.properties` (not in the project), and to
`JAVA_OPTS` for the wrapper download.

---

## Testing without hardware (debug build)

The debug build accepts `adb` commands (restricted to adb through the `DUMP`
permission) that simulate Bluetooth events and go through exactly the same code
as real ones:

```bash
# Add two fake paired devices to the list
adb shell am broadcast -p com.bluetoothspotifytrigger \
  -a com.bluetoothspotifytrigger.debug.SET_FAKE_DEVICES --ez enabled true

# Simulate a device connection (radio link, then A2DP audio)
adb shell am broadcast -p com.bluetoothspotifytrigger \
  -a com.bluetoothspotifytrigger.debug.SIMULATE_ACL_CONNECTED \
  --es address 02:00:00:00:00:01 --es name "'Car (simulated)'"
adb shell am broadcast -p com.bluetoothspotifytrigger \
  -a com.bluetoothspotifytrigger.debug.SIMULATE_A2DP_CONNECTED \
  --es address 02:00:00:00:00:01 --es name "'Car (simulated)'"

# Simulate a disconnection
adb shell am broadcast -p com.bluetoothspotifytrigger \
  -a com.bluetoothspotifytrigger.debug.SIMULATE_ACL_DISCONNECTED \
  --es address 02:00:00:00:00:01 --es name "'Car (simulated)'"

# Follow what happens
adb logcat -s BtConnectionReceiver ConnectionCoordinator TriggerHandler SpotifyIntentPlayer SpotifySessionControl
```

---

## License

[MIT](./LICENSE): free to use, modify and redistribute.

Personal project, **not affiliated** with Spotify or the Bluetooth SIG. Spotify is
a trademark of Spotify AB; Bluetooth is a trademark of Bluetooth SIG, Inc. The
icon uses the "bluetooth" symbol from Google's Material Icons (Apache 2.0).
