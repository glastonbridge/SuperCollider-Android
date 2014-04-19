# SuperCollider-Android

SuperCollider is copyright James McCartney and many different authors
Published under the terms of the GNU GPL, version 2 or later. Some code is GPL3+ so the overall bundle will typically be GPL3+.


SuperCollider-Android port by Alex Shaw and Dan Stowell.
http://github.com/glastonbridge/SuperCollider-Android

## Build Instructions (OS X)
1. Install the Android SDK. See https://developer.android.com/sdk/installing/index.html. Optionally, install Eclipse, although the instructions that
follow describe buildling from the command line.
2. If building from the command-line, install Apache ant. If homebrew is installed:

      ```
      brew install ant
      ```
3. This project is uses the Crystax NDK rather than the standard Android NDK. Download it from https://www.crystax.net/android/ndk.php. Extract and follow the installation instructions in docs/INSTALL.html.
4. For convenience, add the location of the NDK to your PATH (optional).
5. From the root SuperCollider-Android project directory:

        ```bash
        ndk-build
        # or /path/to/ndk-build if this isn't on your PATH
        ```
   This will build the native modules.
6. From the same directory:

        ```bash
        android update project -p . --target [target_number]
        # where target_number is a one of your installed android targets
        # these can be viewed with:
        android list
        ```
   This will generate an ant build.xml in the root directory.
7. Build the project itself:

        ```bash
        ant debug
        ```
8. Install on a connected device or emulator:

        ```bash
        adb install -r bin/SuperColliderActivity-debug.apk # -r here forces a reintsall if the apk already exists on the target
        ```
9. Eclipse and release build left as excercises for the reader.
10. Happy hacking!