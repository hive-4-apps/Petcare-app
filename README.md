# PetCare

Application Android (français / português do Brasil) pour suivre les traitements de son chien ou de son chat.

1. **Nouveau suivi** : choisir un animal déjà enregistré ou en créer un (nom, chien ou chat).
2. **Photo des documents du vétérinaire** (une photo par page, ou depuis la galerie). Le texte est lu
   **sur le téléphone** (ML Kit, hors ligne) : rien n'est envoyé sur Internet.
3. PetCare retrouve chaque **médicament, shampooing, collyre, pommade…** avec la dose, la fréquence et la
   durée, puis affiche un écran de **vérification** : tout est modifiable, ce qui n'a pas été trouvé sur
   l'ordonnance est signalé « À vérifier ».
4. Le **plan de soins** est créé :
   - onglet **Aujourd'hui** : les soins du jour, heure par heure, à cocher quand c'est fait (les prises
     oubliées passent en rouge) ;
   - fiche de l'animal → **Plan complet** : tous les jours du traitement, avec la progression
     (« 5 sur 14 prises faites ») ;
   - **rappels** à l'heure de chaque prise, avec les boutons « ✓ C'est fait » et « Dans 15 min ».

## Ce que la lecture d'ordonnance comprend

| Écrit sur l'ordonnance | Compris comme |
| --- | --- |
| `2x ao dia`, `duas vezes ao dia`, `2 fois par jour`, `matin et soir`, `BID` | 2 prises par jour (08:00, 20:00) |
| `12/12h`, `de 8 em 8 horas`, `a cada 12 horas`, `toutes les 8 h`, `q6h`, `SID/TID/QID` | 24 ÷ n prises par jour |
| `a cada 3 dias`, `dias alternados`, `tous les 3 jours`, `un jour sur deux`, `toutes les 48 h` | 1 jour sur n |
| `2x por semana`, `1 fois par semaine`, `mensal` | tous les 3-4 / 7 / 30 jours |
| `durante 14 dias`, `por 3 semanas`, `x 10 dias`, `pendant 1 mois`, `sete dias` | durée en jours |
| `uso contínuo`, `até novas orientações`, `en continu` | traitement sans fin |
| `1/2 comprimido`, `3 gouttes`, `5 ml`, `1 pipeta` | dose |
| shampoo / banho, colírio, otológico, pomada, pipeta / antipulgas, injetável… | type de soin (icône et texte du rappel) |

Un traitement commencé le soir se termine le matin après le dernier jour : le nombre total de prises est
toujours respecté (ex. 2 fois par jour pendant 7 jours = 14 prises).

## Installer sur le téléphone (adb)

Avec [Platform Tools](https://developer.android.com/tools/releases/platform-tools) et le débogage USB activé :

```
adb install -r petcare.apk
```

`-r` remplace la version installée **en gardant les données** (animaux, soins, historique, photos).
À la première ouverture, autoriser les notifications pour recevoir les rappels.

Chaque push sur GitHub lance le workflow **Android** : tests, compilation, APK signé publié sur la branche
`apk-build` (`petcare.apk`) et dans la release « PetCare (dernière version) ».

### Signature (mises à jour sans perte de données)

Android n'accepte une mise à jour que si elle est signée avec la **même clé** que la version installée.
Toutes les versions sont donc signées avec une clé permanente (RSA 4096, valable 100 ans), gardée dans les
secrets du dépôt et jamais dans le code :

- `PETCARE_KEYSTORE_BASE64` : le fichier `petcare.jks` encodé en base64 ;
- `PETCARE_KEYSTORE_PASSWORD` : son mot de passe (alias `petcare`).

(GitHub → *Settings* → *Secrets and variables* → *Actions* → *New repository secret*.)
Sans ces secrets, la CI compile mais ne publie pas d'APK. Empreinte SHA-256 du certificat :
`DE:64:B7:85:2B:E4:9E:93:BD:0F:33:C1:B1:11:04:41:B3:93:73:BA:FA:1E:75:72:B7:C1:53:07:FC:8D:62:7E`.

En local, un fichier `keystore.properties` (ignoré par git) à la racine suffit :

```
storeFile=/chemin/vers/petcare.jks
password=...
```

puis `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.

## Données

Tout reste sur le téléphone (`petcare.json` + photos dans le stockage privé de l'app), conservé lors des
mises à jour et inclus dans la sauvegarde Google du téléphone. *Réglages → Sauvegarde* permet aussi
d'exporter / restaurer un fichier (sans les photos).

## Structure

- `core/` : Kotlin pur, testé avec `./gradlew :core:test` — modèle, calcul du planning (`Schedule`) et lecture
  des ordonnances FR/PT (`PrescriptionParser`).
- `app/` : application Android (Jetpack Compose, Material 3), lecture du texte (ML Kit), stockage, rappels
  (alarmes exactes, redémarrage, boutons des notifications). `./gradlew :app:testDebugUnitTest` lance l'app
  sous Robolectric et parcourt un suivi complet.

PetCare aide à suivre un traitement ; il ne remplace pas l'avis du vétérinaire. Vérifiez toujours le plan
avec l'ordonnance.
