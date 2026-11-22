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
#
# Optional environment (used by the F-Droid recipe, see fdroid/README.md):
#   OPENCV_SOURCE_DIR    existing OpenCV $OPENCV_VERSION checkout (default: git clone)
#   KLEIDICV_SOURCE_DIR  existing KleidiCV $KLEIDICV_VERSION checkout (default: git clone)
#   ABIS                 space-separated ABIs (default: arm64-v8a armeabi-v7a x86_64)
#   ANDROID_NDK / ANDROID_NDK_HOME  NDK location
# With both source dirs set nothing is downloaded.
set -euo pipefail

OPENCV_VERSION=4.14.0
# Must match KLEIDICV_SRC_COMMIT in OpenCV's hal/kleidicv/kleidicv.cmake.
KLEIDICV_VERSION=26.03
NDK_VERSION=29.0.14206865
MIN_SDK=24
read -r -a ABIS <<< "${ABIS:-arm64-v8a armeabi-v7a x86_64}"
MODULES=core,imgproc,java

MODULE_DIR=$(cd "$(dirname "$0")" && pwd)
SDK_DIR=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
NDK_DIR=${ANDROID_NDK:-${ANDROID_NDK_HOME:-$SDK_DIR/ndk/$NDK_VERSION}}
WORK_DIR=$(mkdir -p "${1:-${TMPDIR:-/tmp}/opencv-slim-build}" && cd "${1:-${TMPDIR:-/tmp}/opencv-slim-build}" && pwd)
SRC_DIR=${OPENCV_SOURCE_DIR:-$WORK_DIR/opencv-$OPENCV_VERSION}
KLEIDICV_DIR=${KLEIDICV_SOURCE_DIR:-$WORK_DIR/kleidicv-$KLEIDICV_VERSION}
STRIP=$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip

[ -d "$NDK_DIR" ] || { echo "NDK not found: $NDK_DIR (android sdk install ndk/$NDK_VERSION)" >&2; exit 1; }

if [ ! -d "$SRC_DIR" ]; then
  git clone --depth 1 --branch "$OPENCV_VERSION" https://github.com/opencv/opencv.git "$SRC_DIR"
fi
# Cloned here instead of letting CMake download it, so a build can run fully offline from
# prepared sources (F-Droid srclibs).
if [ ! -d "$KLEIDICV_DIR" ]; then
  git clone --depth 1 --branch "$KLEIDICV_VERSION" https://gitlab.arm.com/kleidi/kleidicv.git "$KLEIDICV_DIR"
fi
grep -q "KLEIDICV_SRC_COMMIT \"$KLEIDICV_VERSION\"" "$SRC_DIR/hal/kleidicv/kleidicv.cmake" ||
  { echo "OpenCV $OPENCV_VERSION expects another KleidiCV: update KLEIDICV_VERSION" >&2; exit 1; }

for abi in "${ABIS[@]}"; do
  build=$WORK_DIR/build-$abi
  echo "===== $abi"
  # Assert messages embed source paths (__FILE__): map them to fixed names so the library is
  # byte-identical wherever it's built (F-Droid checkouts live elsewhere).
  prefix_maps="-ffile-prefix-map=$SRC_DIR=opencv -ffile-prefix-map=$KLEIDICV_DIR=kleidicv"
  prefix_maps+=" -ffile-prefix-map=$build=build -ffile-prefix-map=$NDK_DIR=ndk"
  cmake -S "$SRC_DIR" -B "$build" -G Ninja \
    -DOPENCV_EXTRA_FLAGS="$prefix_maps" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_DIR/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" \
    -DANDROID_PLATFORM="android-$MIN_SDK" \
    -DKLEIDICV_SOURCE_PATH="$KLEIDICV_DIR" \
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
  # cv::getBuildInformation() embeds the whole CMake summary (build dir, flags, host, date):
  # replace it with a fixed text so it doesn't depend on the machine. CMake rewrites the file
  # on every configure, so this runs after it each time.
  printf '"\\nOpenCV %s for Scontrino (%s, NDK %s, API %s): %s. See opencv/build-opencv.sh\\n"\n' \
    "$OPENCV_VERSION" "$abi" "$NDK_VERSION" "$MIN_SDK" "$MODULES" > "$build/modules/core/version_string.inc"
  # Same for the build/install dirs that findDataFile() would search (samples data on a PC,
  # never used on Android).
  data_config=$build/opencv_data_config.hpp
  sed -i -e "s|\"$build/install\"|\"/opencv-install\"|" -e "s|\"$build\"|\"/opencv-build\"|" \
    -e 's|^    ".*/"$|    "../opencv/"|' "$data_config"
  if grep -qE "$WORK_DIR|$SRC_DIR|$build" "$data_config"; then
    echo "$data_config still has local paths: update build-opencv.sh" >&2; exit 1
  fi
  # ANDROID_PROJECTS_BUILD_TYPE=GRADLE only enables the Java bindings: we build the native
  # library and the generated sources, never OpenCV's own (outdated) Gradle project.
  ninja -C "$build" opencv_java opencv_java_android_source_copy
done

echo "===== copying into $MODULE_DIR"
java_src=$WORK_DIR/build-${ABIS[0]}/opencv_android/opencv/src/org/opencv
java_dst=$MODULE_DIR/src/main/java/org/opencv
rm -rf "$java_dst"
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

# Only the ABIs built now are replaced, so a partial ABIS run keeps the others.
for abi in "${ABIS[@]}"; do
  rm -rf "$MODULE_DIR/src/main/jniLibs/$abi"
  mkdir -p "$MODULE_DIR/src/main/jniLibs/$abi"
  "$STRIP" --strip-unneeded -o "$MODULE_DIR/src/main/jniLibs/$abi/libopencv_java4.so" \
    "$WORK_DIR/build-$abi/jni/$abi/libopencv_java4.so"
done

ls -l "$MODULE_DIR"/src/main/jniLibs/*/
