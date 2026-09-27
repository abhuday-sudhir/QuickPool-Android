# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

QuickPool is an intra-city ride-pooling product, aimed at India and intended for the Play Store. It has two halves, both actively developed:

- **This repo** — a single-module Android app (Kotlin + Jetpack Compose).
- **`/Users/abhuday/Projects/QuickPool`** — the Spring Boot backend (separate git repo, added via `permissions.additionalDirectories` in `.claude/settings.local.json`). **Edit it freely**; app features routinely need matching endpoints. Keep the two in step.

`PRODUCTION_TASKS.md` tracks the road to production — phases, what's done, and what's parked because it costs money (SMS, hosting, FCM, payments). Read it before planning work, and tick items off as you finish them.

## Commands

```
./gradlew assembleDebug                 # build debug APK
./gradlew installDebug                  # build and install on a connected device
./gradlew testDebugUnitTest             # JVM unit tests (app/src/test)
./gradlew assembleRelease               # R8-minified release APK
./gradlew lint                          # Android lint
```

Backend (run from `/Users/abhuday/Projects/QuickPool`, note `sh mvnw` — the wrapper is not executable):

```
sh mvnw -q -DskipTests clean package    # build the jar
sh mvnw test                            # unit tests
sh run.sh 8081                          # start with .env loaded — use this, not java -jar
```

`run.sh` exists because Spring Boot does **not** read `.env`. `application.yml` resolves
`${MAPS_SERVER_KEY}` and `${FIREBASE_CREDENTIALS}` from the process environment, so a plain
`java -jar` silently gets the empty defaults. Both failures are quiet — no route line drawn,
no push delivered — with nothing on screen to say why. `run.sh` sources `.env` with `set -a`
and launches the jar.

Always `clean`. Maven leaves stale `.class` files behind when a source file is deleted, and a leftover `@Service` gets packaged into the jar — which surfaces as a *duplicate bean* startup failure that looks nothing like its cause.

No ktlint/detekt; Android Lint only.

### Local configuration

`local.properties` must define `MAPS_API_KEY`. It is injected as both `BuildConfig.MAPS_API_KEY` and the `com.google.android.geo.API_KEY` manifest placeholder. Never hardcode it in the manifest.

This is the **client** key — Maps SDK for Android and Places only, both of which can be locked to the package name + signing SHA-1. Directions uses a **separate server key**, set as `MAPS_SERVER_KEY` in the backend's environment; without it the backend logs an error and routes come back 500, which on screen just looks like no line drawn.

### Running against the backend

The app targets `http://localhost:8080/` (hardcoded in `ApiClient.kt`).

```
adb reverse tcp:8080 tcp:8080           # or tcp:8081 if running the jar on 8081
```

Two gotchas:
- `adb reverse` is **cleared by every reinstall** and USB re-enumeration. Re-run it whenever requests start failing with "Failed to connect to localhost".
- `res/xml/network_security_config.xml` permits cleartext **only** for `localhost`, `127.0.0.1` and `10.0.2.2`. Pointing `BASE_URL` at a LAN IP over plain `http` will be blocked — add the host there or use HTTPS.

Infrastructure lives in the backend's `docker-compose.yml`: Postgres (5434), Redis (6379), Prometheus, Grafana. **This machine also runs a native Redis on 6379**, which wins for `localhost` — so the app's cache may be in the native instance, not the container. Check both before concluding the cache is broken.

## Architecture (app)

Packages under `app/src/main/java/com/quickpool/app/`: `data/`, `network/`, `ui/`, `ui/components/`, `ui/theme/`.

There is still **no ViewModel/repository layer** — Compose screens own their state with `remember`/`mutableStateOf` and call `ApiClient` directly from `LaunchedEffect` blocks and click lambdas. This is a known gap (Phase 4 in `PRODUCTION_TASKS.md`): state does not survive rotation or process death.

Pure logic is deliberately kept out of composables so it can be unit-tested on the JVM:
- `ui/Validation.kt` — email, name, per-country phone length, E.164 assembly, plate normalisation, star range.
- `ui/TimeFormat.kt` — `formatDeparture` / `formatRelative`. The 12-hour label is hand-built rather than using the `a` pattern, which renders `pm` or `PM` depending on JDK/CLDR.
- `ui/Geocoding.kt`, `ui/MapPins.kt` — reverse geocoding and marker bitmaps.

### Networking (`network/`)

`ApiClient` is an `object` exposing `authApi`, `rideApi`, `userApi`, `notificationApi`, `safetyApi`, `tripShareApi`, `directionsApi`. `ApiClient.tokenStore` must be set in `MainActivity.onCreate` before any lazy val is touched.

- `TokenHolder` — in-memory access token, read by `AuthInterceptor` on every request.
- `TokenStore` — DataStore-backed persistence; writes also update `TokenHolder`.
- `TokenAuthenticator` — refreshes on **401 only**. OkHttp's `Authenticator` ignores 403, so the backend deliberately returns 401 for unauthenticated requests (`HttpStatusEntryPoint` in `SecurityConfig`). If that ever reverts to Spring's default 403, token refresh silently stops working and every call fails ~15 minutes after login.
- `StompManager` — live location over STOMP; one instance per `LiveLocationScreen`, `disconnect()` in `DisposableEffect`'s `onDispose`.
- `DirectionsHelper` — asks the **backend** for routes (`GET /api/v1/directions`) and hand-decodes the
  returned polyline. It deliberately does *not* hold a Google key: Directions is a Web Service API and
  ignores Android package/signature restrictions, so a key in the APK is extractable and billable.

### Navigation (`ui/AppNav.kt`)

Single `NavHost`. Build routes with the helpers on `Routes` (`liveLocation`, `locationSearch`, `mapPicker`) rather than string-templating.

`location_search/{purpose}` and `map_picker/{purpose}` take a purpose — `find`, `offer` or `save` — so one picker serves the Home search, the offer-a-ride flow and Account's address book, and the result is routed back to the right place.

`AppNav` owns the Scaffold and the bottom bar. `MainActivity` deliberately has **no** Scaffold of its own: nesting them left a black strip under the bottom bar.

### Screens (`ui/`)

Each screen is one `@Composable` file owning its state and reporting outward via callbacks. Current set: `LoginScreen`, `RegisterScreen`, `HomeScreen`, `OfferRideScreen`, `ActivityScreen` (wrapping `MyRidesScreen` / `MyBookingsScreen`), `AlertsScreen`, `AccountScreen`, `LocationSearchScreen`, `MapLocationPickerScreen`, `LiveLocationScreen`.

Bottom bar is Home / Activity / **+** / Alerts / Account, where the **+** is a raised FAB sitting in a semicircular notch cut into the bar (`components/QuickPoolBottomBar.kt`), and opens the offer-a-ride flow.

`BitmapDescriptorFactory` (custom map markers) throws unless `MapsInitializer.initialize()` has run — it is called in `MainActivity.onCreate`, and `pinDescriptor`/`squareMarker`/`dotMarker` return null rather than crashing if it hasn't.

### Auth and registration flow

Phone + OTP, no password. `verifyOtp` returns `profileComplete`; the app routes to `REGISTER` when false, `HOME` when true. `MainActivity` re-checks `/users/me` on cold start so anyone who quit mid-registration lands back there. Name **and** email are required to complete a profile.

## Backend

Spring Boot 4 / Java 21, Postgres + PostGIS, Flyway, Redis, JWT. Migrations are at **V9**; add new ones rather than editing applied files.

Key services: `OtpService`, `TokenService` (+ `TokenRevoker`), `BookingService`, `RideOfferService`, `RatingService`, `SafetyService`, `VehicleService`, `TripShareService`, `EmailVerificationService`, `ImpactService`, `DestinationService`, `SavedAddressService`, `RideLifecycleService`, `RateLimiter`.

Behaviour worth knowing before changing anything:

- **Bookings are request → approve.** A booking starts `PENDING` and *holds the seat*; the driver accepts (`CONFIRMED`) or declines (`REJECTED`, seat released). Self-booking is refused, and blocked users are hidden from search and refused at booking in both directions.
- **Refresh tokens are single-use.** Each carries a `jti` tracked in `refresh_tokens`; refreshing revokes the old one. Presenting a spent token is treated as theft and burns every session for that user. That revocation runs in `TokenRevoker` with `REQUIRES_NEW` — it must survive the exception thrown straight after, and a self-call inside `TokenService` would bypass the proxy and be rolled back.
- **A ride lifecycle sweep runs every 15 minutes** (`RideLifecycleService`), expiring unstarted rides past departure and completing rides left running. It moves bookings to `COMPLETED` — anything gating on booking status (rating, contact details) must accept `CONFIRMED` **and** `COMPLETED`.
- **Phone numbers are withheld** until a booking is `CONFIRMED`, and never appear in search results.
- **Rate limiting** is a Redis fixed-window on the auth endpoints, per-phone and per-IP. It fails open: if Redis is down, requests are allowed rather than locking everyone out.
- **Redis is a cache only.** `CacheConfig` installs an error handler so an outage degrades to Postgres instead of 500ing.
- OTP and email verification codes are **logged, not sent** (`LoggingEmailSender`, `OtpService`). Read them from the backend console. Both use a partial unique index on the active row — when replacing one, `delete()` then **`flush()`**, because Hibernate orders inserts before deletes and will otherwise violate the constraint.
- `GET /api/v1/share/{token}` is intentionally **public** — the person following a shared trip has no account.

## Testing

Backend unit tests live in `src/test/java/com/QuickPool/service/` (JUnit 5 + Mockito + AssertJ). App unit tests in `app/src/test/java/com/quickpool/app/`. Both are thin; broaden them when touching a service.

Prefer verifying real behaviour over trusting a green compile — most bugs in this project have been found by running the flow end-to-end or looking at the screen, not by reading the code.
