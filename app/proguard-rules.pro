# R8 rules for Scontrino. Library rules shipped in their AARs (Room, Compose, zxing-cpp...)
# are applied automatically; these cover the libraries that don't ship any.

# Native code looks Java classes, methods and fields up by name (JNI FindClass/GetMethodID/
# GetFieldID: TessBaseAPI.onProgressValues, OpenCV's CvException...), which R8 can't see.
# Keep these packages whole: they are small, and renaming or removing anything breaks OCR at
# runtime, not at build time.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
-keep class org.opencv.** { *; }

# Readable stack traces in crash reports.
-keepattributes SourceFile,LineNumberTable
