# Privacy Policy — Speed Launcher

**Last updated:** [data di pubblicazione]

Speed Launcher ("the app") is developed and operated by Cheip Studio (Eugenio [Cognome], Sesto Calende, Italy, P.IVA 03891670121).

This privacy policy explains how the app handles your data.

## Data we collect

**None.**

Speed Launcher does not collect, transmit, or store any personal data on any server. The app has no network access for analytics, advertising, or tracking. Everything happens locally on your device.

## Permissions explained

### Query installed apps (`<queries>` in manifest)
Required to display the list of installed apps in the launcher home and drawer. This is core launcher functionality.

### Bind App Widget (`BIND_APPWIDGET`)
Required to display widgets you choose to add to your home screen. Standard launcher permission.

### Expand status bar (`EXPAND_STATUS_BAR`)
Used to allow expanding notifications from the launcher (planned feature).

### Notification listener (`BIND_NOTIFICATION_LISTENER_SERVICE`)
**This permission is OFF by default and requires explicit user activation in Settings → Notifications → Notification access.**

When activated, Speed Launcher uses this permission **exclusively to count the number of active notifications per app**, in order to display a small dot ("notification badge") on the corresponding app icon.

Specifically, Speed Launcher:
- Does NOT read the content (text, title, sender, body) of any notification
- Does NOT store any notification data
- Does NOT transmit any notification data anywhere
- Does NOT log or analyze notifications
- Only counts the active notifications per package name

You can revoke this permission at any time from Android system Settings.

## Data storage

All app preferences (which apps are pinned to home, etc.) are stored locally on your device using Android SharedPreferences. They are included in standard Android backup/restore so they can move with your device. No data leaves your device.

## Children's privacy

The app is not directed at children under 13 specifically, but contains no mechanism that would allow data collection regardless of user age.

## Changes to this policy

If this policy changes, the new version will be posted at this URL with an updated "Last updated" date.

## Contact

For privacy questions: [tua email]
Website: https://cheipstudio.org
