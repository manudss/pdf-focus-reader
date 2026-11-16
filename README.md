# PDF Focus Reader

Prototype Android natif pour lire un PDF par zones, sans compte ni service distant.

1. Ouvrir un PDF ou utiliser l'exemple fictif intégré.
2. L'analyse locale propose des blocs de texte et d'image.
3. Toucher les blocs dans l'ordre souhaité. Les blocs choisis se numérotent.
4. Appuyer sur **Lire** : les blocs s'enchaînent dans un ruban à faire défiler au doigt. Le texte est cadré à la largeur de l'écran ; les images s'affichent entières. Les surfaces communes à plusieurs blocs sélectionnés ne sont affichées qu'une fois.
5. Les boutons de page et le choix du numéro restent disponibles pendant la détection locale.
6. En lecture, choisir **Original**, **Nettoyé** (nettoyage local et léger épaississement des lettres) ou **Texte OCR**. Ce dernier recompose les mots reconnus avec une vraie police ; **Réglages** ouvre Texte −/+ pour sa taille, uniquement en OCR. La reconnaissance peut contenir des erreurs. Un bloc sans texte reconnu reste affiché en image. Les photos et le PDF original restent inchangés.
7. En Texte OCR, dans **Réglages**, **OCR brut / OCR amélioré** permet de comparer la reconnaissance initiale et des corrections locales prudentes. En mode amélioré, chaque bloc choisi est publié après reconnaissance de ses morceaux : une limite de traitement ne devient pas un paragraphe. Le texte déjà affiché reste stable. Les chiffres, noms propres et mots ambigus ne sont pas corrigés par un dictionnaire seul. Les erreurs restent possibles et le retour au brut est immédiat.
8. Revenir à la page ou corriger les zones par appui long. **Ajuster le cadre** permet de glisser le bloc ou ses huit poignées, puis Valider ou Annuler. Après un nouveau tracé, cet ajustement est proposé avant l’ajout. Les cadres manuels restent exacts, sans extension automatique.
9. Le bouton **Fusionner** (ou l’appui long **Choisir les blocs à fusionner**) ouvre une sélection violette, indépendante du parcours. Le nouveau bloc prend la place du premier bloc fusionné dans l’ordre existant ; les autres gardent leur ordre relatif. Une confirmation montre le nouvel ordre. Si aucun membre ne figurait dans le parcours, l’ajout à la fin est demandé explicitement.

**Ordre** regroupe Annuler dernier, Effacer l’ordre et Recentrer. Les commandes principales ont un pictogramme et un libellé. Les réglages de lecture se replient pour laisser plus de place au document.

## État : alpha, pas une application finale

La compilation et les tests locaux ne remplacent pas l'essai sur téléphone. Les mises en page irrégulières, les images en noir et blanc, les encadrés et les petits scans peuvent produire des blocs incorrects ou manquants. Le cadrage manuel reste disponible. L'application ne reconstitue pas automatiquement les articles et ne modifie pas le texte source. Le mode Texte OCR est une vue séparée, pas une transcription garantie. Un scan flou reste limité par sa résolution d'origine.

L'exemple est entièrement fictif. Aucun journal ou fichier personnel n'est inclus dans le dépôt ou dans les tests.

## OCR amélioré (alpha 0.9)

- Rendu de la région depuis le PDF, plafonné à 3 millions de pixels / 2400 pixels sur le grand côté. Un agrandissement ne répare pas un scan d’origine flou.
- Tesseract français précis et second reconnaisseur PP-OCRv5 Latin mobile, exécuté avec ONNX Runtime. Les lignes sont découpées à partir de Tesseract : les analyses de mise en page ne sont pas indépendantes.
- Reconstruction des lignes et paragraphes selon leur géométrie. Une césure n’est réunie qu’avec un mot attesté par le lexique ; les composés inconnus restent inchangés. Les retraits et espaces verticaux restent des indices imparfaits.
- Suggestions orthographiques locales, sans analyse grammaticale. Un mot déjà valide, un nombre ou un nom propre n’est pas remplacé sur simple accord statistique. Les divergences ne sont pas toutes corrigées.
- Les changements sont marqués dans la lecture. Toucher un repère permet de consulter le brut, les lectures disponibles et l’extrait PDF, puis de garder le brut. Les choix sont sauvegardés par empreinte, sans recopier le texte du document dans le parcours.
- Deux moteurs qui s’accordent peuvent avoir tort. Les tests emploient des PDF fictifs et ne garantissent pas la qualité sur un journal particulier. Les mesures de temps sur émulateur ne sont pas celles d’un téléphone.
- Le second moteur alourdit l’APK et consomme de la mémoire pendant l’OCR amélioré. Aucun téléchargement de modèle n’est nécessaire.

## Améliorations OCR en préparation

Code expérimental, pas une mise à jour installable publiée :
- Correcteur orthographique indépendant : 476 104 formes françaises issues du lexique libre Grammalecte 7.7, interrogées par Morfologik. Il ne fait pas d'analyse grammaticale.
- Plus de poids à Paddle lorsque sa lecture est attestée et la lecture Tesseract ne l'est pas, même si Tesseract se déclare confiant. Un mot déjà valide reste protégé. Les coupures/concaténations sont alignées sans décaler la suite. Les chiffres et les noms propres connus restent protégés.
- Césures : tiret de fin de ligne avec grand blanc traité comme indice supplémentaire, sous contrôle de la géométrie et du dictionnaire. Les composés attestés gardent leur tiret. Espaces Unicode normalisés.
- Le filtre Nettoyé reste une vue image. Les quatre lectures brut/nettoyé de Tesseract/Paddle font l'objet d'essais séparés, pas de quatre passes systématiques dans cette version du code.
- Aucun changement d'identifiant ou de signature de livraison n'est prévu ici. Les APK de tests CI ne sont pas des mises à jour compatibles garanties.

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
