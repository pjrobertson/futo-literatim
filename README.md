# LiteratIM Keyboard

The goal is to make a good modern keyboard that stays offline and doesn't spy on you. This keyboard is a fork of [LatinIME, The Android Open-Source Keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME), with significant changes made to it.

Check out the [LiteratIM Keyboard website](https://keyboard.futo.org/) for downloads and more information.

The code is licensed under the [FUTO Source First License 1.1](LICENSE.md).

## Issue tracking and PRs

Please check the GitHub repository to report issues: [https://github.com/futo-org/android-keyboard/](https://github.com/futo-org/android-keyboard/)

The source code is hosted on our [internal GitLab](https://gitlab.futo.org/keyboard/latinime) and mirrored to [GitHub](https://github.com/futo-org/android-keyboard/). As registration is closed on our internal GitLab, we use GitHub instead for issues and pull requests.

Due to custom license, pull requests to this repository require signing a [CLA](https://cla.futo.org/) which you can do after opening a PR. Contributions to the [layouts repo](https://github.com/futo-org/futo-keyboard-layouts) don't require CLA as they're Apache-2.0

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

### Command Line Building
1. Then make sure you have Java <20  (Java 20+ not supported at time of writing [[ref](https://docs.gradle.org/8.2/userguide/compatibility.html)])
2. Download and install Android command line tools from [here](https://developer.android.com/studio#command-line-tools-only), OR if you want to use Android Studio then download that [here](https://developer.android.com/studio)
3. For command line tools, unzip and move the *contents* of the folder to `~/.android/cmdline-tools/latest/` - note: the `latest` here is important. (For macOS: `~/Library/Android/cmdline-tools/latest/`)
4. Accept all the licenses with `cd FOLDER && yes | ./cmdline-tools/latest/bin/sdkmanager --licenses

You can then open the project in Android Studio and build it that way, or use gradle commands:
```
./gradlew assembleUnstableDebug
./gradlew assembleStableRelease
```

> [!TIP]
> For a full list of tasks, run `./gradlew tasks`


### Android Studio Building
1. Download [Android Studio](https://developer.android.com/studio)
2. Install and download SDKs, platform tools etc.

> [!TIP]
> For faster builds, you can build the apk just for your device's platform by adding the following into `build.gradle` file inside `android.defaultConfig`:
> ```
>    ndk {
>        // Specify the ABI filters to build for - options include 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'
>        abiFilters 'arm64-v8a'
>    }
> ```

### Release / Package Builds

From Android Studio, go to `Build > Select Build Variant...` then from the 'Build Variants' section choose the required variant.

> [!NOTE]
> The default `unstablDebug` variant apk won't build, because the `compileUnstableDebugAndroidTestKotlin` task is currently failing. Try `stableDebug`/`stableRelease` or `playstoreRelease`

From the command line, run:

`./gradlew assembleStable`

*Signed Builds:* set up your keystore first either in Android Studio with `Build > Generate Signed App Bundle` or APK... or by creating the `keystore.properties` file. [More info here](https://developer.android.com/studio/publish/app-signing#secure-shared-keystore).


## Adding the .sqlite file to the project

1. After creating your sqlite file, zip it and copy it to the `java/assets/sqlite` folder. **THIS STEP IS REQUIRED!**

## Testing

**Futo tests** - You can run things like `./gradlew connectedAndroidTest` to run test (see all tests with `./gradlew tasks`). However tests seem to be failing right now