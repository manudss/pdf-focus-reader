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
