# bugs_live.md

Live bug audit — QuickPool app + backend. Written 2026-09-01 by reading both codebases.

Ordered by **what it costs you**, not by how hard it is to fix. Each entry says where it is,
what actually goes wrong, what it costs, the trade-off being made, and the fix.

Everything here was read out of the current source. Items already tracked in
`PRODUCTION_TASKS.md` are cross-referenced rather than repeated; this file is about defects,
not roadmap.

**Not verified by running.** These come from reading the code. B1, B3, F2 and F3 are certain
from the source alone. B2/F1, B5 and B10 are race conditions and scaling failures — the
reasoning is spelled out so you can judge each one, but they need load to reproduce.

---

## Severity summary

| # | Bug | Severity |
|---|---|---|
| B1 | Refresh token works as an access token; revocation does nothing | **Critical** |
| B2 + F1 | Parallel refresh trips theft detection and logs everyone out | **Critical** |
| F2 | `BASE_URL` hardcoded to `localhost` — release APK talks to nothing | **Critical (ship blocker)** |
| B3 | `X-Forwarded-For` trusted blindly — rate limits bypassable | **High** |
| F3 | Full request/response bodies logged in release builds | **High** |
| F4 | Live tracking: hardcoded URL, token in query string, no reconnect | **High** |
| B4 | Logged-out and deleted users keep working for 15 minutes | Medium |
| B5 | Seat count can be lost when a driver declines a request | Medium |
| B6 | A completed ride can be cancelled | Medium |
| B7 | Changing your email keeps the "verified" tick | Medium |
| B8 | Ride search loads every active ride into memory | Medium |
| B9 | N+1 queries on every bookings list | Medium |
| B10 | Lifecycle sweep: one transaction, no distributed lock | Medium |
| B11 | Share link outlives the ride by up to 12 hours | Medium (privacy) |
| F5 | Expired session strands the user on a broken Home screen | Medium |
| F6 | Rotating the phone loses all screen state | Medium |
| F7 | Tokens stored unencrypted | Medium |
| B12 | Raced double-rating returns 500 instead of 409 | Low |
| B13 | `createRideOffer` is not transactional | Low |
| F8 | Duplicate `Authorization` header on retried requests | Low |
| F9 | Seat count stale after booking | Low |

---

# Critical

## B1 — A refresh token works as an access token, and revocation does nothing

**Where:** `filter/JwtAuthenticationFilter.java:30-37`

```java
if (jwtService.isValid(token)) {
    UUID userId = jwtService.extractUserId(token);
    // authenticated — no check of the "type" claim
}
```

**What happens.** `JwtService` stamps every token with a `type` claim — `"access"` or
`"refresh"` — and `TokenService.rotate` checks it. **The filter never does.** A refresh token
is a valid signed JWT whose subject is the user id, so presenting one as
`Authorization: Bearer <refresh token>` authenticates every endpoint in the app.

That single omission unwinds the whole token design:

- Access tokens are deliberately short (15 min). A refresh token lasts **30 days** — so an
  attacker who gets one holds a 30-day access token, not a 15-minute one.
- Refresh tokens are single-use and tracked in `refresh_tokens`, and reuse burns every session
  (`TokenService.rotate:75-80`). But **that table is only consulted inside `rotate`.** Used as
  a bearer token the JWT never touches it — so a token you revoked, rotated away, or burned
  for theft *still authenticates every request* until it expires on its own.
- `logout` calls `revokeAll`, which sets `revoked = true` in that same unread table. Logging
  out therefore does not invalidate the refresh token for API access.

**Cost.** The theft-detection machinery you built — single-use rotation, `jti` tracking,
`TokenRevoker` with `REQUIRES_NEW` — currently protects nothing. A leaked refresh token is a
30-day skeleton key you cannot revoke. That is a breach you cannot close without rotating
`JWT_SECRET` and logging out every user on the platform.

**Trade-off.** None. This is not a considered decision, it is a missing line. Checking the
claim costs one string comparison per request.

**Fix.** In the filter, reject anything that is not an access token:

```java
if (jwtService.isValid(token) && "access".equals(jwtService.extractType(token))) {
```

`extractType` already exists. Add a test asserting a refresh token gets 401 on `/users/me` —
this is exactly the kind of bug that reappears in a refactor.

---

## B2 + F1 — Parallel refresh trips theft detection and logs the user out

**Where:** `network/TokenAuthenticator.kt:13-37` (app) and `service/TokenService.java:57-95` (backend)

**What happens.** The two halves are individually reasonable and combine badly.

The backend treats a *second* use of a refresh token as theft and burns every session for
that user:

```java
if (Boolean.TRUE.equals(stored.getRevoked())) {
    log.warn("Reuse of a spent refresh token for user {} — revoking all sessions", ...);
    tokenRevoker.revokeAllNow(stored.getUserId(), "REUSE_DETECTED");
```

The app calls `/refresh` from OkHttp's `Authenticator`, which fires **once per failed
request**, with no lock and no deduplication. Screens routinely have several calls in flight
at once — `HomeScreen` alone issues `impact()`, `searchRides()` and the route fetch together.

So when the access token expires with three requests in flight, all three get 401, all three
enter `authenticate()`, all three read the *same* refresh token from DataStore, and all three
POST it. The first rotates it and succeeds. The other two present a token that is now
`revoked = true` — and the backend, correctly by its own rules, concludes the token was stolen
and **revokes every session the user has**.

There is a second, narrower race purely inside `rotate`: the read of `stored.getRevoked()` and
the write of `revoked = true` are not atomic and the row is not locked, so two simultaneous
refreshes can both see `false` and both succeed — the opposite failure, where theft detection
silently misses.

**Cost.** Users are randomly signed out and must redo phone + OTP. It fires most often exactly
15 minutes into a session — mid-ride, on a screen where they are tracking a driver. Once SMS
is wired, every one of those spurious logouts also costs you an SMS. And your logs fill with
`REUSE_DETECTED` warnings from ordinary users, so a real token theft is invisible in the noise.

**Trade-off.** Single-use rotation with theft detection is the right design — don't weaken it.
The bug is that the client violates the contract the server is enforcing. Fix the client;
harden the server.

**Fix.** Both sides:

- **App (the real fix).** Serialise refresh. Wrap the body of `authenticate()` in a
  `synchronized` block or a `Mutex`, and first re-check whether the token already changed —
  if `TokenHolder.accessToken` is no longer the one that just 401'd, another thread already
  refreshed, so retry with the new token instead of calling `/refresh` again.
- **Backend (defence in depth).** Lock the row: add a `findByJtiForUpdate` with
  `@Lock(PESSIMISTIC_WRITE)` so concurrent rotations serialise. Optionally add a small grace
  window — a token rotated within the last few seconds returns the *same* new pair rather than
  being treated as theft — which absorbs client retries without weakening real detection.

---

## F2 — The release APK points at `localhost`

**Where:** `network/ApiClient.kt:11`

```kotlin
private const val BASE_URL = "http://localhost:8080/"
```

**What happens.** One hardcoded constant, no build-type variation. A release APK on a user's
phone resolves `localhost` to the phone itself, where nothing is listening. Every request
fails. `res/xml/network_security_config.xml` also only permits cleartext for `localhost`,
`127.0.0.1` and `10.0.2.2`, so pointing this at a plain-HTTP server is blocked too.

`StompManager.kt:19` repeats the same address independently (see F4), so there are two places
to change, and changing only one leaves live tracking silently broken.

**Cost.** Ship this and the app is inert on every device. It is not subtle — but it is exactly
the kind of thing that gets discovered *after* a Play Store review cycle.

**Trade-off.** Hardcoding was right for local development. It just needs to become
build-dependent before release.

**Fix.** Make it a `buildConfigField` per build type — debug keeps `http://localhost:8080/`,
release gets your real HTTPS origin — and have `StompManager` derive its `ws(s)://` URL from
that same constant instead of holding its own copy. Add your production host to the network
security config only if it is HTTPS (it should be, and then it needs no entry at all).

---

# High

## B3 — `X-Forwarded-For` is trusted, so IP rate limits are bypassable

**Where:** `controller/AuthController.java:63-70`

```java
String forwarded = request.getHeader("X-Forwarded-For");
if (forwarded != null && !forwarded.isBlank()) {
    return forwarded.split(",")[0].trim();
}
```

**What happens.** That header is attacker-controlled — it is just a request header, and
nothing here checks that the request actually arrived through a trusted proxy. Sending a
random value per request gives every request a fresh rate-limit bucket.

That defeats the IP half of all three auth limits: `otp-ip` (20/hr), `otp-verify` (30/hr),
`refresh` (60/hr). The per-phone budget on `otp-phone` (5/hr) still holds, since that keys on
the phone number in the body — so an attacker cannot spam *one* number, but can walk through
*many* numbers unthrottled.

**Cost.** This is the one that turns into a bill. `OtpService` currently logs codes instead of
sending them, so today it is free. The moment MSG91 or Twilio is wired — the top item in your
"Parked — needs money" list — an unthrottled endpoint that triggers an SMS per call is a
direct spend. At roughly ₹0.15/SMS, a script doing 50 requests/second across rotating numbers
burns about ₹27,000 an hour, and you find out from the invoice. It is also an SMS-pumping
vector: attackers farm revenue by driving traffic to premium numbers.

**Trade-off.** You genuinely need the header once you are behind a load balancer, or every
request appears to come from the proxy and one bucket throttles the whole country. The rule is
that the header is trustworthy *only* when the peer is your own proxy.

**Fix.** Configure `server.forward-headers-strategy=framework` and read
`request.getRemoteAddr()` — Spring then applies the header only from trusted peers. If you
parse it yourself, check `request.getRemoteAddr()` is in your proxy's subnet first, and take
the **last** untrusted hop, not the first (the first entry is the one the client supplied).
Until you are behind a proxy, drop the header handling entirely.

---

## F3 — Full request and response bodies are logged in release builds

**Where:** `network/ApiClient.kt:14-16`

```kotlin
private val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
}
```

**What happens.** Unconditional — no `BuildConfig.DEBUG` check — so a shipped release APK
writes every request and response body to logcat. That includes the `Authorization` header on
every call, both tokens in the `/auth/otp/verify` and `/auth/refresh` responses, the OTP in the
verify request, and users' names, emails, phone numbers and live coordinates.

**Cost.** Modern Android confines logcat to the owning app, so this is not trivially readable
by other apps — but it lands in bug reports, crash-reporter attachments, `adb` captures during
support, and anything on a rooted or debuggable device. Credentials in logs are a standard
audit finding and a Play Store data-safety problem, since you are writing user PII to a
surface your privacy policy does not describe. `Level.BODY` also buffers every response into
memory, which is real overhead on the 4-second location stream.

**Trade-off.** `BODY` logging is genuinely valuable in development — keep it there.

**Fix.**

```kotlin
level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE
```

Add `redactHeader("Authorization")` so the token stays out even of debug logs.

---

## F4 — Live tracking: separate hardcoded URL, token in the query string, no reconnect

**Where:** `network/StompManager.kt:11-41`, used by `ui/LiveLocationScreen.kt:67-68`

Four distinct problems in one small class:

**1. Its own hardcoded URL.** `val url = "ws://localhost:8080/ws?token=$token"` duplicates
`ApiClient.BASE_URL`. Fix F2 and forget this line and live tracking breaks in production while
everything else works — the worst kind of failure, because it looks like a tracking bug.
Cleartext `ws://` is also blocked outside the allowlisted dev hosts.

**2. The JWT travels in the query string.** URLs are logged far more freely than headers — by
proxies, load balancers, and access logs — so this writes an access token into infrastructure
logs you may not control. (WebSocket handshakes cannot carry custom headers from a browser,
which is why the pattern exists; on Android you have more options.)

**3. The token is captured once, at construction.**

```kotlin
val stompManager = remember { StompManager(rideId = rideId, token = TokenHolder.accessToken ?: "") }
```

`remember` freezes the token for the life of the screen, but access tokens expire in **15
minutes** and rides last longer. The existing connection survives (it authenticated at
handshake), but any reconnect after that point presents an expired token and fails.

Note also the `?: ""` — if `TokenHolder` is empty at that moment, this connects with an empty
token and fails silently rather than surfacing anything.

**4. There is no reconnect at all.** `connect()` subscribes once; nothing watches the
lifecycle stream (the callback at line 23-25 is an empty stub with a TODO comment). Android
drops sockets constantly — cell-to-Wi-Fi handover, a tunnel, doze. When that happens, live
tracking stops permanently with **no error shown**: the map simply freezes on the last known
position, which reads as "the driver stopped moving."

**Cost.** The headline feature of a ride-pooling app quietly stops working mid-ride, and the
failure is indistinguishable from a stationary car. A passenger waiting at a pickup point sees
a driver who appears not to be coming.

**Trade-off.** The screen-scoped lifecycle (`disconnect()` in `onDispose`) is correct and worth
keeping. What's missing is reconnection and a fresh token per attempt.

**Fix.** Derive the URL from `ApiClient.BASE_URL` (swapping `http`→`ws`, `https`→`wss`). Read
`TokenHolder.accessToken` at *connect* time, not construction — pass a `() -> String?` lambda.
Subscribe to `lifecycleClient.lifecycle()` and reconnect with exponential backoff on
`ERROR`/`CLOSED`, refreshing the token first. Show a "reconnecting…" state so a frozen map is
never mistaken for a stopped car. Longer term, move the token from the query string into the
STOMP `CONNECT` frame headers, which `JwtHandshakeInterceptor` can read instead.

---

# Medium

## B4 — Logged-out and deleted users keep working for 15 minutes

**Where:** `filter/JwtAuthenticationFilter.java`, `controller/UserController.java:144-148`

The filter authenticates from the JWT signature alone and never loads the user. So:

- After `logout`, the access token keeps working until it expires (≤15 min). Independent of
  B1, and it also means "sign out on my lost phone" does not take effect immediately.
- A soft-deleted user (`deletedAt` set) still passes. `currentUser()` looks up by id and does
  not filter on `deletedAt`, so a deleted account can keep reading and writing. `OtpService`
  and `TokenService.rotate` both check `deletedAt` correctly — the filter is the gap.

**Cost.** Account deletion is a Play Store data-safety commitment; "deleted" accounts that
still work is a compliance problem as much as a security one.

**Trade-off.** Checking the DB on every request costs a query — which is the entire reason
stateless JWTs exist. Don't reach for a full session lookup.

**Fix.** Cheapest correct version: keep the filter stateless, but have `currentUser()` (and the
equivalent lookups elsewhere) filter on `deletedAt == null`. For immediate logout, add a
Redis-backed deny-list of revoked `jti`s with a TTL matching the access-token lifetime —
one Redis GET per request, and the existing `CacheConfig` error handler already degrades
gracefully. Accept the 15-minute window only if you decide it is acceptable, explicitly.

## B5 — A declined booking can lose seats

**Where:** `service/BookingService.java:118-138` and `195-223`

`rejectBooking` loads the ride **unlocked** via `requireDriverOwns` → `findById`, then calls
`releaseSeats`, which asks for the same row with `findByIdForUpdate`. Because the entity is
already in the persistence context, Hibernate returns the **cached instance** — it takes the
lock, but does not refresh the in-memory field. So `offer.getSeatsAvailable()` may be a value
read *before* the lock was held.

Two drivers' actions on the same ride overlapping — a decline landing while a passenger
cancellation commits — can therefore both compute from the same stale count, and one increment
is lost.

`cancelBooking` is safe: it calls `releaseSeats` first, so the locked read is also the first
read. `bookRide` is safe for the same reason.

**Cost.** A ride shows fewer seats than it has. Passengers cannot book seats that exist, and
the driver drives with empty seats — lost bookings, and a hard-to-explain support ticket.

**Trade-off.** Pessimistic locking is the right call for seat counts and the rest of the code
does it properly. This is one path that reads before it locks.

**Fix.** Have `requireDriverOwns` take the lock too — use `findByIdForUpdate` there — so the
first read is the locked one. That makes the ordering uniform across all four mutating paths.

## B6 — A completed or in-progress ride can be cancelled

**Where:** `service/RideOfferService.java:126-153`

`cancelRideOffer` checks ownership and nothing else. There is no status guard, so a driver can
cancel a ride that is `IN_PROGRESS`, `COMPLETED` or already `EXPIRED`.

Cancelling a completed ride flips it to `CANCELLED`, which then breaks `RatingService.rate`
(it requires `IN_PROGRESS` or `COMPLETED` at line 60) — so a driver can retroactively destroy
the rating window for a ride that actually happened, including a bad rating they saw coming.
Passengers who already completed the trip get a "The driver cancelled the ride you booked"
notification days later.

**Cost.** A rating system a driver can opt out of after the fact is not a trust signal, and
trust is the product in ride-pooling.

**Trade-off.** Cancelling an `IN_PROGRESS` ride might be legitimate — a breakdown. But that
should be a distinct outcome, not a silent rewrite of history.

**Fix.** Guard it: allow cancellation only from `ACTIVE` or `FULL`. If mid-ride abort is
needed, add an explicit `abortRide` that ends the trip without erasing the ratings window.

## B7 — Changing your email keeps the "verified" tick

**Where:** `controller/UserController.java:65-71`

```java
user.setName(dto.getName().trim());
user.setEmail(dto.getEmail().trim());   // emailVerified untouched
```

Verify `alice@example.com`, then PUT a new email — `emailVerified` stays `true` for an address
nobody ever proved they own. `EmailVerificationService.confirm` carefully guards the *other*
direction (a code issued for an old address is rejected, line 93-95), which shows the intent;
this side was missed.

There is also no uniqueness check, so two accounts can hold the same email.

**Cost.** Whatever you gate on `emailVerified` later — receipts, password recovery, trust
badges, notifications — is gateable by anyone typing any address. If you ever use email for
account recovery, this is an account-takeover path.

**Fix.** In `updateMe`, if the trimmed email differs from the stored one, set
`emailVerified = false` and delete any outstanding verification row. Add a unique index on
`LOWER(email)` where `deleted_at IS NULL` (new Flyway migration — you are at V9), and map the
constraint violation to `ConflictException`.

## B8 — Ride search loads every active ride into memory

**Where:** `service/RideOfferService.java:85-117`

```java
rideOfferRepository.findByStatusAndDepartureTimeBetween(RideStatus.ACTIVE, from, to)
    .stream()
    .filter(...)   // driver, blocks, seats, corridor — all in the JVM
```

Every `ACTIVE` ride in a 3-hour window is pulled into the app and filtered in Java, including
the geometry (`GeoUtils.distancePointToSegmentMeters`). You have Postgres **with PostGIS** and
none of it is used for this.

**Cost.** Fine at 50 rides, fatal at scale — and the failure is superlinear, because *every*
search does it. At 5,000 active rides in a window, each search deserialises 5,000 entities and
runs 10,000 distance calculations. Search is the hottest path in the app, so this becomes your
first outage and your first surprise hosting bill.

**Trade-off.** The corridor approximation is a deliberate, documented v1 simplification and
that is fine. The problem is not the approximation, it is doing it in the wrong place.

**Fix.** Push at least the coarse filter into the query. A bounding-box predicate on
origin/destination in SQL cuts the candidate set by orders of magnitude before Java sees it,
and needs no schema change. Properly: store a `geography(LineString)` per ride and use
`ST_DWithin`, which uses a GiST index — that is what PostGIS is there for.

## B9 — N+1 queries on every bookings list

**Where:** `service/BookingService.java:166-193`, `service/RatingService.java:92-102`

`getMyBookings` and `getBookingRequestsForDriver` both call `rideOfferRepository.findById`
inside a `.map()` — one query per booking — and the driver version adds a `userRepository`
lookup per row too, so a driver with 30 bookings issues about 61 queries for one screen.
`reviewsFor` does the same, one user lookup per rating.

`search` and `visibleByIds` in `RideOfferService` already batch with `findAllById` — the
pattern to copy is in the codebase.

**Cost.** Slow list screens that get slower as users get more active — your best users get the
worst experience. Connection-pool pressure under load turns it into timeouts.

**Fix.** Collect the ids, one `findAllById`, build a map, then map over it — exactly as
`RideOfferService.search:104-109` does.

## B10 — Lifecycle sweep: one transaction, no distributed lock

**Where:** `service/RideLifecycleService.java:42-47`

```java
@Scheduled(fixedDelayString = "${app.lifecycle.interval-ms:900000}")
@Transactional
public void sweep() { expireUnstarted(); completeStale(); }
```

Two issues:

- **One transaction for the whole sweep.** Every ride and every booking it touches, plus every
  notification, is one unit of work. One bad row rolls back the entire sweep — and it will
  retry and fail again 15 minutes later, so rides stay stuck in `ACTIVE` indefinitely with no
  visible error beyond a log line.
- **No distributed lock.** `@Scheduled` fires on every instance. The moment you run two pods
  for availability, both sweep simultaneously — duplicate `RIDE_CANCELLED` notifications to the
  same passengers, and concurrent writes to the same rows with no locking.

The sweep also does not lock the rides it updates, so it can race a driver pressing "Start" at
the moment it is being expired.

**Cost.** Today (single instance) the blast radius is a stuck sweep. On the day you scale to
two instances — likely the day you get traction — users get duplicate cancellation notices.

**Fix.** Move `@Transactional` down to a per-ride method so one bad row skips rather than
kills the batch (call it through a separate bean so the proxy applies — the same
self-invocation trap `TokenRevoker` documents). Add ShedLock, or a Postgres advisory lock,
before you run more than one instance. Lock rides with `findByIdForUpdate` inside the per-ride
transaction.

## B11 — A share link outlives the ride by up to 12 hours

**Where:** `service/TripShareService.java:30, 95-120`

Links expire on a fixed 12-hour clock (`SHARE_HOURS`) with no reference to the ride's state.
`view()` checks `revoked` and `expiresAt` — never `ride.getStatus()`. So after the ride
completes, anyone holding the link keeps seeing `lastLat`/`lastLng`: the **drop-off point**,
which for most rides is where someone lives.

The endpoint is deliberately public (correctly — followers have no account), so this data is
available to anyone the link reaches, forwarded onward or not.

**Cost.** You are exposing a user's home address to a link they shared to feel safer on one
trip. That is the opposite of what the feature is for, and it is a serious privacy story if
anyone notices — exactly the kind of thing that generates press for a consumer app in India.

**Trade-off.** The link must stay live slightly past arrival — the follower wants to see the
trip *did* complete. That is minutes, not 12 hours.

**Fix.** In `view()`, stop returning `lastLat`/`lastLng` once the ride is `COMPLETED`,
`CANCELLED` or `EXPIRED` — return the status so the follower sees "trip completed" with no
coordinates. Expire the share itself shortly after completion (say 30 minutes), keeping the
12-hour cap only as an upper bound.

## F5 — An expired session strands the user on a broken Home screen

**Where:** `network/TokenAuthenticator.kt:28-32`, `MainActivity.kt:67-76`

When refresh fails permanently — the refresh token expired after 30 days, or every session was
revoked by B2 — the authenticator clears the tokens and returns `null`. Nothing tells the UI.
The user stays on whatever screen they were on, and every call fails with a generic error.
Only a full app restart re-runs the `startDestination` logic and routes them to login.

**Cost.** Looks like the app is broken, not like a session expiry. Users uninstall over this
rather than restarting, and the crash reporter shows nothing because nothing crashed.

**Fix.** Expose an auth-state signal — a `MutableStateFlow<Boolean>` on `TokenHolder`, or a
`SharedFlow` of logout events — set it when the authenticator gives up, collect it in `AppNav`,
and navigate to `LOGIN` clearing the back stack. One place to emit, one place to react.

## F6 — Rotating the phone loses everything on screen

**Where:** every screen — the documented Phase 4 gap in `PRODUCTION_TASKS.md`

State lives in `remember { mutableStateOf(...) }` inside composables, which does not survive
configuration change or process death. Rotating the phone on `HomeScreen` clears the chosen
destination, the search results and the route — and re-triggers the `LaunchedEffect`s, so it
also **re-issues the network calls**, including a billed Directions request.

`OfferRideScreen` is the worst case: a half-filled ride form is lost on rotation.

**Cost.** Beyond the UX, it is a small ongoing spend — every rotation is another Directions
call — and process death (routine when a user takes a call mid-form) loses their input
entirely.

**Trade-off.** Adding ViewModels is real work and the current shape is legitimately simpler.
It is a known, accepted gap — but it is worth knowing it costs money, not just polish.

**Fix.** As planned in Phase 4. If you want the cheap intermediate step, `rememberSaveable`
for the plain-data fields (text, ids, picked places) fixes rotation for the form screens
without introducing a ViewModel layer.

## F7 — Tokens are stored unencrypted

**Where:** `data/TokenStore.kt:9-24`

Plain `preferencesDataStore`. The 30-day refresh token sits in cleartext in the app's data
directory — readable on a rooted device, in an ADB backup, or by any device-management agent.
Combined with B1 (a refresh token authenticates everything) the impact is a full account
takeover, not just a refresh capability.

**Fix.** Fixing B1 shrinks this a lot on its own. Beyond that, wrap the values with a
Keystore-backed cipher before writing them (`androidx.security.crypto`, or an
`EncryptedFile`-backed DataStore), and set `android:allowBackup="false"` so tokens never leave
in a cloud backup.

---

# Low

## B12 — A raced double-rating returns 500 instead of 409

`RatingService.rate:66-79` checks `existsBy...` then saves. The check-then-act is not atomic,
but the database catches it: `V7__trust_and_safety.sql:23` already has
`uq_rating_per_ride UNIQUE (ride_offer_id, rater_id, ratee_id)`, so the duplicate is rejected
and the average stays correct.

What's left is only the error surface — the constraint violation is not mapped, so it falls
through to `GlobalExceptionHandler`'s catch-all and the user gets
`500 "Something went wrong"` where the same request one second later gives a clean
`409 "You have already rated them for this ride"`.
**Fix:** catch `DataIntegrityViolationException` in `rate` and rethrow as `ConflictException`.

## B13 — `createRideOffer` is not transactional

`RideOfferService.createRideOffer:57-82` saves the ride and writes an activity log outside any
transaction, so they can diverge. Every other mutating method in the class is `@Transactional`.
**Fix:** add `@Transactional`.

## F8 — Duplicate `Authorization` header on retried requests

`AuthInterceptor.kt:12` uses `addHeader`. On a request retried by `TokenAuthenticator` the
header is already set, so the interceptor appends a **second** one. Both currently carry the
same value (`saveTokens` updates `TokenHolder` before the retry), so it is benign today — but
it is fragile, and some gateways reject duplicate auth headers.
**Fix:** use `header(...)`, which replaces.

## F9 — Seat count is stale right after booking

`HomeScreen.book()` shows a confirmation but never refreshes `results`, so the card still
shows the old `seatsAvailable`. Tapping again returns "You already have a booking on this
ride" from the backend — correct, but it reads as a bug to the user.
**Fix:** re-run the search, or decrement the seat count on the matching result locally.

---

# Already tracked, not repeated here

- **OTP and email codes are logged, not sent** (`OtpService`, `LoggingEmailSender`) — a
  deliberate stub, and a hard production blocker. Note it interacts with B3: fixing the rate
  limiting matters *before* the SMS provider is wired, not after.
- **Passengers are invisible on the map** — `PRODUCTION_TASKS.md` 5.3, with the
  `otherPartyLocation` single-value trap documented in the Phase 5 primer.
- **Maps key restrictions** — `PRODUCTION_TASKS.md` 5.4. The code side is done; the Cloud
  console side (IP-restrict the server key, app-restrict the client key) is still open, and
  until then the old unrestricted key in shipped APKs remains billable.

---

# Suggested order

1. **B1** — one line, closes an unrevokable 30-day key.
2. **F3** — one line, stops writing credentials to logs.
3. **B2 + F1** — client-side mutex first, then the server-side row lock. Stops the random logouts.
4. **B3** — before SMS is wired, not after.
5. **F2 + F4** — must both land before any release build; do them together since F4 depends on F2's constant.
6. **B6, B7, B5** — small, contained correctness fixes.
7. **B11** — privacy, and cheap.
8. **B8, B9, B10** — before traffic grows, not after.
