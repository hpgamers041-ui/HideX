# HideX — Native Android Privacy Vault

HideX is being rebuilt as a native Android app. The first native version is a PIN-gated local vault that encrypts files imported through Android's system file picker.

## Included in this initial build
- First-run PIN setup (6–12 digits) and PIN unlock
- Import files using Android's system document picker
- Encrypt file contents locally with AES-GCM and a PBKDF2-HMAC-SHA256-derived key
- Keep encrypted files in the app's private internal storage
- Export/decrypt files through Android's system save picker
- GitHub Actions workflow to build a debug APK artifact

## Build APK
1. Open the repository's **Actions** tab.
2. Run **Build HideX Android APK** with **Run workflow**, or push a change to the Android project.
3. When the workflow succeeds, open the run and download the `HideX-debug-apk` artifact.
4. You can also open this repository in Android Studio and build the debug APK locally.

## Important limitations and safety
- This is an initial implementation; it has not yet been validated on a physical Android device. Test with copies of files first.
- Keep original files until you have successfully exported and verified each encrypted copy.
- If you forget your PIN, the files cannot be decrypted. There is no PIN reset or recovery key in this version.
- Use a strong PIN and secure your phone. The PIN-derived key is not stored, but a short numeric PIN has a limited search space.
- This app does **not** hide or lock other installed apps. Android restricts that behavior and it requires a separate implementation with user-granted capabilities.
- Debug APKs are for testing, not a signed Play Store release.
