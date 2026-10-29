# HideX — Android Privacy Vault + Optional Custom Launcher

HideX is a native Android app with a PIN-protected encrypted file vault and an optional custom home launcher. The launcher can hide selected apps from **HideX's own app drawer**.

## App hiding / launcher setup
1. Install the HideX debug APK.
2. Open Android **Settings → Apps → Default apps → Home app** (wording varies by phone).
3. Select **HideX** as the Home app. On some phones, press the Home button and choose HideX.
4. In HideX Launcher, tap the settings icon.
5. Tick the apps you want to hide, then tap **Done / Save**.
6. Return to the drawer. The selected apps no longer appear in HideX's drawer.

**Important:** This only filters apps inside HideX Launcher. It does not uninstall, disable, or securely lock the selected apps. They may still appear in other launchers, Android Settings, recent apps, notifications, or system search. To see them again in HideX, open the management screen and untick them. You can switch back to your previous Home app in Android Settings.

## Encrypted file vault
- First-run PIN setup (6–12 digits) and PIN unlock
- Import files using Android's system document picker
- Encrypt file contents locally with AES-GCM and a PBKDF2-HMAC-SHA256-derived key
- Keep encrypted files in the app's private internal storage
- Export/decrypt files through Android's system save picker

## Build APK
1. Open the repository's **Actions** tab.
2. Open the latest **Build HideX Android APK** run.
3. When the workflow succeeds, download the `HideX-debug-apk` artifact.
4. Extract the ZIP and install `app-debug.apk`.

## Important limitations and safety
- The APK must still be tested on a physical device; Android launcher behavior can vary by manufacturer.
- Keep original files until you have successfully exported and verified each encrypted copy.
- If you forget your vault PIN, files in the encrypted vault cannot be decrypted. There is no PIN reset or recovery key in this version.
- Debug APKs are for testing, not a signed Play Store release.
