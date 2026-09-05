# Implementation Plan - Transition to Map-Based Location Selection

This plan transitions the app from manual latitude/longitude entry to a map-based selection interface using the Google Maps Compose library. It also addresses the integration status of "Book a Ride" and potential backend changes.

## User Review Required

> [!NOTE]
> The current backend DTOs (`CreateRideOfferDto`, `RideSearchRequestDto`, etc.) only store coordinates (`Double`). While this works for searching, a production app would typically store and display human-readable addresses (e.g., "123 Main St"). I am sticking to coordinates for now to avoid breaking backend compatibility, but I recommend adding address fields to the backend in the future.

## Open Questions
- Do you want to automatically use the "Current Location" for the pickup/origin by default? (I will implement this as an option).

## Proposed Changes

### [Component] UI Screens

#### [MODIFY] [CreateRideScreen.kt](file:///Users/abhuday/AndroidStudioProjects/QuickPool/app/src/main/java/com/quickpool/app/ui/CreateRideScreen.kt)
- Replace manual `NumberField` entries for Origin/Destination with a map-based selection.
- Implement a "Pick on Map" button or an inline map that updates coordinates when the user drops a pin.
- Use `FusedLocationProviderClient` to suggest the current location as the default origin.

#### [MODIFY] [SearchRideScreen.kt](file:///Users/abhuday/AndroidStudioProjects/QuickPool/app/src/main/java/com/quickpool/app/ui/SearchRideScreen.kt)
- Replace manual `SearchField` entries for Pickup/Drop with map-based selection.
- Ensure the "Book a Ride" button remains functional and correctly navigates to `LiveLocationScreen` after booking.

### [Component] Navigation & Setup

#### [MODIFY] [MainActivity.kt](file:///Users/abhuday/AndroidStudioProjects/QuickPool/app/src/main/java/com/quickpool/app/MainActivity.kt)
- Request location permissions at runtime if they are not already granted.

## Answers to Your Questions

1.  **Backend Changes**: No strictly *required* changes if you continue using coordinates. However, adding `originName` and `destinationName` (Strings) to the backend DTOs would allow you to show address names in the ride list instead of just coordinates.
2.  **Booking Integration**: Yes, "Book a ride" is already integrated into the "Find a ride" (Search) screen. When a user clicks "Book this ride" on a search result, it calls the booking API and navigates them to the live tracking screen.

## Verification Plan

### Manual Verification
- Deploy to a device/emulator.
- Grant location permissions.
- Verify "Post a Ride" allows picking origin/destination on a map.
- Verify "Find a Ride" allows picking pickup/drop locations on a map.
- Verify booking a ride from search results still works and opens tracking.
