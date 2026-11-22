// Slim OpenCV (core + imgproc + Java bindings) built from source by build-opencv.sh.
// The Java sources and native libraries in src/main are generated: don't edit them by hand.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.opencv"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
