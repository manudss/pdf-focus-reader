# PDF Focus Reader

Prototype Android natif pour lire un PDF par zones, sans compte ni service distant.

1. Ouvrir un PDF ou utiliser l'exemple fictif intégré.
2. L'analyse locale propose des blocs de texte et d'image.
3. Toucher les blocs dans l'ordre souhaité. Les blocs choisis se numérotent.
4. Appuyer sur **Lire** : les blocs s'enchaînent dans un ruban à faire défiler au doigt. Le texte est cadré à la largeur de l'écran ; les images s'affichent entières. Les surfaces communes à plusieurs blocs sélectionnés ne sont affichées qu'une fois.
5. Les boutons de page et le choix du numéro restent disponibles pendant la détection locale.
6. En lecture, choisir **Original**, **Nettoyé** (nettoyage local et léger épaississement des lettres) ou **Texte OCR**. Ce dernier recompose les mots reconnus avec une vraie police ; Texte −/+ règle sa taille. La reconnaissance peut contenir des erreurs. Un bloc sans texte reconnu reste affiché en image. Les photos et le PDF original restent inchangés.
7. En Texte OCR, **OCR brut / OCR amélioré** permet de comparer la reconnaissance initiale et des corrections locales prudentes. Les morceaux arrivent progressivement ; le texte déjà affiché reste stable. Les chiffres, noms propres et mots ambigus ne sont pas corrigés par un dictionnaire seul. Les erreurs restent possibles et le retour au brut est immédiat.
8. Revenir à la page ou corriger les zones par appui long. **Ajuster le cadre** permet de glisser le bloc ou ses huit poignées, puis Valider ou Annuler. Après un nouveau tracé, cet ajustement est proposé avant l’ajout. Les cadres manuels restent exacts, sans extension automatique.
9. **Choisir les blocs à fusionner** ouvre une sélection violette, indépendante du parcours. Le nouveau bloc prend la place du premier bloc fusionné dans l’ordre existant ; les autres gardent leur ordre relatif. Une confirmation montre le nouvel ordre. Si aucun membre ne figurait dans le parcours, l’ajout à la fin est demandé explicitement.

## État : alpha, pas une application finale

La compilation et les tests locaux ne remplacent pas l'essai sur téléphone. Les mises en page irrégulières, les images en noir et blanc, les encadrés et les petits scans peuvent produire des blocs incorrects ou manquants. Le cadrage manuel reste disponible. L'application ne reconstitue pas automatiquement les articles et ne modifie pas le texte source. Le mode Texte OCR est une vue séparée, pas une transcription garantie. Un scan flou reste limité par sa résolution d'origine.

L'exemple est entièrement fictif. Aucun journal ou fichier personnel n'est inclus dans le dépôt ou dans les tests.

## Confidentialité

- Aucun compte, publicité, API distante ou permission Internet.
- OCR Tesseract embarqué avec modèles français et anglais, disponible sans téléchargement au premier lancement.
- Une copie du dernier PDF et ses cadres/parcours sont conservés dans le stockage privé de l'application, hors sauvegarde Android.
- Le menu permet de supprimer cette copie et le parcours sans toucher à l'original.
- Les mots reconnus servent au mode Texte OCR en mémoire sur l'appareil. Aucun mot reconnu n'est enregistré dans des journaux applicatifs, dans le parcours sauvegardé ou dans le dépôt.
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
- Couverture continue des lignes, conservation du bas des caractères, soustraction des zones communes et images entières.
- Reprise du parcours et isolation de la sauvegarde entre documents.
- Tests d'instrumentation Android sur document synthétique : OCR embarqué, ouverture de PDF, sélection, défilement continu et changement de page pendant une détection bloquée. Captures conservées avec les résultats CI.

## Dépendances

Voir [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). La mise à disposition publique du dépôt n'accorde pas de droits sur des PDF tiers.
