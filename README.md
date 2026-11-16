# BluetoothSpotifyTrigger

Application Android qui lance automatiquement une playlist Spotify dès que le
téléphone se connecte à un appareil Bluetooth choisi (autoradio, enceinte,
casque), avec un volume de départ réglable par appareil.

> Je monte en voiture → le téléphone se connecte à l'autoradio → ma playlist
> démarre toute seule, au bon volume.

Aucun compte développeur Spotify n'est nécessaire : l'app pilote l'application
Spotify officielle installée sur le téléphone.

Spécification et choix techniques : [`CLAUDE.md`](./CLAUDE.md).

---

## Installer l'app sur un téléphone

1. Récupérer `app-release.apk` (voir « Construire » ci-dessous, ou le fichier
   partagé).
2. Sur le téléphone, ouvrir le fichier. Android demande d'autoriser
   l'installation d'applications inconnues pour l'app utilisée (Fichiers,
   Drive, WhatsApp…) : accepter. Google Play Protect peut afficher un
   avertissement « app inconnue » : choisir « Installer quand même ».
3. Ouvrir **BT Spotify Trigger**, onglet **Configuration**, dans l'ordre :
   1. « Demander les permissions » (Bluetooth, notifications) ;
   2. autorisations spéciales : « Afficher par-dessus les autres apps »
      (**indispensable** : permet d'ouvrir Spotify téléphone verrouillé),
      « Ignorer l'optimisation batterie » (recommandé), « Accès aux
      notifications » (facultatif, uniquement pour la lecture aléatoire —
      si l'option est grisée : Infos de l'appli → ⋮ → « Autoriser les
      paramètres restreints ») ;
   3. « Démarrer la surveillance ».
4. Onglet **Appareils** → sur l'appareil voulu, « Associer une playlist » :
   coller le lien Spotify (dans Spotify : Partager → Copier le lien), régler
   si souhaité le volume de départ et la lecture aléatoire, puis « Tester ».
5. C'est tout : la surveillance tourne en arrière-plan (notification
   persistante) et redémarre seule après un redémarrage du téléphone. Si un
   son joue déjà quand l'appareil se connecte, l'app ne fait rien.

Prérequis : Android 8.0+, application Spotify installée et connectée.

### Limites connues

- **Délai** : ~5 à 10 s entre la connexion Bluetooth et la musique (Spotify doit
  démarrer), un peu plus téléphone verrouillé.
- **Spotify gratuit** : Spotify peut imposer la lecture aléatoire de la
  playlist au lieu de l'ordre exact.
- **Mécanisme non officiel** : l'app utilise un comportement de Spotify
  (`spotify:…:play`) non documenté. Une future version de Spotify pourrait le
  modifier.
- **Constructeurs agressifs** (Samsung, Xiaomi, Huawei…) : en plus de
  l'exemption batterie, il peut falloir autoriser le « démarrage automatique »
  ou désactiver la « mise en veille des apps » dans les réglages du fabricant.

---

## Construire

Environnement : JDK 17+ (testé avec Temurin 21), Android SDK (platform 35,
build-tools 35.0.0). Développement natif (pas de container : l'accès USB au
téléphone n'y est pas fiable).

```bash
./gradlew testDebugUnitTest   # tests unitaires JVM
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew installDebug        # installe sur le téléphone branché (adb)
```

### Signature

La version release est signée avec la clé décrite dans `keystore.properties`
(racine du projet, **jamais commité**) :

```properties
storeFile=C:/chemin/vers/bluetoothspotifytrigger-release.jks
storePassword=…
keyAlias=bst
keyPassword=…
```

⚠️ Sauvegarder le `.jks` et ses mots de passe : sans eux, impossible de publier
une mise à jour installable par-dessus (il faudrait désinstaller l'app et
perdre les réglages). Sans `keystore.properties`, le build utilise la clé de
debug Android par défaut.

### Pièges d'environnement (Windows)

- Antivirus avec analyse HTTPS (ex. Avast) : Gradle échoue en `PKIX path
  building failed`. Ajouter `-Djavax.net.ssl.trustStoreType=Windows-ROOT` à
  `org.gradle.jvmargs` (déjà fait) et à `JAVA_OPTS` pour le wrapper. Casse aussi
  le Play Store de l'émulateur.

---

## Tester sans matériel (build debug)

Le build debug accepte des commandes `adb` (réservées à adb via la permission
`DUMP`) qui simulent les événements Bluetooth et passent par exactement le
même code que les vrais :

```bash
# Ajouter deux appareils fictifs à la liste
adb shell am broadcast -p com.bluetoothspotifytrigger \
  -a com.bluetoothspotifytrigger.debug.SET_FAKE_DEVICES --ez enabled true

# Simuler la connexion d'un appareil (lien radio, puis audio A2DP)
adb shell am broadcast -p com.bluetoothspotifytrigger \
  -a com.bluetoothspotifytrigger.debug.SIMULATE_ACL_CONNECTED \
  --es address 02:00:00:00:00:01 --es name "'Autoradio (simulé)'"
adb shell am broadcast -p com.bluetoothspotifytrigger \
  -a com.bluetoothspotifytrigger.debug.SIMULATE_A2DP_CONNECTED \
  --es address 02:00:00:00:00:01 --es name "'Autoradio (simulé)'"

# Suivre le déroulé
adb logcat -s BtConnectionReceiver ConnectionCoordinator TriggerHandler SpotifyIntentPlayer
```
