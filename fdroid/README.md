# F-Droid build notes

Draft for the fdroiddata merge request (metadata/com.pepesantos.scontrino.yml). Not used by
the app build itself.

## Dependencies

- Tesseract4Android comes from JitPack and zxing-cpp from Maven Central. Both are trusted
  Maven repositories in the F-Droid Inclusion Policy, so neither needs building from source
  (the OCR app org.totschnig.ocr.tesseract ships Tesseract4Android from JitPack the same way).
- OpenCV is the exception: its native libraries are committed in `opencv/src/main/jniLibs`,
  and F-Droid rejects prebuilt binaries inside the source repo. The recipe deletes them and
  rebuilds them with `opencv/build-opencv.sh` from the OpenCV and KleidiCV sources, given as
  srclibs so the build downloads nothing.
- The script is reproducible: built in two different directories, the libraries are
  byte-identical (source paths are mapped with `-ffile-prefix-map`, and the build info and
  data-search paths OpenCV embeds are replaced with fixed text). This needs exactly NDK
  `29.0.14206865`; another NDK gives working but different binaries.

## Recipe

```yaml
Builds:
  - versionName: '1.0'
    versionCode: 1
    commit: v1.0
    subdir: app
    sudo:
      - apt-get update
      - apt-get install -y cmake ninja-build python3
    srclibs:
      - OpenCV@4.14.0
      - KleidiCV@26.03
    scandelete:
      - opencv/src/main/jniLibs
    ndk: r29
    prebuild:
      - ABIS="arm64-v8a armeabi-v7a" OPENCV_SOURCE_DIR=$$OpenCV$$ KLEIDICV_SOURCE_DIR=$$KleidiCV$$
        ../opencv/build-opencv.sh
    gradle:
      - yes
```

If fdroiddata doesn't have them yet, the srclibs go in `srclibs/OpenCV.yml` and
`srclibs/KleidiCV.yml`:

```yaml
RepoType: git
Repo: https://github.com/opencv/opencv.git
```

```yaml
RepoType: git
Repo: https://gitlab.arm.com/kleidi/kleidicv.git
```

## Before submitting

- Release is signed with the debug key for local testing (`app/build.gradle.kts`): remove that
  so F-Droid gets an unsigned release to sign.
- Keep `OPENCV_VERSION` / `KLEIDICV_VERSION` in the script and the srclib versions above in sync.
- Check that the F-Droid build server offers NDK r29; otherwise build with the closest one it has
  (`NDK_VERSION` in the script only sets the default path).
