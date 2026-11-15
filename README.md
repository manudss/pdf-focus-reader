# PDF Focus Reader

Prototype Android natif pour lire un PDF par zones, sans compte ni service distant.

1. Ouvrir un PDF ou utiliser l'exemple fictif intégré.
2. L'analyse locale propose des blocs de texte et d'image.
3. Toucher les blocs dans l'ordre souhaité. Les blocs choisis se numérotent.
4. Appuyer sur **Lire** : le texte est cadré à la largeur de l'écran et parcouru par portions qui se chevauchent ; les images s'affichent entières.
5. Revenir à la page ou corriger les zones par appui long. Ajouter un bloc en traçant un rectangle ; diviser ou fusionner les cadres si nécessaire.

## État : alpha, pas une application finale

La compilation et les tests locaux ne remplacent pas l'essai sur téléphone. Les mises en page irrégulières, les images en noir et blanc, les encadrés et les petits scans peuvent produire des blocs incorrects ou manquants. Le cadrage manuel reste disponible. L'application ne reconstitue pas automatiquement les articles et ne modifie pas le texte source. Un scan flou reste limité par sa résolution d'origine.

L'exemple est entièrement fictif. Aucun journal ou fichier personnel n'est inclus dans le dépôt ou dans les tests.

## Confidentialité

- Aucun compte, publicité, API distante ou permission Internet.
- OCR Tesseract embarqué avec modèles français et anglais, disponible sans téléchargement au premier lancement.
- Une copie du dernier PDF et ses cadres/parcours sont conservés dans le stockage privé de l'application, hors sauvegarde Android.
- Le menu permet de supprimer cette copie et le parcours sans toucher à l'original.
- Aucun mot reconnu n'est enregistré dans des journaux applicatifs ni dans le dépôt.
- Limite d'import de cette alpha : 250 Mo. Les pages sont rendues à la demande, pas toutes chargées en mémoire.

## Développement

Android 8.0+ (API 26), JDK 17, SDK Android 35. Kotlin, vues Android natives, PdfRenderer et Tesseract4Android.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest  # appareil ou émulateur connecté
```

L'APK de développement se trouve dans `app/build/outputs/apk/debug/`. Ce n'est pas un APK de production : sa signature est une signature de test. Ne jamais commiter une clé de signature. Les mises à jour d'APK produits sur des machines différentes peuvent nécessiter une désinstallation, qui efface la copie locale et les parcours.

Les travaux sont proposés en branche et pull request. Pas de publication boutique ni de fusion automatique.

## Tests

- Ordre choisi, retrait/renumérotation, zones imbriquées.
- Couverture continue des portions de texte, chevauchement, rotation et images entières.
- Reprise du parcours et isolation de la sauvegarde entre documents.
- Tests d'instrumentation Android sur document synthétique : OCR embarqué, ouverture de PDF, sélection et lecture. Captures conservées avec les résultats CI.

## Dépendances

Voir [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). La mise à disposition publique du dépôt n'accorde pas de droits sur des PDF tiers.
