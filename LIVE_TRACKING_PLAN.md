# Live tracking, Maps billing & branding — plan

Companion to `PRODUCTION_TASKS.md` Phase 5. Sections 1–4 expand tasks 5.1–5.4;
section 5 is new. Written 2026-09-01.

Findings are grounded in the current code: `ui/LiveLocationScreen.kt`,
`network/StompManager.kt`, `network/DirectionsHelper.kt`, `ui/MapPins.kt`, and
backend `controller/LocationController.java`, `config/WebSocketConfig.java`,
`config/JwtHandshakeInterceptor.java`.

`com.google.maps.android:android-maps-utils:3.8.2` is already a dependency, so
`PolyUtil` is available — no new library needed for the route work.

---

## 1. Two-way location: car for the driver, coloured circles for passengers

**The backend is already done.** `LocationController` classifies `DRIVER`/`PASSENGER`,
authorises passengers via their `CONFIRMED` booking, broadcasts
`{userId, role, lat, lng, timestamp}`, and deliberately persists only the driver's fix.
All the missing work is client-side.

- **Publish from both sides.** `LiveLocationScreen.kt:56` gates the send loop on
  `isDriver`, so a passenger never publishes and never even gets a "You" marker
  (`myLocation` is only assigned inside that branch). Drop the guard.
- **`otherPartyLocation` must become a map.** One ride can have several confirmed
  passengers, and today every broadcast overwrites that single `LatLng` — passengers
  would flicker on top of each other. Replace with
  `Map<userId, Participant(latLng, role, lastSeenAt)>`.
- **Filter your own echo** (task 5.1). The screen subscribes to the topic it publishes
  to, so the driver's own position returns and fills the marker titled "Passenger".
  Drop broadcasts whose `userId` is your own — cleaner than filtering on `role`, and it
  also stops passenger-to-passenger self-echo once both sides publish.
- **Markers.** Add `carMarker()` to `MapPins.kt` beside `squareMarker`/`dotMarker`: a
  car vector drawn to a bitmap, rotated by bearing (computed from the previous fix),
  with `flat = true` and `anchor = 0.5f, 0.5f` so it turns with the vehicle rather than
  the camera. For passengers use the maps-compose `Circle` composable (radius in metres,
  so it scales with zoom) or `dotMarker`, coloured from a fixed palette indexed by
  `userId.hashCode()` so a rider keeps the same colour for the whole trip.
- **Staleness.** Fade a participant at ~30 s without a fix, remove at ~90 s. Otherwise a
  passenger who backgrounds the app leaves a ghost circle on the map forever.
- **While in here:** `fusedLocationClient.lastLocation` inside a 4 s `while (true)` loop
  returns a cached, possibly stale or null fix. Switch to `requestLocationUpdates` with
  a callback.

### Decision needed before building

Riders continuously broadcasting their position to the driver is a different consent
story from following the vehicle. **Recommendation:** a passenger publishes only while
the tracking screen is foregrounded, with a visible "sharing your location with the
driver" state and an off switch — not a background service.

### Security note (not in the task list)

`JwtHandshakeInterceptor` authenticates the handshake, but nothing authorises the STOMP
**subscribe**. Anyone with a valid token who knows a `rideId` can subscribe to
`/topic/ride/{id}/location` and watch that vehicle. Add a `ChannelInterceptor` on
`SUBSCRIBE` frames applying the same driver/passenger check `LocationController` already
does. This matters more once passengers broadcast too.

---

## 2. The route recalculation problem

**Diagnosis (task 5.2).** `LaunchedEffect(myLocation, otherPartyLocation)` at
`LiveLocationScreen.kt:70` re-keys on every 4 s tick, so it fires a **billable Directions
call every 4 seconds per open screen** — ~900/hour, roughly $4–5/hour per tracking
screen. On the driver's side it is currently routing me→me because of the echo bug.

**Core insight: the route is static; only the position on it moves.** Fetch once, then
move locally.

1. **Fetch once per ride, not per fix.** The route should target the ride's
   `destinationLat/Lng`, not the other party. `LiveLocationScreen` doesn't receive them
   today — it takes only `rideId` and `isDriver` (`AppNav.kt:40`). Fetch the ride on
   entry rather than widening the route string.
2. **Snap, don't refetch.** On each fix use `PolyUtil.locationIndexOnPath` to find the
   nearest vertex, then draw the consumed prefix in grey and the remainder in the primary
   colour. The car sits on the snapped point, so it tracks the road instead of jittering
   off it.
3. **ETA without a request.** Remaining polyline length ÷ recent average speed, or decay
   the original leg duration. Not traffic-aware — fine for the accuracy an intra-city
   pool ride needs.
4. **Refetch only on a real trigger:** perpendicular distance > ~150 m off the polyline
   for 3 consecutive fixes (genuine deviation, not GPS noise), destination changed, or a
   hard floor of at most one refetch every ~2 minutes.
5. **Interpolate for smoothness.** Animate the marker between fixes over the 4 s interval
   and rotate by bearing; the map reads as continuous even though data arrives every 4 s.

---

## 3. Doing it properly: move Directions server-side

Steps 1–5 cut cost by ~99%, but each device still fetches its own copy. The better shape
(task 5.4) is to compute the route **once per ride on the backend** and store the encoded
polyline + distance + duration on `ride_offers` (new migration, **V10** — V9 is current;
add, never edit applied files). The driver, every passenger, and the public
`/api/v1/share/{token}` follower all read the same stored polyline.

One Directions call per ride regardless of how many people watch — and it removes
Directions from the client entirely, which is what makes section 4 solvable.

---

## 4. The Maps key shipping in the APK

**It ships, and it always will.** `BuildConfig.MAPS_API_KEY` and the
`com.google.android.geo.API_KEY` manifest placeholder both land as plain strings in the
APK; `unzip` + `strings` finds them in seconds, and R8 does not obscure them. Any key the
Maps SDK needs on-device is public by definition. **The mitigation is restriction, not
secrecy** — and the two APIs in use differ crucially:

| | Maps SDK for Android | Directions (Web Service) |
|---|---|---|
| Restrictable by package name + SHA-1 | **Yes** | **No** — web services have no app signature to check |
| Safe to ship in the APK | Yes | **No** |
| Billing | Map loads are free | ~$5 / 1000 calls |

So: **two keys.**

- **Android key** — Application restriction: Android apps, package `com.quickpool.app`,
  SHA-1 of *both* the debug cert and the Play upload/signing cert (Play App Signing
  re-signs your upload, so you need Google's fingerprint from the console, not only
  yours). API restriction: Maps SDK for Android only. Safe to ship — extracting it gets
  an attacker nothing, because a call from any other app is rejected.
- **Server key** — lives only in backend config, IP-restricted to the backend host, API-
  restricted to Directions. Never enters the APK.

This is why section 3 is not optional for the Play Store: as long as `DirectionsHelper`
calls the web service from the device, the shipped key must work without app restriction,
and anyone who pulls it can bill Directions to the account with no cap.

Two smaller notes: `reverseGeocode` uses the on-device `android.location.Geocoder` and
needs no key at all; and set a Cloud billing budget alert plus per-API quota caps
regardless — the cheapest backstop against a leak or a runaway loop.

---

## 5. App logo — DONE (2026-09-01)

Wired from the custom PNG (`Gemini_Generated_Image_35wajy35wajy35wa.png`). It could not
be used as-is: the rounded-square plate and white surround were baked into the raster, so
feeding it whole to an adaptive icon would have produced a rounded square inside the OEM
mask plus a white halo.

What was done: flood-keyed the white surround and then the dark-green plate off the
artwork, dropped the antialiased outline ring left behind (identified by its bounding box
hugging the old plate border), leaving leaf + car on transparency. The plate colour
`#276D39` was sampled from the original and became the background layer.

Generated and installed:
- `drawable-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.webp` — 108 dp canvas, art at 66%
- `drawable/ic_launcher_background.xml` — solid `#276D39`
- `mipmap-*dpi/ic_launcher.webp` + `ic_launcher_round.webp` — regenerated legacy rasters
- `app/ic_launcher-playstore.png` — flat 512x512, no alpha, for the store listing
- `branding/logo_foreground_master.png` — keyed master, so the set can be regenerated

Removed: the stock `drawable/ic_launcher_foreground.xml` vector and all ten stock `.webp`
rasters. Verified present in the built APK.

### Left open, deliberately

- **No `<monochrome>` layer.** Flattening this mark merges the leaf into the car body and
  only the wheels survive — it reads as a black blob. Android falls back to the
  full-colour icon for Android 13+ themed icons. Needs a purpose-drawn silhouette.
- **The three passenger figures blur at 48 px.** The leaf-and-car outline carries the
  identity, so this is acceptable, but a simplified variant for the low-density buckets
  would be better if the icon matters.
- **In-app branding is untouched.** `ui/theme/Color.kt` is a deliberate neutral palette
  with `AccentGreen #06C167`; the icon's `#276D39` is darker because the accent is too
  bright to sit behind a white car at launcher size. Whether the brand green shifts to
  match the logo is a separate decision. (`values/colors.xml` still holds unused stock
  purples from the project template.)

---

## Open questions

1. **Passenger consent** — foregrounded-only publishing with a visible toggle
   (recommended), or something else?
2. ~~Logo artwork~~ — resolved; icon shipped, see section 5.

## Suggested order

1. **5.1** echo filter — one-line bug, costs money today
2. **Section 2** — fetch-once route + local snapping
3. **5.3** — two-way publishing, participant map, car + circle markers
4. **5.4 / section 3** — server-side Directions, V10 migration, two-key split
5. ~~Section 5 — logo~~ (done)

Sequenced so the two money bugs die first, and the marker work lands on a route layer
that no longer refetches.
