# Native keypress sounds — V2

Enable **Settings → Behavior → Sound on keypress** and choose **Keypress sound
volume** (0–100%, default 50%). The toggle remains off by default. V2 uses its
own enable/volume settings; Android's global touch sounds do not need to be
turned on by the application. Normal sound mode and audible system volume are
still required. Preferences refresh immediately, independently of vibration.

## Why V2

S24 Ultra testing of V1 found delayed clicks and a dependency on Samsung's
**Touch interactions** setting. V1 deliberately waited for release to classify
the final gesture. It also used `AudioManager.playSoundEffect(int)`, which
consults `SOUND_EFFECTS_ENABLED`. Those choices explain the reported behavior.
V2 changes that policy at the user's request.

`Pointers.onTouchDown` now emits `onPointerFeedback` once for an accepted
initial contact, immediately before the existing down/vibration callback.
The same feedback callback handles long-press repeats. Release does not emit
sound. It is intentionally separate from `onPointerDown`, which also fires
when a swipe or long press changes selection. No input timing, repeat schedule,
character commitment or haptic behavior changes.

| Interaction | V2 sound policy |
| --- | --- |
| Normal tap | One click at initial contact, none on release |
| Corner/circle/round-trip gesture | Initial center key's effect; selection and release add nothing |
| Shift/Ctrl/Alt/Fn/compose | One click on initial contact; latch/unlatch/lock add nothing |
| Long-press alternative | Initial key's effect; no second click when the alternative is selected |
| Held character/Backspace | Initial click plus one per repeat; final release action remains unchanged and adds no sound |
| Multiple fingers | One click per accepted initial contact; no global debounce |
| Cancel/reset | No additional sound; the initial click and earlier repeats cannot be undone |
| Empty/removed center or invalid touch | No initial sound; no late click if a corner is selected |
| Cursor sliding | Initial center click if valid (e.g. space); slider selection/movement/release are silent |
| Automatic modifiers / physical keyboard | No feedback added |

This trades final-key classification and cancellation silence for immediate
feedback. For example, swiping from a letter to Backspace initially sounds like
a letter; subsequent deletion repeats use the delete effect. A normal Backspace
tap still uses the delete effect at contact. There is no gesture prediction.

## Native audio and initialization

`KeypressSound` uses `playSoundEffect(effect, gain)`, passing the clamped app
percentage divided by 100. This is an app-local linear gain, not a system volume
change. Zero app volume/disabled sound avoid audio calls. Before playback it
checks normal ringer mode and nonzero `STREAM_SYSTEM` volume. Android retains
control of system mute, DND, routing and other restrictions. No permission,
audio focus, global setting modification, custom asset or dependency is added.
Runtime audio failures cannot interrupt input.

AOSP evidence:

- [AudioService](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/services/core/java/com/android/server/audio/AudioService.java):
  `playSoundEffect` checks the global preference; `playSoundEffectVolume` skips
  that preference but checks system-stream mute. `loadSoundEffects` waits for
  the audio service to finish loading.
- [SoundEffectsHelper](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/services/core/java/com/android/server/audio/SoundEffectsHelper.java):
  playback loads samples if necessary before playing; an unloaded pool can
  therefore add first-use delay. Android can unload effects when touch sounds
  are disabled.

V2 requests `loadSoundEffects()` when the keyboard view opens/resets with sound
and nonzero app volume. Since this API can block, preparation runs on a small
background task, at most one in flight per view. It never runs from touch or
repeat callbacks, never plays a warm-up click, and never unloads the shared
system effects. A subsequent opening retries preparation because the platform
may have unloaded them. Input does not wait for the task: a very early first
keypress can still encounter Android's normal lazy loading. Preparation and
executor failures are contained. No custom audio engine or per-key asynchronous
queue is introduced.

The explicit-volume overload and preparation are not a guarantee of playback
or latency on every OEM. Samsung may impose additional mute/routing policies;
verify V2 with Touch interactions OFF, especially after reboot or idle.

## Validation

Local V2 validation: 47 JVM tests passed in each of debug and release (94
executions, zero failures/errors/skips). All four generated-file checks and
`git diff --check` passed. The debug APK built and verified with v1/v2 signatures,
the preserved local certificate, and package `juloo.keyboard2.debug` (SDK 21–36).
Its SHA-256 is
`b2ec4b38ece11496f489b2d64f714b8a81540cef10327495acc5ca5b289627a3`.
The CI artifact has its own certificate/checksum report; do not substitute this
local APK hash when verifying the downloaded CI build.

The JVM suites exercise the real pointer state machine with small Android
resource/scheduler doubles. Coverage includes immediate feedback and no release
duplication, swipes, cancellation, modifiers, multi-touch, repeat classification,
sliders, default/live settings, gain clamping/zero volume, ringer/system-volume
gating, deferred/coalesced preparation and audio failures. They do not emulate
Android audio services or prove Samsung behavior.

Using the prepared JDK 17 / SDK 36 / NDK environment:

```sh
source /workspace/setup/env.sh
./gradlew --offline --no-daemon --no-watch-fs --no-configuration-cache \
  --console=plain --max-workers=4 test assembleDebug
bash /workspace/setup/check-generated.sh
git diff --check
```

## APK installation and signing

Open [Make Apk CI](https://github.com/m-abdelhamid-i-s/Unexpected-Keyboard-Key-Sounds-/actions/workflows/make-apk.yml),
select the latest successful **feature/keypress-sounds** run, and download
**m-abdelhamid-i-s feature-keypress-sounds debug_apk** (GitHub sign-in required).
Extract the ZIP. It contains the APK, its SHA-256 checksum, package information,
and a signing report comparing its certificate to V1 run `39160749536`.
If that old artifact expires, the comparison explicitly reports UNKNOWN.

Both versions use `juloo.keyboard2.debug`. The official `juloo.keyboard2`
installation coexists and should be kept available as a fallback.

**Do not assume V2 can update V1.** V1's CI log shows that it generated a fresh
debug keystore. That private key was not uploaded; it cannot be recovered from
the signed APK. A new CI run without the `DEBUG_KEYSTORE` secret generates a
different key. Check `signing-report.txt` before installing:

- MATCH: install as an update if the installed APK is that V1 CI build.
- DIFFERENT: first export/back up debug keyboard settings, then uninstall only
  **Unexpected Keyboard (Debug)** and install V2. Uninstallation removes its
  private settings. Re-enable the debug keyboard and restore/configure it.
- UNKNOWN, or a different installed build: compare certificates independently;
  a normal update attempt will refuse a mismatch rather than overwrite it.

The existing local `debug.keystore` is preserved and ignored by Git. Its public
certificate SHA-256 is
`010b0f9b92496fb77d817cec60c9f72fcf9ae4824bdd79c6e5d19649c65151ba`.
Keep a private backup. To use this same key for future CI builds, follow the
existing CONTRIBUTING.md procedure:

```sh
gpg -c --armor --pinentry-mode loopback --passphrase debug0 --yes debug.keystore
```

Store the resulting ASCII-armored ciphertext in the fork's **Actions repository
secret `DEBUG_KEYSTORE`**. Never commit the key/export or upload it as an artifact.
The default password `debug0` is public: confidential storage of the key matters.
No repository secrets were created or changed by V2. Until the secret is set,
CI keys remain temporary and should not be relied upon for future updates.
Switching from a temporary CI key to the preserved local key requires a one-time
reinstall unless certificates match. The existing local build environment
variables `DEBUG_KEYSTORE`, `DEBUG_KEYSTORE_PASSWORD`, `DEBUG_KEY_ALIAS`, and
`DEBUG_KEY_PASSWORD` can select another privately stored key.

## S24 Ultra checklist

No V2 physical-device verification has been performed by the implementation work.

1. Set Samsung **Touch interactions OFF**, normal sound mode and audible system
   volume. Enable keyboard sound at 50%. Check clicks begin on contact while
   holding the finger stationary; release must add no click.
2. Compare slow and fast two-thumb typing. Check the first key after opening,
   a long idle and reboot, and after toggling Touch interactions OFF/ON/OFF.
3. Try all corners, circles, round trips, cancelled touches and cursor sliding.
   Expect the initial center click with no selection/release chatter.
4. Test Shift/Ctrl/Alt taps, locks, long-press alternatives, overlapping fingers,
   repeated letters and held Backspace. Repeats should click separately.
5. Test space, Enter/Search/Go/Send, Backspace/forward delete and word deletion.
   A corner's first click may use its center's sound; repeats use the selection.
6. Toggle keyboard sound off/on; change its volume to 0/25/50/100. Verify
   vibration independently. No app/system restart should be needed.
7. With the keyboard open, test silent mode, vibrate mode, zero system volume
   and DND under its configured policy. Expect the applicable restrictions.
8. Play music; check no pause, focus interruption or global-volume change.
   Test speakers/headphones/Bluetooth, whose latency may differ.

## Upstream scope

V1 research reviewed [#634](https://github.com/Julow/Unexpected-Keyboard/issues/634),
[#823](https://github.com/Julow/Unexpected-Keyboard/issues/823) (the maintainer's
native AudioManager suggestion), [#1306](https://github.com/Julow/Unexpected-Keyboard/issues/1306),
and [Hacker's Keyboard](https://github.com/klausw/hackerskeyboard/blob/master/app/src/main/java/org/pocketworkstation/pckeyboard/LatinIME.java).
V2 keeps native effects and the existing architecture. OEM latency/timbre,
first-use preparation races and Bluetooth remain device-dependent. Device
measurements and translations are useful follow-ups. The fork-specific artifact
comparison/delivery changes can be omitted from an upstream PR. No merge or PR
is performed by this work.
