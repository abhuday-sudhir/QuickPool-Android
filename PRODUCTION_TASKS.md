# QuickPool — road to production

Everything here is buildable **without spending money**. Items that need paid
services are parked at the bottom so they don't get lost.

Status: `[ ]` todo · `[~]` in progress · `[x]` done

---

## Remaining work

### Phase 3 — Correctness & scale
- [~] **3.5 Tests** — backend: 122 passing across 17 classes (the original `RatingService`,
  `SafetyService`, `VehicleService`, `ImpactService`, `DirectionsService`, plus `RideOfferService`
  and `BookingService` now covering their full lifecycle — `bookRide`/`acceptBooking`/
  `rejectBooking`/`cancelBooking`, `createRideOffer`/`cancelRideOffer`/`startRide` — and ten new
  classes: `OtpService`, `TokenService`, `JwtService`, `EmailVerificationService`,
  `AccountDeletionService`, `TripShareService`, `RideLifecycleService`, `SavedAddressService`,
  `DestinationService`, `RateLimiter`) · app: 15 passing (`Validation`, `TimeFormat`). Backend line
  coverage jumped from 21.7% to **67.0% overall** (`com.QuickPool.service` alone: 21.9% → 85.0%).
  Still to cover: controllers (0%, the one real gap left — nothing exercises Spring MVC routing/
  validation/exception-mapping in a fast test), config/filter (JWT filter, CORS, WebSocket —
  needs a `@WebMvcTest` slice, not plain Mockito), repository queries, `RouteTrackingTest` (10,
  already counted in 5.2). See `TESTING.md` for what each class actually verifies.

### Phase 4 — App architecture
- [ ] **4.1 ViewModels + repository** — state survives rotation/process death, no refetch storms
- [ ] **4.2 Error/retry handling** — consistent, offline-tolerant

---

## Completed

### Phase 1 — Trust & safety
The real gap for a carpooling app: strangers getting into cars together.

- [x] **1.1 Vehicle details** — done, backend + Account panel; shown on every search result
- [x] **1.2 Ratings & reviews** — done, backend + star dialogs for both driver and passenger
- [x] **1.3 Block users** — done, backend + overflow menu on ride cards and passenger rows
- [x] **1.4 Report users** — done, backend + reason-picker dialog
- [x] **1.5 Phone number privacy** — done; number withheld while PENDING, revealed only on CONFIRMED, never in search results
- [x] **1.6 Emergency contact + share trip** — done; expiring, revocable public link (no login needed to follow), contact panel in Account, OS share sheet
- [x] **1.7 Email verification** — done; 6-digit code via a pluggable `EmailSender` (logs in dev), verified badge in Account

> Phase 1 verified by `p1test.py` (27 checks) and `p16test.py` (27 checks) — all passing, plus walked on-device.

### Phase 2 — Security hardening
- [x] **2.1 Refresh token rotation + revocation** — done; single-use refresh tokens, reuse burns every session, logout revokes server-side
- [x] **2.2 Rate limiting** — done; Redis fixed-window on OTP request/verify and refresh, per-phone *and* per-IP, fails open
- [x] **2.3 Account deletion** — done; scrubs PII, frees the phone number, refuses mid-commitment, in-app dialog
- [x] **2.4 Kill cleartext HTTP** — done; `network_security_config.xml`, cleartext only for localhost/emulator
- [x] **2.5 Release build** — done; R8 + resource shrinking (22.8 MB → 3.8 MB), ProGuard rules, env-driven signing scaffold

> Phase 2 verified by `p2test.py` — 21 checks, all passing.

### Phase 3 — Correctness & scale
- [x] **3.1 PostGIS ride search** — the corridor check (both pickup and drop within 2km of a
  ride's origin-destination line) used to fetch every `ACTIVE` ride in the time window and run
  `GeoUtils.distancePointToSegmentMeters` against each one in Java. **Done:** migration V10 adds
  `ride_offers.route`, a `geography(LineString,4326)` column kept in sync by a DB trigger (not
  application code — every insert path gets it for free, including ones added later), with a GiST
  index. `RideOfferRepository.searchCorridor` is a native query doing
  `ST_DWithin(route, ..., 2000)` for both pickup and drop directly in Postgres; `RideOfferService`
  now only applies the block-list filter in Java afterward. `GeoUtils.distancePointToSegmentMeters`
  was deleted (no longer called from anywhere). Verified against a real backend: an on-corridor
  pickup/drop matched, one 1,000+ km away didn't, and `EXPLAIN ANALYZE` confirmed the planner picks
  `idx_ride_offers_route` over the existing status/time index once the table has enough rows for
  it to matter (checked by seeding 5,000 decoy rides).
- [x] **3.2 Pagination** — `/notifications`, `/bookings/mine`, `/bookings/requests`, and
  `/ride-offers/mine` (the lists that grow without bound over a user's lifetime — ride search stays
  unpaged, it's already bounded by the corridor + 3-hour window) now take `?page=&size=` and
  return a lean `PageResponseDto` (`content`, `hasNext`) instead of the full list every time.
  Deliberately not Spring's `Page<T>` — that serializes pageable/sort metadata the client never
  uses and runs an extra `COUNT(*)` per call; this wraps a `Slice` instead, one query per page.
  App side: `RideApi`/`NotificationApi` take `page`/`size` (defaulting to page 0, size 20 to match
  `@PageableDefault`), and `AlertsScreen`, `MyBookingsScreen`, `MyRidesScreen` (both its ride list
  and its booking-requests list) each track their own page/`hasNext` and show a `LoadMoreRow` —
  spinner while fetching, "Load more" button otherwise — when there's another page.
- [x] **3.3 Fix N+1 queries** — `BookingService.getMyBookings` and `getBookingRequestsForDriver`
  each called `rideOfferRepository.findById` once per row inside a `.map()` (the driver-side one
  also did the same for `userRepository`) — one extra round-trip per booking on the page. **Done:**
  both now batch with a single `findAllById` (and `getBookingRequestsForDriver` a single
  `userRepository.findAllById` too) per page, the same pattern `RideOfferService.search` already
  used for driver/vehicle lookups. Locked in by `BookingServiceTest` asserting `findById` is
  *never* called during either method.

### Phase 5 — Live tracking & Maps billing
Found while testing the ride socket across two devices. All four items below are done.

#### How the ride room works today

One `LiveLocationScreen`, opened by every party, told apart only by the `isDriver` flag it is
handed. Everyone in the ride subscribes to the same STOMP topic, `/topic/ride/{rideId}/location`,
and everyone — driver and passengers alike — now publishes to `/app/ride/{rideId}/location`.

| | Driver's screen | Passenger's screen |
|---|---|---|
| Vehicle marker | own GPS, car icon, titled **"You (driving)"** | driver's broadcast, car icon, titled **"Driver"** |
| Own dot | — (it *is* the vehicle) | own GPS, titled **"You (Passenger N)"** |
| Other passengers | numbered circle per confirmed passenger broadcasting | numbered circle per *other* confirmed passenger broadcasting |
| Route + ETA | yes | yes, once the driver's first fix lands |

The vehicle marker is the interpolated one: fixes arrive every 4s and the marker is animated
across that gap so it glides instead of teleporting. The route is snapped locally on every fix
(`ui/RouteTracking.kt`) — driven prefix grey, remainder in primary — and the ETA is remaining
length ÷ rolling average speed. None of that costs a request.

`otherPartyLocation` (a single `LatLng`) became `driverLocation` plus `passengerLocations`, a
`Map<userId, LatLng>` — the backend broadcast already carries `role`, so the client sorts each
fix into the right bucket without asking the server anything new. Passenger numbering ("1", "2",
"3"...) comes from `GET /api/v1/ride-offers/{id}/passengers`, which returns confirmed passenger
ids in booking order so the same person renders under the same number on every device. The echo
filter stays `broadcast.userId != myUserId` from `/users/me` — unchanged, and it now also keeps
two passengers from clobbering each other's marker.

#### Where the Maps money actually goes

Three different Google products, and only two of them are billed:

- **Map loads** (Maps SDK for Android) — free, unlimited. Panning the map costs nothing.
- **Places Autocomplete** — billed, but already batched into sessions by session tokens.
- **Directions** — billed per call, ~$5 per 1,000. This is the one worth guarding, which is what
  5.2 (fewer calls) and 5.4 (a key nobody can steal) were both about. Passengers publishing their
  own position for 5.3 costs nothing extra here — it rides the same STOMP/websocket connection
  the driver already uses, which is our own backend, not a Google API.

Reverse geocoding is on-device (`android.location.Geocoder`) and never reaches Google's billed API.

- [x] **5.1 Driver sees its own echo as "Passenger"** — `LiveLocationScreen` subscribes to the same
  topic it publishes to and takes every broadcast unfiltered, so the driver's own position comes
  back and fills the marker titled "Passenger". **Done:** filtered on `broadcast.userId` against
  `/users/me` rather than on `role` — that also survives passenger-to-passenger echo now that 5.3
  has passengers publishing too.
- [x] **5.2 Directions called every 4s** — `LaunchedEffect(myLocation, otherPartyLocation)` refired on
  every location tick: ~900 billable calls/hour per tracking screen, and on the driver's side it was
  routing me→me because of 5.1. **Done:** the route now runs to the ride's own destination (fetched
  via `ridesByIds` on entry) and is requested once; every later fix is snapped onto the stored
  polyline in `ui/RouteTracking.kt` (driven prefix grey, remainder in primary) and the ETA comes from
  remaining length ÷ rolling average speed, with no request. Re-fetch needs >150 m off-route for 3
  consecutive fixes, or a changed destination, and is floored at one per 2 minutes.
  `lastLocation` in a `while (true)` loop also became `requestLocationUpdates`, and the marker is
  interpolated across the 4 s gap. Covered by `RouteTrackingTest` (10 tests).
- [x] **5.3 Two-way location** — passengers never published (`isDriver` guard on the send loop), so
  the driver's screen had a "Passenger" marker slot that was permanently empty. **Done:** the send
  guard is now `if (isDriver || sharingEnabled) stompManager.sendLocation(...)`, where
  `sharingEnabled` is a passenger-only, on-by-default toggle rendered right above the map ("Sharing
  your location with the driver" / "Your location is hidden", with a `Switch`). Publishing lives
  entirely inside the same `DisposableEffect` as GPS collection, so leaving the tracking screen
  (`onDispose`) stops it outright — satisfies the foreground-only recommendation without a separate
  lifecycle observer. `otherPartyLocation` became `driverLocation` (single) plus `passengerLocations`
  (`Map<userId, LatLng>`), sorted from the broadcast's existing `role` field. Numbering — "Passenger
  1", "Passenger 2"... — comes from the new `GET /api/v1/ride-offers/{id}/passengers`
  (`RideOfferService.getConfirmedPassengerOrder`, confirmed bookings ordered by `createdAt`,
  restricted to the ride's driver or one of its own passengers), so the number is stable across
  every device on the ride rather than assigned locally. New markers in `ui/MapPins.kt`:
  `carMarker` for the vehicle, `numberedCircleMarker` for each passenger. The privacy question is
  resolved as: passengers opt out per-ride, not globally, and only ever broadcast while this screen
  is open.

  **This was marked done from code review alone, and shipped two real bugs that only surfaced when
  actually driven on a physical device** (one against `network/StompManager.kt`, pre-existing —
  not introduced this round; one against the new `carMarker`):
  - `StompManager.disposables` was a single `CompositeDisposable` reused across reconnects.
    RxJava permanently kills a `CompositeDisposable` on `.dispose()` — anything `.add()`ed to it
    afterward is disposed immediately, silently, with no error. `LiveLocationScreen` always
    reconnects once (`DisposableEffect(myUserId)` re-keys the moment the async `/users/me` call
    resolves from null to a real id), so the topic subscription added on that *second* connect was
    torn down the instant it was added. Net effect: no broadcast — driver's or any passenger's —
    was ever actually delivered to a live screen, on any device, ever, even though outgoing
    `sendLocation()` kept working fine (it doesn't go through this composite) and unit tests never
    exercise `StompManager` at all. **Fixed:** `connect()` now assigns a fresh
    `CompositeDisposable` each call instead of reusing one across the object's lifetime.
  - `carMarker`'s wheels were drawn in the `ring` color — identical to the background circle
    color, making them invisible regardless of theme — and `LiveLocationScreen` passed
    `MaterialTheme.colorScheme.primary` as the fill, which is `White` in dark theme
    (`ui/theme/Theme.kt`), so the body/cabin were *also* the same color as the background. Dark
    theme is this app's default, so the vehicle marker was a plain white blob with no car visible
    on it in ordinary use. **Fixed:** wheels now draw in `fill` (matching body/cabin), and the
    vehicle color is a fixed `Color.Black` instead of the theme-dependent primary.

  Found by: creating two real passenger accounts and a driver account via the API, starting the
  ride, running two small Python STOMP publisher scripts (`websocket-client`, hand-built STOMP
  frames — no `stomp.py` dependency needed) to simulate the driver's and a second passenger's GPS,
  and watching the actual tracking screen on a physical device. Confirmed after both fixes: a
  correctly-numbered green "2" circle for the second passenger and a legible black car icon for
  the vehicle, both rendering and moving as fixes arrived.
- [x] **5.4 Get the Maps key off the client** — done via a backend proxy. `GET /api/v1/directions`
  (`DirectionsController` + `DirectionsService`) holds the key server-side as `MAPS_SERVER_KEY`;
  `DirectionsHelper` now calls it over the authenticated Retrofit client and only decodes the
  polyline locally. Verified: the debug dex contains `api/v1/directions` and no
  `maps/api/directions` URL. Two things still to do **in the Google Cloud console**, which is where
  the saving actually lands: IP-restrict the server key to the backend host and limit it to the
  Directions API, then restrict the client `MAPS_API_KEY` by package name + signing SHA-1 and to
  the Maps SDK for Android + Places only. Responses are cached in Redis for 7 days, keyed on
  origin/destination rounded to ~11m, so repeated routings of the same commute cost nothing.
  Note this does **not** subsume 5.2: live tracking re-routes from a moving origin, so each fix is
  a fresh key and a fresh call. Storing one polyline per ride on `ride_offers` is still the fix
  there, and is now a smaller change since the routing already happens server-side.
  `MAPS_SERVER_KEY` itself is wired for local dev via a gitignored `.env` in the backend repo
  (reusing the client key, since it isn't restricted yet) — the two Cloud console restrictions
  above are still the only things left here.

  **Console checklist (do it in this order — the client key is currently reused as
  `MAPS_SERVER_KEY`, so restricting it first would 403 every Directions call):**
  - [x] Create a **new** key, `QuickPool Directions (server)`. Application restriction: **IP
        addresses** — leave it unrestricted while the backend is on localhost, and add the host IP
        as soon as it is deployed. API restriction: **Directions API** only.
  - [x] Put that new key in the backend's gitignored `.env` as `MAPS_SERVER_KEY`, restart, and
        confirm a route still draws. **Verified 2026-09-10** — route drew on device.
        Note: nothing loads `.env` automatically, so the jar must be launched with
        `set -a; source .env; set +a` first or the key is silently empty.
  - [ ] Only then restrict the existing client key (`QuickPool Android`): application restriction
        **Android apps**, package `com.quickpool.app`, SHA-1
        `8E:5D:D7:ED:CD:C6:FA:C0:CE:4D:74:E3:71:1C:2F:B5:54:B5:9E:BC` (the debug keystore on this
        machine). API restriction: **Maps SDK for Android** + **Places API** only.
  - [ ] Add the release SHA-1 to that same key when a release keystore exists — and the Play App
        Signing SHA-1 too, since Play re-signs the upload. Maps go grey on a store build otherwise.

---

## Push notifications (FCM) — built 2026-09-10
Firebase project `quickpool-909f1`. Two credential files, both gitignored:
`app/google-services.json` (client config, not secret) and the backend's
`firebase-service-account.json` (private key — **never commit**), pointed at by
`FIREBASE_CREDENTIALS` in the backend `.env`.

- [x] `V11__device_tokens.sql` — one row per device, several per user. `token` is unique
      table-wide, not per user: FCM reissues the same token to whoever installs next on a
      device, so re-registering moves the row rather than leaving the old owner's pushes
      going to a phone they no longer hold.
- [x] `POST`/`DELETE /api/v1/devices` (`DeviceController`, `DeviceTokenService`) — register
      is idempotent, unregister is silent when the token is already gone.
- [x] `FcmSender` — `@Async` so a slow FCM round trip never holds up the booking transaction,
      and it swallows its own failures. The inbox row stays the source of truth; a dead token
      or an FCM outage costs a buzz, never the notification. Prunes `UNREGISTERED` /
      `INVALID_ARGUMENT` tokens so they stop being retried forever.
- [x] `FirebaseConfig` returns a null `FirebaseMessaging` when no credentials are set, and
      `FcmSender` injects it via `ObjectProvider` — so the backend still boots for a developer
      without the service account file, just with push disabled.
- [x] App: `QuickPoolMessagingService`, `quickpool_rides` channel created in
      `MainActivity.onCreate` (a notification posted to a missing channel is dropped silently),
      `POST_NOTIFICATIONS` asked for **after** login rather than on first launch, and
      `DeviceRegistrar` registering on login + cold start and unregistering on logout
      *before* the auth token is cleared.
- [ ] Deep-link the tap. `entityId` and `type` already ride along in the payload and reach
      `MainActivity` as intent extras — nothing reads them yet, so a tap just opens Home.

---

## Parked — needs money
- SMS OTP (MSG91/Twilio) — currently printed to the server console
- Hosted backend + domain + TLS certificate
- Payments / settlement
- Paid KYC identity checks
