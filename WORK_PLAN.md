# Work plan — icon, time picker, live tracking, Maps key, Redis

**Status 2026-09-01 (evening): A, B, C1 and C2 are done and on the device.**
Section 0 is **resolved** — the backend moved to `~/Projects/QuickPool`
and is readable again, so C4 and section 2 are unblocked. C3 still waits on the
consent answer. See the per-section "Done" notes below.

Written 2026-09-01. Companion to `LIVE_TRACKING_PLAN.md` and `PRODUCTION_TASKS.md`
Phase 5. Everything here is grounded in measurements of the current tree, not
guesses — where a number appears, it was measured.

**Billing constraint for phase 1: nothing in sections A, B, C1–C3 costs money.**
C2 *reduces* Directions spend by ~99%. The only billing-adjacent step is the
Cloud Console key/quota setup in section 1, which is left for you to do.

---

## 0. ~~Blocker~~ — RESOLVED: the backend was unreadable

> **Fixed 2026-09-01 (evening).** Option 1 was taken: the repo now lives at
> `/Users/abhuday/Projects/QuickPool`, with its git history intact
> (`695bd8a` still HEAD). Source, `pom.xml` and history all read fine, so every
> backend item below — the Redis line, V10, server-side Directions, STOMP
> SUBSCRIBE authorisation — is unblocked.
>
> `.claude/settings.local.json` and `CLAUDE.md` were updated to the new path.
> The directory is granted per-project through
> `permissions.additionalDirectories`, so no `--add-dir` flag is needed — but
> **Claude Code must be restarted** for writes to the new path to be allowed
> (reads already work; the running session's grant still names the old path).
>
> Worth separating, because it caused confusion: the *running* backend was never
> affected. `localhost:8080` answered `/actuator/health` with 200 throughout and
> the app talked to it normally. Only the **source files on disk** were blocked.

The original diagnosis, kept for reference:

`/Users/abhuday/Downloads/QuickPool` returned `EPERM: operation not permitted`
from every tool, sandboxed or not.

**Cause: macOS TCC, not file permissions.** macOS specially protects Desktop,
Documents and **Downloads**; an app may only read them if that *specific app*
has been granted access. Claude Code is currently running inside **Android
Studio's** built-in terminal (`/Applications/Android Studio.app` → zsh →
claude), and Android Studio has no Downloads grant. Earlier sessions ran from a
terminal app that did, which is why the backend was readable then. The Android
repo lives in `~/AndroidStudioProjects`, which is not a protected folder — hence
only the backend half fails.

Machine: macOS Monterey **12.7.6**. Menu names below are Monterey's; Ventura and
later renamed this pane to System Settings → Privacy & Security.

### Option 1 — move the backend out of Downloads (recommended)

No permission dialogs, no restart, and Downloads is a scratch folder some
cleanup tools empty automatically — not a place to keep a git repo.

```
mkdir -p ~/Projects
mv ~/Downloads/QuickPool ~/Projects/QuickPool
```

Then relaunch Claude Code with `--add-dir ~/Projects/QuickPool`, and
update the backend paths in `CLAUDE.md` to match.

### Option 2 — grant Android Studio access

**System Preferences → Security & Privacy → Privacy tab** → click the padlock
at bottom-left and authenticate → **Files and Folders** in the left list →
find **Android Studio** → tick **Downloads Folder**.

If Android Studio is not listed, use **Full Disk Access** in the same left list,
click **+**, and add `/Applications/Android Studio.app`.

Either way, **fully quit and reopen Android Studio** — the grant is read at
launch.

### Until then

~~Every backend item — the Redis line, the V10 migration, server-side Directions,
STOMP subscribe authorisation — is unbuildable.~~ No longer applies; see the
resolution note at the top of this section.

---

## 1. The `MAPS_API_KEY` question

Two keys, because the two Google APIs in use have opposite security properties:

|                                    | Maps SDK for Android | Directions (web service) |
|------------------------------------|----------------------|--------------------------|
| Restrictable to package + SHA-1     | **Yes**              | **No** (no app signature to check) |
| Safe to ship inside the APK         | **Yes**              | **No**                   |

- **Android key** — Application restriction: *Android apps*, package
  `com.quickpool.app`, SHA-1 of the debug cert **and** the Play App Signing cert
  (Google's fingerprint from the Play Console, not only your upload cert — Play
  re-signs your upload). API restriction: Maps SDK for Android only. This key
  shipping in the APK is fine **by design**: extracted, it is worthless, because
  a call from any other package is rejected. Keep it in `local.properties` /
  `BuildConfig` exactly as it is today.
- **Server key** — Directions only, IP-restricted to the backend host. Never
  enters the APK.

### Why this is currently broken

`network/DirectionsHelper.kt:19` calls the Directions web service **from the
device** using `BuildConfig.MAPS_API_KEY`. A key that must work from a web
service cannot carry an Android app restriction — so the shipped key is
unrestricted, and anyone who unzips the APK can bill Directions to the account.
R8 does not obscure it; `unzip` + `strings` finds it in seconds.

That is the Play Store blocker, and it is only solvable by moving Directions
server-side (section C4).

### Staying bill-free

Enabling the APIs requires a billing account with a card, but overspend can be
made structurally impossible:

- **Per-API daily quota caps** in the Cloud Console — e.g. Directions 100/day.
  A cap is a hard stop, not a warning.
- **A budget alert** at ~$1 as a backstop against a leak or a runaway loop.

`ui/Geocoding.kt` uses the on-device `android.location.Geocoder` and needs no
key at all.

**Nothing in your Google Cloud console will be touched without you asking.**

---

## 2. The `Found 0 Redis repository interfaces` line — not an error

Spring Data's repository scanner reporting it found no `@RedisHash` entities.
Correct: Redis here is a **cache only** (`CacheConfig`, `RateLimiter`), so there
are no Redis repositories to find. Nothing is broken.

One line in `application.properties` silences it and shaves startup:

```properties
spring.data.redis.repositories.enabled=false
```

Unblocked as of the section 0 resolution. Zero cost.

---

## A. App icon — edges cut

**Root cause found by measurement. It is not a masking-config problem — the
adaptive icon is wired correctly. The artwork is too wide for the safe zone.**

At xxxhdpi the 432 px (= 108 dp) foreground has an opaque bounding box of
`(73,104)–(358,328)`:

- width **285 px = 71.25 dp**
- height 224 px = 56 dp
- centred correctly on both axes (bbox centre 215.5, 216 vs canvas centre 216)

Android only guarantees the inner **66 dp circle** (264 px at 4x) is visible.
The car + leaf overruns that circle by ~8% horizontally, and the bbox diagonal
(362 px) far exceeds 264 px. Circle and squircle launcher masks therefore clip
the left and right ends of the car. That is exactly the reported symptom.

### Done

Measured, fixed, verified on device. The min-enclosing-circle of the opaque
pixels was **167.1 px** on the 432 px canvas against a **132.0 px** safe radius —
a 27% overrun, which is exactly the clipping reported. All five foregrounds were
re-rendered from the master at the scale that puts every opaque pixel inside the
66 dp circle (1% spare for resampling bleed), and the legacy `mipmap-*` and
`ic_launcher-playstore.png` were regenerated from the same source so every
surface matches. Post-fix radii: 32.6/49.0/65.7/98.4/131.1 px against
33.0/49.5/66.0/99.0/132.0. Checked under circle, squircle and rounded-square
masks and on the device launcher — the car is whole.

### Steps

1. Re-render the foreground from `branding/logo_foreground_master.png`, scaling
   by the **measured** rule: shrink until no opaque pixel falls outside the
   66 dp safe circle. Test actual pixels, not the bounding box — a car's bbox
   corners are transparent, so a pixel test keeps the mark as large as it can
   honestly be rather than the over-conservative bbox-diagonal fit.
2. Regenerate all five `drawable-{m,h,xh,xxh,xxx}dpi/ic_launcher_foreground.webp`,
   re-centred on the 108 dp canvas.
3. Regenerate the legacy `mipmap-*/ic_launcher.webp` and `ic_launcher_round.webp`
   plus `app/ic_launcher-playstore.png` from the same source, so every surface
   matches.
4. Verify: render the result under circle / squircle / rounded-square masks
   side by side, before and after, and install on device.

### Left alone

- The missing `<monochrome>` layer — needs purpose-drawn art (see
  `LIVE_TRACKING_PLAN.md` section 5).
- `mipmap-anydpi` is not `mipmap-anydpi-v26`, which is unconventional but
  harmless: `minSdk = 26`, so every device that resolves it can inflate
  `<adaptive-icon>`. No change proposed.

---

## B. Time picker — AM/PM overlapping the dial

`ui/DateTimePickerField.kt:63-76`.

The Compose BOM is `2024.12.01` → Material3 **1.3.1**, which has no built-in
`TimePickerDialog`, so the dial is hand-hosted in a raw `Dialog`. Two things
squeeze it:

- `Dialog` defaults to `usePlatformDefaultWidth = true`, capping the width at
  the platform dialog width;
- inside that, `Surface(padding = 16.dp)` + `Column(padding = 24.dp)` consumes
  **80 dp** of horizontal space.

The M3 dial needs roughly 256 dp for the clock face **plus** a period column
beside it in the horizontal layout. It does not get that, so the AM/PM selector
draws over the clock face.

### Done

`DateTimePickerField.kt` now passes `usePlatformDefaultWidth = false`, trims the
nested padding from 80 dp to 32 dp, and adds the dial ⇄ keyboard toggle.

One thing the plan got half right: `TimePickerLayoutType.Vertical` fixes portrait
but *breaks* landscape, where stacking AM/PM above the dial runs off a 411 dp-tall
screen. The layout is therefore chosen by height — Vertical above 480 dp, and
Horizontal below it, which is fine now that the dialog finally has the width the
side-by-side layout always needed. The dialog is also capped at 560 dp wide (it
would otherwise stretch across a landscape screen) and at the screen height, with
the picker scrolling inside and the action row pinned outside the scroll so
"Done" is always reachable. Verified on device in portrait and landscape, and
with the keyboard fallback.

### Steps

1. `DialogProperties(usePlatformDefaultWidth = false)`, and trim the nested
   padding so the picker gets its natural size.
2. Pass `layoutType = TimePickerLayoutType.Vertical` explicitly — this puts
   AM/PM **below** the dial instead of beside it, which is what fits a
   phone-width dialog.
3. Add the standard dial ⇄ keyboard toggle (`TimeInput`), so a cramped or
   landscape screen always has a working path.

Verify on device at small width **and** in landscape — that is where it breaks.

---

## C. Live tracking

Follows the ordering in `LIVE_TRACKING_PLAN.md`: the two money bugs die first,
and the marker work lands on a route layer that no longer refetches.

### C1 — task 5.1, echo filter — **done**

Filtered on `broadcast.userId`, fetched from `/users/me` on entry, as planned.

One line, and it costs money today. `LiveLocationScreen` subscribes to the topic
it publishes to, so the driver's own position returns and fills the marker
titled "Passenger".

Drop broadcasts whose `userId` is your own — cleaner than filtering on `role`,
and it also survives passenger-to-passenger echo once C3 lands.

### C2 — task 5.2, fetch the route once and snap locally — **done**

Steps 1, 2, 3, 4 and 6 landed as written, plus interpolation from step 5. New
file `ui/RouteTracking.kt` holds the pure maths — `SnappedRoute.snap`,
`SpeedTracker`, `routeStatusText` — kept out of the composable per the project's
convention and covered by `RouteTrackingTest` (10 tests, all passing on the JVM).

Two departures from the plan, both deliberate:

- **`PolyUtil.locationIndexOnPath` was not used.** It returns a segment *index*
  and nothing else — not the projected point and not the perpendicular distance,
  and both are needed (one for the marker, one for the off-route trigger). The
  projection is done directly instead, equirectangular about the segment's
  latitude, which at intra-city segment lengths errs far below GPS noise.
- **The marker is interpolated but not rotated.** Rotating the default Google pin
  by bearing means nothing; rotation lands with `carMarker()` in C3.

`LaunchedEffect(myLocation, otherPartyLocation)` at `LiveLocationScreen.kt:70`
re-keys on every 4 s tick: **~900 billable Directions calls per hour per open
tracking screen**, and on the driver's side it is currently routing me→me
because of C1.

The route is static; only the position on it moves. App-side only, no backend
needed:

1. Route to the **ride's destination**, not the other party. `LiveLocationScreen`
   receives only `rideId` / `isDriver` (`ui/AppNav.kt:263`), so fetch the ride on
   entry to get `destinationLat`/`destinationLng` rather than widening the route
   string.
2. Fetch once, then `PolyUtil.locationIndexOnPath` to snap each fix onto the
   polyline — `com.google.maps.android:android-maps-utils:3.8.2` is already a
   dependency, no new library. Draw the consumed prefix grey, the remainder in
   primary. The car sits on the snapped point, so it tracks the road instead of
   jittering off it.
3. ETA from remaining polyline length ÷ recent average speed — no request. Not
   traffic-aware, which is fine for an intra-city pool ride.
4. Refetch only on a real trigger: perpendicular distance > ~150 m off the
   polyline for 3 consecutive fixes (genuine deviation, not GPS noise),
   destination changed, or a hard floor of one refetch per ~2 minutes.
5. Interpolate the marker between fixes over the 4 s interval and rotate by
   bearing, so the map reads as continuous.
6. While in here: `fusedLocationClient.lastLocation` inside a `while (true)` loop
   returns a cached, possibly stale or null fix. Switch to
   `requestLocationUpdates` with a callback.

### C3 — task 5.3, two-way location

`LiveLocationScreen.kt:56` gates the send loop on `isDriver`, so a passenger
never publishes and never even gets a "You" marker (`myLocation` is only assigned
inside that branch). The backend already relays both roles.

- Drop the `isDriver` guard.
- Replace the single `otherPartyLocation` with
  `Map<userId, Participant(latLng, role, lastSeenAt)>` — a ride can have several
  confirmed passengers, and today every broadcast overwrites one `LatLng`, so
  they would flicker on top of each other.
- Add `carMarker()` to `ui/MapPins.kt` beside `squareMarker` / `dotMarker`: a car
  vector drawn to a bitmap, rotated by bearing, `flat = true`,
  `anchor = 0.5f, 0.5f` so it turns with the vehicle rather than the camera.
- Passengers: the maps-compose `Circle` composable (radius in metres, so it
  scales with zoom), coloured from a fixed palette indexed by `userId` so a rider
  keeps the same colour for the whole trip.
- Staleness: fade a participant at ~30 s without a fix, remove at ~90 s.
  Otherwise a backgrounded passenger leaves a ghost circle forever.

#### Decision needed before building C3

Riders continuously broadcasting their position to the driver is a different
consent story from following the vehicle.

**Recommendation:** a passenger publishes **only while the tracking screen is
foregrounded**, with a visible "sharing your location with the driver" state and
an off switch — not a background service.

*Awaiting your answer. A/B/C1/C2 do not depend on it.*

### C4 — backend (unblocked — section 0 resolved)

- The Redis properties line from section 2.
- **V10 migration** (V9 is current; add, never edit applied files): store the
  encoded polyline + distance + duration on `ride_offers`, computed **once per
  ride** on the backend. The driver, every passenger, and the public
  `/api/v1/share/{token}` follower all read the same stored polyline. One
  Directions call per ride regardless of how many people watch — and it removes
  Directions from the client entirely, which is what makes the two-key split in
  section 1 possible.
- **Security fix**, noted in `LIVE_TRACKING_PLAN.md` but absent from the task
  list: `JwtHandshakeInterceptor` authenticates the handshake, but nothing
  authorises the STOMP **SUBSCRIBE**. Anyone with a valid token who knows a
  `rideId` can subscribe to `/topic/ride/{id}/location` and watch that vehicle.
  Add a `ChannelInterceptor` on SUBSCRIBE frames applying the same
  driver/passenger check `LocationController` already performs. This matters far
  more once passengers broadcast too.

### C5 — bookkeeping

Tick 5.1–5.4 off `PRODUCTION_TASKS.md` and update `LIVE_TRACKING_PLAN.md` as
each lands. **5.1 and 5.2 are ticked.**

### Not yet verified end-to-end

C1 and C2 compile, pass unit tests, and install, but the live tracking screen
needs a *started* ride with a *confirmed* booking on a second device to reach.
Only one device is attached, so the socket path has not been exercised since the
change. Worth doing across two handsets before calling it closed.

---

## Execution order

1. ~~**A** (icon) and **B** (time picker)~~ — done, verified on device.
2. ~~**C1** — one-line money bug.~~ — done.
3. ~~**C2** — the real cost fix, ~99% of Directions spend.~~ — done.
4. **C3** — after the consent question is answered.
5. **C4** — now unblocked; the backend is readable again.

## Open questions

1. **Passenger consent** (C3) — foregrounded-only publishing with a visible
   toggle (recommended), or something else?
2. **Backend access** (section 0) — move the repo to `~/Projects` (recommended),
   or grant Android Studio access to Downloads?
