# Publication boundary

Publish only this SDK directory, never its parent or the sibling dashboard. No Git repository or remote has been created here.

Keep sample/google-services.json, local.properties, .env files, keystores, build outputs and Gradle caches out of version control. The example JSON is deliberately unusable; obtain your own client configuration from Firebase. Do not add any Firebase Admin Service Account file. Review ignored/untracked files before publication; ignore patterns do not remove already tracked files.

Internal module IDs stay :core, :sdk, :sample. Package names and Java APIs are unchanged. SDK source and Gradle dependencies work without the dashboard. The sample alone optionally communicates with your configured telemetry server. A raw SDK AAR does not embed core or Firebase; a future Maven release must publish dependency metadata for both modules. Publication is not configured or performed by this reorganization.

Retained technical docs include historical device observations. Current build commands are in LOCAL-SETUP.md and docs/TESTING.md. Local screenshots and device/server logs are held in the private dashboard archive instead.
