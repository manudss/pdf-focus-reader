# PDF Focus

Lire un journal ou une BD en PDF sur un téléphone, bloc par bloc, hors ligne et sans compte.

PDF Focus est une application Android native qui découpe chaque page en blocs (titres, colonnes, images, cases) et les enchaîne dans un ruban de lecture continu, cadré à la largeur de l'écran. Fini le pincement-zoom et les allers-retours dans la page : on choisit ses blocs, on appuie sur **Lire**, on fait défiler.

- **100 % privé** : aucun compte, aucune publicité, aucune API distante, aucune permission Internet. Tout est analysé et conservé sur l'appareil.
- **Gratuit et open source** : le code, les tests et les APK de développement sont dans ce dépôt.
- **Pensé pour les vrais documents** : journaux multi-colonnes, BD et mangas, livres, scans irréguliers.

## Comment ça marche

1. **Ouvrez un PDF** (ou essayez l'exemple fictif intégré, sans fichier à vous).
2. **L'analyse locale propose des blocs** adaptés au document : modes Auto, Journal, Livre, BD, Manga ou entièrement Manuel.
3. **Touchez les blocs dans l'ordre de lecture** : ils se numérotent. Maintenez-glissez pour en choisir plusieurs, deux doigts pour retirer, double tap pour corriger un bloc.
4. **Appuyez sur Lire** : les blocs s'enchaînent dans un ruban à faire défiler. Le texte est cadré à la largeur de l'écran, les images s'affichent entières, et une surface commune à deux blocs n'est jamais affichée deux fois.
5. **Changez de page quand vous voulez** : boutons et curseur de page restent disponibles, même pendant une détection en cours.

## Préparer sa lecture

- **Modes de page** : Auto, Journal, Livre, BD, Manga, Manuel. Chaque mode propose ses blocs avec un aperçu avant application ; rien n'est remplacé sans accord.
- **Correction d'un bloc** (double tap) : ajuster le cadre ou ses huit poignées, redessiner le cadre, séparer en deux (gauche/droite ou haut/bas), proposer l'article autour du bloc, fusionner avec d'autres blocs, déplacer un bloc à une autre position du parcours (le parcours passe en violet, un toucher le place où vous voulez), supprimer.
- **Ordre du parcours** : annuler le dernier choix, effacer, recentrer, regrouper automatiquement.
- **Cadrage manuel** : tracez un rectangle pour ajouter un bloc que la détection aurait manqué ; l'ajustement est proposé avant l'ajout, sans extension automatique.

## Lire les BD et les mangas

- **Découpe en cases** : les modes BD et Manga adaptent la détection aux cases et à leur ordre de lecture, avec aperçu avant application.
- **Case par case** : chaque case défile cadrée à la largeur de l'écran, affichée entière et jamais deux fois, raccords propres aux bords.
- **Agrandir d'un geste** : appui bref pour lire plus grand, zoom pincé conservé pendant l'exploration, retour au ruban d'un toucher.

## Lire confortablement

- **Ruban continu** : les blocs choisis défilent à la suite, numérotés, avec la progression de lecture.
- **Trois rendus** : **Original** (le PDF tel quel), **Nettoyé** (nettoyage local et léger renforcement des lettres), **Texte OCR** (les mots reconnus recomposés avec une vraie police, taille réglable Texte −/+).
- **OCR brut / amélioré** : comparez la reconnaissance initiale (Tesseract français embarqué) et des corrections locales prudentes secondées par un second moteur (PP-OCRv5, ONNX Runtime). Chiffres, noms propres et mots attestés ne sont jamais remplacés sur simple statistique ; chaque correction reste visible et réversible, passage par passage.
- **Reprise automatique** : le document, les cadres et le parcours sont retrouvés à la réouverture. Un didacticiel guidé explique les gestes au premier lancement.

## Confidentialité

- Aucune donnée ne quitte l'appareil : pas de compte, pas de journal applicatif du contenu, pas d'analyse distante.
- Une copie du dernier PDF et de ses cadres/parcours reste dans le stockage privé de l'application, hors sauvegarde Android, et se supprime depuis le menu sans toucher à l'original.
- Les mots reconnus ne servent qu'en mémoire pour le mode Texte OCR ; ils ne sont enregistrés ni dans le parcours sauvegardé ni dans le dépôt.
- L'exemple intégré est entièrement fictif : aucun journal ou fichier personnel n'est inclus dans le dépôt ni dans les tests.

## État du projet

Version stable **1.0.x** : l'APK (arm64 et x86_64) et ses sommes de contrôle SHA-256 se téléchargent depuis les [releases GitHub](https://github.com/manudss/pdf-focus-reader/releases). Installation manuelle, sans magasin d'applications.

Limites connues :

- Les mises en page très irrégulières, les scans flous ou en noir et blanc et les petits encadrés peuvent produire des blocs incorrects ou manquants ; le cadrage manuel reste là pour ça. Dans les BD, les cases très fines ou imbriquées peuvent aussi demander un ajustement à la main.
- Le mode Texte OCR est une aide à la lecture, pas une transcription garantie ; le retour au brut est immédiat.
- Limite d'import : 250 Mo. Les pages sont rendues à la demande.
- Des pré-versions de test (identifiant `com.pdffocus.reader.test`, icône à bande orange) restent publiées en prerelease pour les essais, installables à côté de la version stable, sans transfert automatique des parcours.

Une page de présentation animée accompagne le dépôt (captures réelles sur documents fictifs) : elle est servie par GitHub Pages depuis le dossier `docs/` une fois la fusion effectuée et Pages activé sur la branche principale.

## Construire et tester

Android 8.0+ (API 26), JDK 17, SDK Android 35. Kotlin, vues Android natives, PdfRenderer, Tesseract4Android et ONNX Runtime.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest  # appareil ou émulateur connecté
```

L'APK de développement est produit dans `app/build/outputs/apk/debug/` et en artefact de chaque CI. Signature de test uniquement : ne jamais commiter de clé de signature.

Les tests couvrent l'ordre choisi et sa renumérotation, la couverture continue des lignes (aucune ligne répétée ni coupée entre blocs), la soustraction des zones communes, la reprise de parcours et l'isolation des sauvegardes, ainsi que des parcours d'instrumentation complets sur documents synthétiques (sélection, lecture, OCR embarqué, changement de page pendant une détection). Les captures des parcours sont conservées avec les résultats CI.

## Organisation du dépôt

- `app/` : l'application (Kotlin, vues natives), ses tests unitaires et d'instrumentation, l'exemple fictif `decouverte.pdf`.
- `docs/` : la page de présentation publique (GitHub Pages).
- `.github/workflows/` : compilation, tests unitaires et tests sur émulateur à chaque proposition.
- `THIRD_PARTY_NOTICES.md` : dépendances et notices, dont le lexique libre Grammalecte 7.7 (476 104 formes) utilisé par le correcteur OCR.

## Contributions

Travaux proposés en branche et pull request, avec tests. Pas de publication boutique ni de fusion automatique : chaque fusion est décidée par le mainteneur. La mise à disposition publique du dépôt n'accorde aucun droit sur des PDF tiers.
