# No database encryption of our own, and no Android backup

The database is a plain Room (SQLite) file. It relies on Android's file-based encryption of app-private storage, which on the Pixel 8 is tied to the lock screen and the Titan M2 chip. We do not add SQLCipher. Its only extra protection is against a rooted or compromised phone while it is unlocked, and there the attacker can read the screen or the key anyway. In exchange it would cost a native library of several MB, slower queries, another key to manage, and JVM tests that run against a different SQLite than the real one. With the app locked behind the phone's biometrics or PIN (like a banking app), Android backup is turned off entirely: no cloud backup and no device-to-device transfer, because a Google Drive backup would take the data off the phone. Secrets (bank signing keys, consent tokens) live in Android Keystore and never in the database or an export.

## Consequences

- Moving to a new phone needs an explicit export and import; bank links are made again there.
- If the threat model changes (e.g. sharing the phone), adding SQLCipher means a one-off migration of the database file.
