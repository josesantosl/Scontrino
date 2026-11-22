#!/usr/bin/env bash
# Builds a slim OpenCV for Android with only what Scontrino uses (core, imgproc and the
# Java bindings) and copies the result into this module:
#   src/main/java/org/opencv/...                 generated Java bindings
#   src/main/jniLibs/<abi>/libopencv_java4.so    native library (C++ runtime linked statically)
#
# The official AAR bundles every module in one ~25 MB library per ABI; this one is a fraction
# of that. Rerun after bumping OPENCV_VERSION, or if ReceiptImagePreprocessor starts using a
# module that isn't in BUILD_LIST (it would fail at compile time: the Java class won't exist).
#
# Usage: opencv/build-opencv.sh [work-dir]
# Needs git, cmake, ninja, python3, a JDK and NDK $NDK_VERSION in the Android SDK.
set -euo pipefail

OPENCV_VERSION=4.14.0
NDK_VERSION=29.0.14206865
MIN_SDK=24
ABIS=(arm64-v8a armeabi-v7a x86_64)
MODULES=core,imgproc,java

MODULE_DIR=$(cd "$(dirname "$0")" && pwd)
SDK_DIR=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
NDK_DIR=${ANDROID_NDK:-$SDK_DIR/ndk/$NDK_VERSION}
WORK_DIR=$(mkdir -p "${1:-${TMPDIR:-/tmp}/opencv-slim-build}" && cd "${1:-${TMPDIR:-/tmp}/opencv-slim-build}" && pwd)
SRC_DIR=$WORK_DIR/opencv-$OPENCV_VERSION
STRIP=$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip

[ -d "$NDK_DIR" ] || { echo "NDK not found: $NDK_DIR (android sdk install ndk/$NDK_VERSION)" >&2; exit 1; }

if [ ! -d "$SRC_DIR" ]; then
  git clone --depth 1 --branch "$OPENCV_VERSION" https://github.com/opencv/opencv.git "$SRC_DIR"
fi

for abi in "${ABIS[@]}"; do
  build=$WORK_DIR/build-$abi
  echo "===== $abi"
  cmake -S "$SRC_DIR" -B "$build" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_DIR/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" \
    -DANDROID_PLATFORM="android-$MIN_SDK" \
    -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_LIST="$MODULES" \
    -DBUILD_SHARED_LIBS=OFF \
    -DBUILD_FAT_JAVA_LIB=ON \
    -DBUILD_ANDROID_PROJECTS=OFF \
    -DANDROID_PROJECTS_BUILD_TYPE=GRADLE \
    -DBUILD_ANDROID_EXAMPLES=OFF \
    -DBUILD_KOTLIN_EXTENSIONS=OFF \
    -DBUILD_TESTS=OFF \
    -DBUILD_PERF_TESTS=OFF \
    -DBUILD_EXAMPLES=OFF \
    -DBUILD_DOCS=OFF \
    -DWITH_OPENCL=OFF \
    -DWITH_ANDROID_MEDIANDK=OFF \
    -DWITH_IPP=OFF \
    -DWITH_ITT=OFF \
    -DWITH_JPEG=OFF -DWITH_PNG=OFF -DWITH_TIFF=OFF -DWITH_WEBP=OFF -DWITH_OPENJPEG=OFF \
    -DWITH_JASPER=OFF -DWITH_OPENEXR=OFF -DWITH_IMGCODEC_GIF=OFF -DWITH_PROTOBUF=OFF
  # ANDROID_PROJECTS_BUILD_TYPE=GRADLE only enables the Java bindings: we build the native
  # library and the generated sources, never OpenCV's own (outdated) Gradle project.
  ninja -C "$build" opencv_java opencv_java_android_source_copy
done

echo "===== copying into $MODULE_DIR"
java_src=$WORK_DIR/build-${ABIS[0]}/opencv_android/opencv/src/org/opencv
java_dst=$MODULE_DIR/src/main/java/org/opencv
rm -rf "$java_dst" "$MODULE_DIR/src/main/jniLibs"
mkdir -p "$java_dst/android"
cp -r "$java_src/core" "$java_src/imgproc" "$java_src/utils" "$java_dst/"
# Kotlin extensions (Mat.at, Mat * Mat) aren't used.
rm -f "$java_dst"/core/*.kt
# org.opencv.android also holds OpenCV's camera views (one needs videoio): keep only the loader
# and the Bitmap <-> Mat conversion.
cp "$java_src/android/OpenCVLoader.java" "$java_src/android/StaticHelper.java" \
  "$java_src/android/Utils.java" "$java_dst/android/"
# Utils.loadResource decodes images with imgcodecs, which isn't built: drop it.
python3 - "$java_dst/android/Utils.java" <<'EOF'
import re, sys
path = sys.argv[1]
src = open(path).read()
src = src.replace("import org.opencv.imgcodecs.Imgcodecs;\n", "")
src, n = re.subn(r"    public static Mat loadResource\(.*?(?=    /\*\*\n     \* Converts Android Bitmap to OpenCV Mat)",
                 "", src, flags=re.S)
if n != 1 or "Imgcodecs" in src:
    sys.exit("Utils.java changed upstream: update the loadResource removal in build-opencv.sh")
open(path, "w").write(src)
EOF

for abi in "${ABIS[@]}"; do
  mkdir -p "$MODULE_DIR/src/main/jniLibs/$abi"
  "$STRIP" --strip-unneeded -o "$MODULE_DIR/src/main/jniLibs/$abi/libopencv_java4.so" \
    "$WORK_DIR/build-$abi/jni/$abi/libopencv_java4.so"
done

ls -l "$MODULE_DIR"/src/main/jniLibs/*/
