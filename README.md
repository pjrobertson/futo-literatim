# LiteratIM Keyboard

The goal is to make a good modern keyboard that stays offline and doesn't spy on you. This keyboard is a fork of [LatinIME, The Android Open-Source Keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME), with significant changes made to it.

Check out the [LiteratIM Keyboard website](https://keyboard.futo.org/) for downloads and more information.

The code is licensed under the [FUTO Source First License 1.1](LICENSE.md).

## Layouts

If you want to contribute layouts, check out the [layouts repo](https://github.com/futo-org/futo-keyboard-layouts).

## Building

When cloning the repository, you must perform a recursive clone to fetch all dependencies:
```
git clone --recursive https://gitlab.futo.org/keyboard/latinime.git
```

If you forgot to specify recursive clone, use this to fetch submodules:
```
git submodule update --init --recursive
```

1. Then make sure you have Java 21 (not Java 24)
2. Download and install Android command line tools from [here](https://developer.android.com/studio#command-line-tools-only), OR if you want to use Android Studio then download that [here](https://developer.android.com/studio)
3. For command line tools, unzip and move the *contents* of the folder to `~/.android/cmdline-tools/latest/` - note: the `latest` here is important. (For macOS: `~/Library/Android/cmdline-tools/latest/`)
4. Accept all the licenses with `cd FOLDER && yes | ./cmdline-tools/latest/bin/sdkmanager --licenses

You can then open the project in Android Studio and build it that way, or use gradle commands:
```
./gradlew assembleUnstableDebug
./gradlew assembleStableRelease
```

