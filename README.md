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

## Sécurité et vie privée

**L'app n'a pas accès à Internet** (aucune permission `INTERNET`) : elle ne peut
rien envoyer nulle part. Pas de compte, pas de publicité, pas de statistiques.
Tout reste dans le stockage privé de l'app : pour chaque appareil configuré,
son adresse Bluetooth, la playlist, le volume et le choix aléatoire.

Ce que fait chaque autorisation, et rien de plus :

| Autorisation | Utilisation réelle |
|---|---|
| Bluetooth (`BLUETOOTH_CONNECT`) | Lister les appareils appairés, savoir quand l'un d'eux se connecte. |
| Notifications | Afficher la notification permanente de la surveillance et les alertes d'échec. |
| Afficher par-dessus les autres apps | **Uniquement** l'exemption Android qui permet d'ouvrir Spotify en arrière-plan. L'app n'affiche jamais rien par-dessus les autres apps. |
| Ignorer l'optimisation batterie | Empêcher le système d'arrêter la surveillance. |
| Accès aux notifications (facultatif) | **Uniquement** la porte d'accès imposée par Android aux commandes de lecture de Spotify (mode aléatoire, titre suivant). Le service correspondant est vide : aucune notification n'est lue. |
| Démarrage au boot | Relancer la surveillance après un redémarrage, si elle était active. |

Ce que l'app peut faire sur le téléphone se limite à : ouvrir **l'app Spotify
officielle** (`com.spotify.music`) sur un contenu dont l'adresse a été validée
(`spotify:<type>:<identifiant>` uniquement), envoyer la touche média « Lecture »,
régler le volume média, et activer l'aléatoire / passer au titre suivant dans
Spotify. Elle ne fait rien si un son joue déjà ou pendant un appel.

**Ce qu'une autre app ne peut pas faire** (vérifié sur téléphone, Android 17) :
- simuler une connexion Bluetooth ou un redémarrage pour déclencher l'app :
  ces événements sont des *broadcasts protégés* qu'Android réserve au système
  (refusé même au shell `adb`) ;
- piloter l'app : aucun composant n'accepte d'ordres de l'extérieur (l'écran
  principal ne lit aucun paramètre ; le service d'accès aux notifications ne
  peut être lié que par le système) ;
- utiliser les commandes de simulation de la version debug : absentes de la
  version release, et réservées à `adb` (permission `DUMP`) en debug.

Limite connue : un appareil Bluetooth malveillant à portée qui usurperait
l'adresse d'un de vos appareils configurés pourrait, au pire, déclencher la
lecture de **votre propre playlist**. Aucun autre effet possible.

### Vérifier qu'un APK est authentique

Les APK officiels sont signés avec ce certificat :

```
SHA-256 : 67:9D:D7:BC:AB:86:EA:E8:1F:BD:06:62:03:01:26:B0:41:1F:7E:9E:35:7A:A4:9A:66:B5:7A:D7:09:AD:CD:C1
```

```bash
apksigner verify --print-certs app-release.apk   # « Signer #1 certificate SHA-256 digest »
```

Un APK signé avec un autre certificat n'est pas une version officielle (et ne
pourra pas s'installer par-dessus une version officielle).

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

### Intégrité de la chaîne de build

- `gradle/wrapper/gradle-wrapper.jar` : identique au jar officiel Gradle 8.10.2
  (SHA-256 `2db75c40…48046`, vérifiable sur services.gradle.org).
- La distribution Gradle téléchargée est vérifiée par empreinte
  (`distributionSha256Sum` dans `gradle-wrapper.properties`).
- Dépendances : uniquement AndroidX / Kotlin (Google Maven, Maven Central).

### Pièges d'environnement (Windows)

- Antivirus avec analyse HTTPS (ex. Avast) : Gradle échoue en `PKIX path
  building failed`. Ajouter `-Djavax.net.ssl.trustStoreType=Windows-ROOT` à
  `org.gradle.jvmargs` dans **`~/.gradle/gradle.properties`** (réglage propre à
  la machine, à ne pas mettre dans le projet) et à `JAVA_OPTS` pour le wrapper.
  Casse aussi le Play Store de l'émulateur.

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
