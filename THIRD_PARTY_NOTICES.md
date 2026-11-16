# Third-party notices

The application uses the following components under their own licenses. No third-party publication content is included.

- Android framework / SDK: https://source.android.com/license
- Kotlin standard library, Apache-2.0: https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt
- Tesseract4Android 4.9.0, Apache-2.0: https://github.com/adaptech-cz/Tesseract4Android
- Tesseract OCR 5.5.1, Apache-2.0: https://github.com/tesseract-ocr/tesseract
- Leptonica 1.85.0, BSD-style: https://github.com/DanBloomberg/leptonica/blob/master/leptonica-license.txt
- libjpeg 9f, IJG license: https://www.ijg.org/files/README
- libpng 1.6.48, PNG Reference Library License: https://github.com/pnggroup/libpng/blob/libpng16/LICENSE
- French and English trained models, tessdata_fast, Apache-2.0: https://github.com/tesseract-ocr/tessdata_fast
- AndroidX annotations, Apache-2.0: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt

Test-only components include JUnit (EPL-1.0), Robolectric (MIT), and AndroidX Test (Apache-2.0). They are not application features.

Model files are bundled to avoid a first-use network download. Their SHA-256 hashes are recorded in MODEL_CHECKSUMS.txt. License texts for runtime native components are included in app/src/main/assets/licenses/ and accessible from the application help menu.

Le modèle français précis et son lexique dérivé proviennent de tessdata_best (Apache-2.0) : https://github.com/tesseract-ocr/tessdata_best. Le lexique est extrait de fra.lstm-word-dawg ; aucune donnée de document utilisateur ne le complète.

Second local recognizer in enhanced OCR (line segmentation shared with Tesseract, not an independent layout detector): ONNX Runtime Android1.23.2, MIT, https://github.com/microsoft/onnxruntime/tree/v1.23.2 ; PP-OCRv5 Latin mobile recognizer, PaddleOCR Apache-2.0, https://github.com/PaddlePaddle/PaddleOCR . ONNX conversion distributed by RapidOCR3.10.0 from https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.10.0/onnx/PP-OCRv5/rec/latin_PP-OCRv5_rec_mobile.onnx . The bundled character dictionary is read from model metadata. No user data is used to train or extend this model.

Independent local spelling: Morfologik Speller2.1.9 (BSD-3-Clause), https://github.com/morfologik/morfologik-stemming/ . French inflected forms derived from Grammalecte7.7 lexicon (MPL-2.0), https://grammalecte.net/dic/lexique-grammalecte-fr-v7.7.zip . Dictionary source is available at that URL; reproducible conversion scripts/compile-french-dictionary.py is included. The spelling engine is Morfologik, not Grammalecte's full grammar checker. All variants are accepted; source dictionary is independent of OCR training and has no user additions.
