fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android deploy

```sh
[bundle exec] fastlane android deploy
```

Deploy app to Google Play Store

Usage:

  fastlane deploy

  fastlane deploy track:internal

  fastlane deploy track:production upload_metadata:false

  fastlane deploy upload_binary:false

  fastlane deploy bump_version:false

### android download_metadata

```sh
[bundle exec] fastlane android download_metadata
```

Download metadata from Google Play Store

### android bump_version

```sh
[bundle exec] fastlane android bump_version
```

Increment versionCode without building or deploying

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
