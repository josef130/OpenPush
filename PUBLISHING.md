# Publication boundary

Publish only this SDK directory, never its parent or the sibling dashboard. No Git repository or remote has been created here.

Keep sample/google-services.json, local.properties, .env files, keystores, build outputs and Gradle caches out of version control. The example JSON is deliberately unusable; obtain your own client configuration from Firebase. Do not add any Firebase Admin Service Account file. Review ignored/untracked files before publication; ignore patterns do not remove already tracked files.

Internal module IDs stay :core, :sdk, :sample. Existing Java APIs remain supported.
Maven publication is now configured for core and sdk, with a root OpenPush POM
depending on sdk; sdk exports core and Firebase Messaging. Local verification uses
0.1.0-local in build/verification-maven. No remote publication was performed and
the existing 0.1.0 tag must not be replaced. Automatic enrollment is optional and
uses app resource configuration. A raw AAR alone still does not embed dependencies.

`jitpack.yml` uses JDK 17 and publishes all three Maven publications with the
requested build version. Its remote execution has not been verified. Obtain owner
approval for a new release before publishing. Keep automatic-test.properties and
the private dashboard outside the public repository.

Retained technical docs include historical device observations. Current build commands are in LOCAL-SETUP.md and docs/TESTING.md. Local screenshots and device/server logs are held in the private dashboard archive instead.
