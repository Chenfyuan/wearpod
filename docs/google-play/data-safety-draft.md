# Data Safety Draft

This is a practical draft based on the current codebase and relay flow. Review it carefully in Play Console before submission.

## Working assumptions from the current app
- No account creation
- No ads SDK
- No analytics SDK
- No location collection
- No contacts collection
- No health or fitness data collection
- No precise device identifier collection beyond normal app operation

## Data handled directly by the app
### Device or app activity
- Purpose: core app functionality only
- Shared: no
- Collected by developer backend: no dedicated telemetry pipeline in the current app code

### Audio files downloaded from podcast feeds
- Purpose: offline playback on device
- Shared: no
- Collected by developer backend: no

## Data handled through the relay service
### RSS URLs and OPML content
- Flow: used only during phone-assisted import/export
- Purpose: app functionality
- Shared: no
- Retention: short-lived session data only

### Session identifiers and short codes
- Purpose: connect the watch and phone for one import/export action
- Shared: no
- Retention: short-lived session data only

## Draft declaration direction
- Data is not sold
- Data is not used for advertising
- Data is not used for profiling
- Data handling is limited to app functionality
- Temporary relay data should be marked consistently with the privacy policy

## Review before submission
- Re-check whether Play Console expects relay-based OPML content to be declared as `Files and docs` or `Other user-generated content`
- Re-check whether temporary session metadata should be declared under identifiers or app activity
- Confirm that the privacy policy language matches the final Play declaration
