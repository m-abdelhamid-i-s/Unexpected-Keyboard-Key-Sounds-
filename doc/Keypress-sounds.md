# Native keypress sounds

Enable **Settings → Behavior → Sound on keypress** (the category label may
be translated). It is off by default. Android's system touch sounds must also
be enabled, the phone must be in normal sound mode, and system sound volume
must be audible. Changes take effect through the existing preference listener;
no keyboard or application restart is required. Vibration settings are separate.

## Research and design

Reviewed upstream on 2026-10-18, at `c87d9b8fbd501197286eaafeae88278dfbdd56b7`:

- [#634](https://github.com/Julow/Unexpected-Keyboard/issues/634) requests sound
  as feedback alongside vibration and on devices without a vibration motor.
- [#823](https://github.com/Julow/Unexpected-Keyboard/issues/823#issuecomment-2543050340)
  explicitly recommends `AudioManager.playSoundEffect()` and differentiated
  effects. The suggested directional swipe sounds remain outside this version.
- [#1306](https://github.com/Julow/Unexpected-Keyboard/issues/1306) requests the
  system keypress effects and customization. This version uses native effects;
  custom assets and independent volume are out of scope.
- All three issues were open. Searches of upstream PR titles/bodies for sound,
  keypress and audio, and commit history for sound, found no matching
  implementation. The fork's starting commit matched upstream master. This
  does not establish whether someone is working on an unpublished branch.
- [Hacker's Keyboard's LatinIME](https://github.com/klausw/hackerskeyboard/blob/master/app/src/main/java/org/pocketworkstation/pckeyboard/LatinIME.java)
  uses `onPress` → `playKeyClick`, a ringer-mode guard, and the four native
  keypress effects. Its volume calculation and receiver architecture were not
  copied. Its volume-taking overload does not meet our system-touch-preference
  requirement.

`Keyboard2View` passes touch events to `Pointers`, which selects and modifies
keys. `onPointerDown` also fires for corner selection and long-press alternatives;
it is not a unique activation. Flag changes also include cancellation and fake
pointers used by automatic capitalization. `KeyEventHandler` later emits text,
editing operations, macros and key events, so character commitment is too late
and too broad a place for feedback.

The new `onPointerActivated` callback is emitted once for each non-sliding
pointer release and once for each repeat. It runs before the input handler;
there is no timer, worker queue, file decoding or per-key resource setup in the
sound helper. Release timing deliberately waits for the final selection: down
timing cannot both be immediate and remain silent if the gesture is cancelled.
Audio latency and the first sound after idle still need device measurement.

| Interaction | Feedback |
| --- | --- |
| Normal tap | One sound on release |
| Corner, circle or round-trip gesture | Final selected key on release; selection changes are silent |
| Shift, Ctrl, Alt, Fn, compose/dead keys | One standard sound on explicit release, including latch/unlatch and double-tap lock |
| Long-press modifier lock | One sound on release, no additional lock-time sound |
| Long-press alternative | Final alternative on release |
| Held character or Backspace | One sound per repeat, plus the existing final release action |
| Multiple fingers | Each released pointer/repeat has its own feedback; no global debounce |
| Cancellation/reset/unknown pointer/removed key | No new sound; already executed repeats cannot be undone |
| Automatic modifier changes | Silent |
| Cursor sliders | Silent, including initial movement and repeats |
| Physical keyboard, suggestion and clipboard panels | Unchanged; no sound added by this feature |

Moving outside the original key is part of Unexpected Keyboard's swipe system,
not automatically a cancellation. Feedback follows the existing recognizer's
selected value and does not change gesture acceptance or typing behavior.

`KeypressSound.effect_of_key` is pure selection logic. Space (editing action,
character or keycode), backward/forward deletion (including word deletion),
and Enter/editor actions use `FX_KEYPRESS_SPACEBAR`, `FX_KEYPRESS_DELETE`, and
`FX_KEYPRESS_RETURN`. Other explicit keys use `FX_KEYPRESS_STANDARD`. Nulls,
placeholders and sliders are silent; no invalid-key sound is introduced.

Playback lazily obtains `AudioManager`, checks the current ringer mode for each
enabled action, and uses **only `playSoundEffect(int)`**. This overload delegates
the touch-sound preference and effect volume to Android. Silent and vibrate
ringer modes are explicitly suppressed. Android controls routing, volume, DND
and other audio restrictions. No audio focus, volume changes, sound assets,
new permissions, telemetry, key logging or dependencies are added. Runtime
audio-service failures are swallowed; a later action can retry.

## Automated validation

Using the prepared JDK 17 / Android SDK 36 / NDK environment:

```sh
source /workspace/setup/env.sh
./gradlew --offline --no-daemon --no-watch-fs --no-configuration-cache \
  --console=plain --max-workers=4 test assembleDebug
bash /workspace/setup/check-generated.sh
git diff --check
```

`test` runs debug and release JVM suites. New tests exercise mapping, the
default and refreshed configuration, independent vibration, disabled sound,
ringer transitions, playback failures, taps, swipes, modifiers, cancellation,
repeat behavior, multi-touch and sliders. Small `test/android` doubles supply
only resources and a deterministic handler; they are not shipped in the APK.
The pointer tests execute the real pointer state machine, not a copy of it.
They do not validate Android audio services or Samsung hardware.

Validation on 2026-10-18: **43 tests passed in each variant** (86 executions,
zero failures/errors/skips), including 22 new tests per variant. All four
generated-file checks and `git diff --check` passed; `assembleDebug` succeeded.
The local APK verifies with v1 and v2 signatures and contains the expected
`juloo.keyboard2.debug` package, version 2.1.0 (56), min SDK 21 / target SDK 36.
`apksigner` also prints standard v1 warnings about META-INF dependency metadata;
the v2 signature verifies the APK as a whole.

Local APK SHA-256:
`62bf77e75dac322239781ad921f08c48074bf5cff0e3105edd98c43f383cbedf`.
Local signing-certificate SHA-256:
`010b0f9b92496fb77d817cec60c9f72fcf9ae4824bdd79c6e5d19649c65151ba`.
CI artifacts can have different hashes and certificates; verify the particular
APK you install.

The generated-file checks cover the layout list, layout warnings, compose data
and input-method XML, matching the existing CI checks.

## APK delivery and signing

The debug build uses **`juloo.keyboard2.debug`**, while the official app uses
`juloo.keyboard2`. Both can be installed at the same time with separate settings
and enabled-keyboard entries. This is a debug build, not a release-signed update
of the official keyboard. Its APK is in `build/outputs/apk/debug/`.

This branch enables the existing **Make Apk CI** workflow on pushes to
`feature/keypress-sounds`. To download a CI build:

1. Open [the fork's Actions page](https://github.com/m-abdelhamid-i-s/Unexpected-Keyboard-Key-Sounds-/actions/workflows/make-apk.yml).
2. Select a successful run for **feature/keypress-sounds**. If needed, select
   **Run workflow**, choose that branch, and run it. Do not select master.
3. Download **m-abdelhamid-i-s feature-keypress-sounds debug_apk** under
   Artifacts (GitHub sign-in required), extract the ZIP, and open the APK on the
   phone. Allow installation from that browser/file manager when prompted.
4. Enable **Unexpected Keyboard (debug)** in Android's keyboard management,
   select it, and enable the sound option. The official keyboard remains
   available as a fallback.

Android requires the same signing key to update an installed debug package.
The existing local `debug.keystore` is ignored by Git; preserve it in a private
backup. Do not commit it or its encrypted export. To reuse this exact key in
Actions, follow CONTRIBUTING.md's existing secret procedure:

```sh
gpg -c --armor --pinentry-mode loopback --passphrase debug0 --yes debug.keystore
```

Store the resulting ASCII-armored text in the fork's **Actions repository
secret `DEBUG_KEYSTORE`**, not an Actions variable or artifact. This is GPG
armored ciphertext, not a base64 encoding. The existing workflow restores it
using alias `debug` and password `debug0`. That default password is public;
confidential storage of the key is essential. No secrets are configured or
exported by this feature. For local builds the existing `DEBUG_KEYSTORE`,
`DEBUG_KEYSTORE_PASSWORD`, `DEBUG_KEY_ALIAS`, and `DEBUG_KEY_PASSWORD`
environment variables can select a privately stored keystore.

Without this secret, a fresh CI runner generates a new key, so successive APKs
may not update each other or the locally built APK. Before relying on updates,
compare certificate SHA-256 digests with:

```sh
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs path/to/app.apk
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging path/to/app.apk
sha256sum path/to/app.apk
```

Use the installed build-tools version if different. Losing the key means
uninstalling the debug package before installing the differently signed build;
that removes its settings. Preserve/export settings before doing so.

## Galaxy S24 Ultra manual checklist

No physical-device testing has been performed for this change.

- Install alongside the official keyboard; verify both entries are available.
  Before enabling sound, verify the debug keyboard behaves as before.
- Enable sound with system touch sounds on, normal sound mode and audible
  system volume. Type letters, numbers and punctuation slowly, then rapidly
  with two thumbs. Check accuracy, one sound per action, latency and first
  key after idle. Record Android/One UI version and perceived delay.
- Test space, Backspace, forward delete, word deletion, Enter, Search/Go/Send,
  Tab, Escape and layout switching in a notes app, browser and terminal.
  Confirm sounds correspond to the selected action; OEM effects may sound alike.
- Swipe to each corner, change direction, and try circle/round-trip gestures.
  Expect one final sound, no selection chatter. Try cancelled touches and
  empty/removed keys; cursor sliding should remain silent.
- Tap/latch/unlatch Shift, Ctrl and Alt; test double-tap lock, long-press lock,
  held-modifier chords and overlapping fingers. Automatic capitalization
  should not add clicks.
- Hold a letter and Backspace; compare emitted characters/deletions with
  sounds, including release. Disable key repeat and retest. Try a long press
  on the input-method switcher to select its alternate action.
- Toggle sound off/on and return immediately to typing. Independently test
  system vibration, custom vibration and zero vibration duration. Neither
  setting should enable or disable the other.
- With the keyboard still open, switch to silent and vibrate modes. Expect
  silence. Return to sound mode; disable system touch sounds and then set
  system volume to zero. Test DND under its configured system-sound policy.
- Play music while typing: check no pausing, volume changes or audio-focus
  interruption. Test speaker, headphones and Bluetooth; Bluetooth latency
  can differ substantially.
- Use a physical keyboard, switch applications, rotate, hide/show the IME and
  unlock the phone. Check for stuck modifiers, unexpected sounds or crashes.

## Limits and upstream review

Native effect timbre, availability, routing, DND policy and latency depend on
Android/OEM settings. This does not reproduce Samsung Keyboard's private sound
assets. The JVM tests cannot establish device latency, actual silence or music
mixing. Confirm these on the S24 Ultra before submitting upstream.

The implementation is deliberately small and independent of haptics. An
upstream review should agree on the release-time modifier/slider policy and
review the lightweight JVM doubles. Follow-up work can add translations,
device/instrumentation tests and latency measurements. Expand feedback to
other panels only as a separate design decision. The fork-only CI branch
trigger and delivery instructions can be omitted from an upstream PR. No PR
is submitted by this work.
