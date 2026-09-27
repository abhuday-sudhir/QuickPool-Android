# Session fixes — 2026-09-05

What changed in this session, across both repos.

## 1. Backend Directions calls were 500ing (missing `MAPS_SERVER_KEY`)

`DirectionsService` reads `app.maps.directions-key` from the `MAPS_SERVER_KEY` environment
variable, which was never set on this machine — so every `GET /api/v1/directions` call (used by
the app's `DirectionsHelper` for route + ETA on the live-tracking screen) logged an error and
returned 500, which on screen just looked like no route line ever drawing.

**Fix:** added a gitignored `.env` in the backend repo (`/Users/abhuday/Projects/QuickPool/.env`)
with `MAPS_SERVER_KEY` set to the same value as the Android client's `MAPS_API_KEY`
(`local.properties`). This is only possible because that key isn't restricted in the Google Cloud
console yet — see the caveat below.

To pick it up when running the jar directly:
```
set -a; source .env; set +a
java -jar target/quickpool-0.0.1-SNAPSHOT.jar --server.port=8081
```

**Caveat, not yet done:** PRODUCTION_TASKS.md 5.4 already called for two separate keys —
IP-restricted server key for Directions, package+SHA1-restricted client key for Maps SDK/Places —
set up in the Google Cloud console. Reusing one key for both is a stop-gap for local development
cost-effectiveness (avoids provisioning a second key before this needs to go anywhere near
production); once either key is restricted the other purpose stops working and they need to
actually split.

## 2. Two-way live location (PRODUCTION_TASKS.md 5.3)

Previously only the driver published their GPS over the ride's STOMP topic; passengers could see
the driver but the driver's "Passenger" marker slot was always empty, and a ride with several
passengers had nowhere to put more than one of them anyway.

**Backend** (`/Users/abhuday/Projects/QuickPool`):
- `BookingRepository.findByRideOfferIdAndStatusOrderByCreatedAtAsc` — deterministic booking order.
- `RideOfferService.getConfirmedPassengerOrder(rideOfferId, requesterId)` — confirmed passenger
  ids for a ride, oldest booking first; throws `ForbiddenException` unless the caller is the
  ride's driver or one of those passengers.
- `RideOfferController`: new `GET /api/v1/ride-offers/{id}/passengers` exposing the above. This is
  how every device on the same ride agrees that a given person is "Passenger 2" rather than each
  screen numbering them independently.
- No change to `LocationController` — it already classified `DRIVER`/`PASSENGER` and relayed both
  over `/topic/ride/{rideId}/location`; the gap was entirely on the client, which never published
  as a passenger and only kept a single `LatLng` for "the other party."

**App** (`app/src/main/java/com/quickpool/app/`):
- `ui/LiveLocationScreen.kt` — `otherPartyLocation: LatLng?` replaced with `driverLocation: LatLng?`
  plus `passengerLocations: Map<userId, LatLng>`, sorted from the broadcast's existing `role`
  field. The send guard became `if (isDriver || sharingEnabled) stompManager.sendLocation(...)`.
  `sharingEnabled` is a passenger-only toggle, **on by default**, shown as a `Switch` with a
  status line ("Sharing your location with the driver" / "Your location is hidden") right above
  the map. It lives entirely inside the same `DisposableEffect` as GPS collection, so leaving the
  screen (`onDispose`) stops publishing outright — no separate lifecycle wiring needed.
- `ui/MapPins.kt` — two new marker bitmaps in the same hand-drawn `Canvas` style as the existing
  `squareMarker`/`dotMarker`: `carMarker` for the vehicle, `numberedCircleMarker(number, color)`
  for each passenger, both anchored at centre like the rest.
- `network/RideApi.kt` — `passengerOrder(rideId)` calling the new endpoint.

Resulting behaviour: the vehicle (driver, whoever that is on a given screen) always renders as a
car icon; every other confirmed passenger currently broadcasting renders as a numbered circle —
"Passenger 1", "Passenger 2"... — the same number on every device, because the order comes from
the server rather than local insertion order. Your own dot is labelled with your own number too
("You (Passenger 2)").

**Cost:** none. Passenger broadcasting rides the existing STOMP/websocket connection to our own
backend — it is not a Google API call regardless of how many passengers are on a ride. Directions
usage is unchanged from 5.2 (one fetch per screen, re-fetched only on >150m drift over 3 fixes or
a changed destination, floored at once per 2 minutes) — 5.3 does not add a single extra billable
call.

## Verification

- `./gradlew assembleDebug` — clean.
- `./gradlew testDebugUnitTest` — clean (existing `RouteTrackingTest` etc. unaffected).
- `sh mvnw -q -DskipTests clean package` — clean.
- `sh mvnw test` — ran with `docker compose up -d postgres redis` (Docker Desktop wasn't running;
  started it for this). See the test run in this session's log for the final pass/fail count —
  previously (Docker down) 22/23 backend tests passed, with the one failure being the full
  `ApplicationTests.contextLoads` integration test unable to reach Postgres, unrelated to any
  change here.
- **Now done** — see "Device testing pass" below. That walk surfaced two real bugs neither
  compiling nor the unit suite could have caught.

## 3. Phase 3 — Correctness & scale (3.1 PostGIS search, 3.2 pagination, 3.3 N+1 queries)

Done in a later part of this same session — see `PRODUCTION_TASKS.md`'s Phase 3 entries for the
full detail (migration V10 adding `ride_offers.route` + a GiST index, `PageResponseDto`/`Slice`
pagination on notifications/bookings/rides, batched `findAllById` replacing per-row `findById`).
Backed by two new test classes (`RideOfferServiceTest`, `BookingServiceTest`) and `TESTING.md`
updated to match. Verified against a real backend: on-corridor search matched, off-corridor
didn't, `EXPLAIN ANALYZE` confirmed the new index gets used at scale, and all four paginated
endpoints returned the correct `{content, hasNext}` shape.

## 4. Home screen: the red "Getting your location…" text

Replaced with a pulsing black location-pin marker centered on the map itself (not the whole
screen) — `HomeScreen.kt`'s `PulsingLocationMarker`, an `rememberInfiniteTransition` radar pulse
behind a blinking `Icons.Default.LocationOn`. The red text now only ever fires for genuine
problems (permission off, GPS failed) — waiting on the first ordinary fix is silent apart from the
marker. The resolved "You" dot was also changed from green to black, for uniformity with the
pulsing pin — both now use a fixed `Color.Black` rather than a theme color, deliberately: see the
device-testing section below for why theme-driven marker colors bit us on `LiveLocationScreen`.

## 5. Device testing pass — two real bugs neither compiling nor unit tests could catch

Actually drove the app on a connected physical device (Pixel 7a) rather than trusting `assembleDebug`
and the test suite: registered a real account through the UI end-to-end (OTP, profile completion),
seeded 25 notifications to force pagination's "Load more" to appear (confirmed it fetches and
appends page 2 correctly), and — the one that mattered — created a driver + two passenger accounts
via the API, started a ride, and used two small Python scripts (`websocket-client`, hand-built STOMP
frames over the raw `ws://…/ws?token=` endpoint — no extra library needed) to simulate the driver's
and a second passenger's GPS while watching the real `LiveLocationScreen` on-device.

That surfaced two bugs that "5.3 is done" had shipped without ever actually working end-to-end:

- **`network/StompManager.kt` — dead on the first real reconnect, always.** `disposables` was a
  single `CompositeDisposable` reused for the object's whole lifetime. RxJava permanently kills a
  `CompositeDisposable` on `.dispose()`; anything `.add()`ed afterward is disposed immediately and
  silently. `LiveLocationScreen`'s `DisposableEffect(myUserId)` always reconnects once — `myUserId`
  starts `null` and resolves from an async `/users/me` call, re-keying the effect — so the *second*
  `connect()`'s topic subscription died the instant it was added. No broadcast (driver's or any
  passenger's) was ever delivered to a live screen, in any session, ever; outgoing `sendLocation()`
  kept working because it doesn't go through this composite, which is exactly why the bug was
  invisible from the sending side. Fixed: `connect()` now builds a fresh `CompositeDisposable` every
  call.
- **`ui/MapPins.kt` `carMarker` — invisible in dark theme.** The wheels were painted in the `ring`
  color, identical to the background circle, so they never showed regardless of theme. Separately,
  `LiveLocationScreen` passed `MaterialTheme.colorScheme.primary` as the car's fill color, and
  `primary` is `White` in this app's dark theme (`ui/theme/Theme.kt`) — the same white as the ring
  background — so the whole marker rendered as a blank white circle. Dark theme is the default here,
  so this hit every session. Fixed: wheels now use `fill` (matching body/cabin), and the vehicle
  color is a fixed `Color.Black` rather than a theme-dependent value.

Confirmed after both fixes, on-device: a correctly numbered green "2" circle for the second
passenger, and a legible black car icon for the driver, both updating as simulated fixes arrived.

## PRODUCTION_TASKS.md

Reorganized (previous session) into **Remaining work** / **Completed**; this session moved Phase 3
and Phase 5 to fully **Completed** — Phase 5's entry now also documents the two bugs above and how
they were found. Remaining work is just Phase 4 (ViewModels, error/retry handling) and the two
Google Cloud console key restrictions under 5.4.
