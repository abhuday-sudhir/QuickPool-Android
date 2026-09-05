# QuickPool — road to production

Everything here is buildable **without spending money**. Items that need paid
services are parked at the bottom so they don't get lost.

Status: `[ ]` todo · `[~]` in progress · `[x]` done

---

## Phase 1 — Trust & safety
The real gap for a carpooling app: strangers getting into cars together.

- [x] **1.1 Vehicle details** — done, backend + Account panel; shown on every search result
- [x] **1.2 Ratings & reviews** — done, backend + star dialogs for both driver and passenger
- [x] **1.3 Block users** — done, backend + overflow menu on ride cards and passenger rows
- [x] **1.4 Report users** — done, backend + reason-picker dialog
- [x] **1.5 Phone number privacy** — done; number withheld while PENDING, revealed only on CONFIRMED, never in search results
- [x] **1.6 Emergency contact + share trip** — done; expiring, revocable public link (no login needed to follow), contact panel in Account, OS share sheet
- [x] **1.7 Email verification** — done; 6-digit code via a pluggable `EmailSender` (logs in dev), verified badge in Account

> Phase 2 verified by `p2test.py` — 21 checks, all passing.

> Phase 1 verified by `p1test.py` (27 checks) and `p16test.py` (27 checks) — all passing, plus walked on-device.

## Phase 2 — Security hardening
- [x] **2.1 Refresh token rotation + revocation** — done; single-use refresh tokens, reuse burns every session, logout revokes server-side
- [x] **2.2 Rate limiting** — done; Redis fixed-window on OTP request/verify and refresh, per-phone *and* per-IP, fails open
- [x] **2.3 Account deletion** — done; scrubs PII, frees the phone number, refuses mid-commitment, in-app dialog
- [x] **2.4 Kill cleartext HTTP** — done; `network_security_config.xml`, cleartext only for localhost/emulator
- [x] **2.5 Release build** — done; R8 + resource shrinking (22.8 MB → 3.8 MB), ProGuard rules, env-driven signing scaffold

## Phase 3 — Correctness & scale
- [ ] **3.1 PostGIS ride search** — replace the in-memory corridor scan with a spatial index (PostGIS is already running)
- [ ] **3.2 Pagination** — notifications, bookings, rides
- [ ] **3.3 Fix N+1 queries** — booking lists fetch one ride per row in a loop
- [x] **3.4 Ride lifecycle job** — done; sweeps every 15 min, expires unstarted rides (+cancels their bookings), completes rides left running
- [~] **3.5 Tests** — backend: 21 passing (`RatingService`, `SafetyService`, `VehicleService`, `ImpactService`) · app: 15 passing (`Validation`, `TimeFormat`). Still to cover: controllers, booking/ride lifecycle, repository queries.

## Phase 4 — App architecture
- [ ] **4.1 ViewModels + repository** — state survives rotation/process death, no refetch storms
- [ ] **4.2 Error/retry handling** — consistent, offline-tolerant

## Phase 5 — Live tracking & Maps billing
Found while testing the ride socket across two devices. 5.1 and 5.2 are bugs that
cost real money today; 5.3 is a feature; 5.4 is a Play Store blocker.

### How the ride room works today — a primer

One `LiveLocationScreen`, opened by both sides, told apart only by the `isDriver` flag it is
handed. Everyone in the ride subscribes to the same STOMP topic, `/topic/ride/{rideId}/location`,
and publishes to `/app/ride/{rideId}/location`.

**Only the driver publishes.** The send is guarded: `if (isDriver) stompManager.sendLocation(...)`.
So the topic carries exactly one stream of positions — the vehicle's — and everything each screen
shows is built from that one stream plus its own GPS.

| | Driver's screen | Passenger's screen |
|---|---|---|
| Vehicle marker | own GPS, titled **"You"** | driver's broadcast, titled **"Driver"** |
| Own dot | — (it *is* the vehicle) | own GPS, titled **"You"** |
| Other passengers | **nothing** — they never publish | **nothing** |
| Route + ETA | yes | yes, once the driver's first fix lands |

The vehicle marker is the interpolated one: fixes arrive every 4s and the marker is animated
across that gap so it glides instead of teleporting. The route is snapped locally on every fix
(`ui/RouteTracking.kt`) — driven prefix grey, remainder in primary — and the ETA is remaining
length ÷ rolling average speed. None of that costs a request.

Two things to know before touching 5.3:

- `otherPartyLocation` is a **single** `LatLng`, not a collection. It works only because exactly
  one party publishes. The moment passengers publish too, each passenger's screen starts taking
  other *passengers'* fixes into that same variable and the "Driver" marker jumps between people.
  It has to become a map keyed by `userId` first.
- The echo filter is `broadcast.userId != myUserId`, where `myUserId` comes from `/users/me` on
  entry. If that call fails the id stays null, nothing is filtered, and the driver sees their own
  echo as a second pin — which is exactly the 5.1 bug returning through a different door.

### Where the Maps money actually goes

Three different Google products, and only two of them are billed:

- **Map loads** (Maps SDK for Android) — free, unlimited. Panning the map costs nothing.
- **Places Autocomplete** — billed, but already batched into sessions by session tokens.
- **Directions** — billed per call, ~$5 per 1,000. This is the one worth guarding, which is what
  5.2 (fewer calls) and 5.4 (a key nobody can steal) are both about.

Reverse geocoding is on-device (`android.location.Geocoder`) and never reaches Google's billed API.

- [x] **5.1 Driver sees its own echo as "Passenger"** — `LiveLocationScreen` subscribes to the same
  topic it publishes to and takes every broadcast unfiltered, so the driver's own position comes
  back and fills the marker titled "Passenger". **Done:** filtered on `broadcast.userId` against
  `/users/me` rather than on `role` — that also survives passenger-to-passenger echo once 5.3 lands.
- [x] **5.2 Directions called every 4s** — `LaunchedEffect(myLocation, otherPartyLocation)` refired on
  every location tick: ~900 billable calls/hour per tracking screen, and on the driver's side it was
  routing me→me because of 5.1. **Done:** the route now runs to the ride's own destination (fetched
  via `ridesByIds` on entry) and is requested once; every later fix is snapped onto the stored
  polyline in `ui/RouteTracking.kt` (driven prefix grey, remainder in primary) and the ETA comes from
  remaining length ÷ rolling average speed, with no request. Re-fetch needs >150 m off-route for 3
  consecutive fixes, or a changed destination, and is floored at one per 2 minutes.
  `lastLocation` in a `while (true)` loop also became `requestLocationUpdates`, and the marker is
  interpolated across the 4 s gap. Covered by `RouteTrackingTest` (10 tests).
- [ ] **5.3 Two-way location** — passengers never publish (`isDriver` guard on the send loop), so
  nobody on the ride can see where the passengers are — the driver's screen has a "Passenger"
  marker slot that is permanently empty. (A passenger *does* get their own "You" dot and the
  route: `myLocation` is set for both roles, and the route runs to the ride's destination off the
  driver's broadcast. An earlier note here claimed otherwise; the code says this.) The
  backend already relays both roles (`LocationController` classifies `DRIVER`/`PASSENGER` and only
  *persists* the driver's fix). Note a ride can have several confirmed passengers, so
  `otherPartyLocation` must become a map keyed by `userId`, one marker each. Decide the privacy
  question first: riders continuously broadcasting to the driver is a different consent story from
  following the vehicle. **Blocked on that decision** — recommendation is that a passenger publishes
  only while the tracking screen is foregrounded, with a visible indicator and an off switch.
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

Not billed, for reference: Maps SDK for Android map loads are free, and `reverseGeocode` uses the
on-device `android.location.Geocoder`. Places Autocomplete *is* billed but already uses session tokens.

---

## Parked — needs money
- SMS OTP (MSG91/Twilio) — currently printed to the server console
- Hosted backend + domain + TLS certificate
- FCM push (free tier, but needs a Firebase project you own)
- Payments / settlement
- Paid KYC identity checks
