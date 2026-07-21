# shalotrack-mobile

Workspace for the ShaloTrack Lanka Android application — the customer-facing GPS
vehicle tracking and fleet management app, built around Letstrack/V5 GPS hardware.
Designed with elderly-user accessibility as a core constraint (OTP-only login,
no passwords).

---

## ⚠️ Do not touch the CI/CD pipeline

This repo has an automated pipeline that builds the `.aab` (Android App Bundle)
used for Google Play Store uploads. **Do not modify pipeline configuration
files without explicit sign-off** — this is production release tooling, not
something to experiment on.

Play Store upload credentials have been shared separately (with Suwen) and are
**not** stored in this repository. Do not commit signing keys, keystores, or
Play Console credentials here under any circumstances.

---

## Live Development Status

| Area | Status |
|---|---|
| Authentication (Firebase Phone OTP + email verification) | ✅ Complete |
| Live vehicle tracking (Home + Vehicles tabs) | ✅ Complete |
| Real-time push updates (SignalR) | ✅ Complete, with poll fallback |
| Vehicle trail / route line | ✅ Complete |
| Reverse geocoding (addresses instead of coordinates) | ✅ Complete |
| Link Vehicle (customer-safe device linking by IMEI) | ✅ Complete |
| Vehicle Information (IMEI, GPS device status) | ✅ Complete |
| Trip History (list, stats, addresses) | ✅ Complete |
| Trip Playback (route animation, scrubbing, start/end markers) | ✅ Complete |
| Alerts (real-time list, mark-as-read) | ✅ Complete |
| Alerts push delivery (FCM) | 🚧 Not yet — tokens are collected, nothing sends a push yet |
| Edit Profile | ✅ Complete |
| Drawer menu (Letstrack-style) | ✅ Complete — some items are honest "Coming soon" placeholders |
| Navy design system (colors.xml) | 🚧 Partial — applied to Home, Vehicles, Trip History; not yet to Tags/Circles/Alerts |
| Engine Cut / Immobilize | ⛔ Blocked — requires a written safety specification from the client before any code is written. Do not build without it. |
| Geofencing | ⏳ Not started |
| Device Offline detection | ⏳ Not started (needs a periodic check, not an event trigger) |
| Subscription / billing | ⏳ Not started |

**Current focus:** trip history and alerts are functionally complete; remaining
work is largely visual polish (navy pass on remaining screens) and the FCM push
delivery layer for alerts.

---

## Tech Stack

- **Language:** Java
- **Min SDK:** 26 (Android 8.0)
- **Networking:** Retrofit2 + OkHttp, Gson for JSON parsing
- **Maps:** Google Maps SDK for Android
- **Real-time:** Microsoft SignalR Android client (`com.microsoft.signalr:signalr`)
- **Auth:** Firebase Phone Auth (OTP) + email verification — no password login
- **Push notifications:** Firebase Cloud Messaging (token collection built; actual
  delivery not yet wired — see Roadmap)
- **UI:** Standard Android Views (no Compose), Material Components,
  `RecyclerView`, `CardView`, `DrawerLayout`

---

## Architecture Notes

- **API base:** all data comes from the ShaloTrack C# API
  (`api.shalotrack.com`), authenticated via Firebase JWT bearer tokens attached
  to every request. There is no local login/password flow — the app
  authenticates with Firebase directly.
- **Known duplication (flagged, not yet resolved):** `HomeActivity` and
  `VehiclesActivity` each implement their own independent live-tracking loop
  (polling + SignalR + trail rendering) rather than sharing one. This has
  caused real bugs tonight — a fix applied to one screen didn't automatically
  apply to the other (e.g. the `AddressResolver` wiring, the immediate-fetch
  latency fix). A shared base class or fragment would remove this class of bug
  entirely. Recommended as a cleanup pass once remaining features stabilize.
- **Reusable components worth knowing about:**
  - `VehicleTrailRenderer` — draws the live trail polyline + animated,
    rotating car marker. Shared by Home and Vehicles.
  - `AddressResolver` — debounced reverse-geocoding (Android's free built-in
    `Geocoder`, not the paid Google API — deliberate cost choice). Shared by
    every screen showing a location.
  - `RealtimeLocationClient` — SignalR connection wrapper for live push
    updates, with automatic reconnect and a slow poll fallback if the push
    connection drops.

---

## Real-Time Push (Option B)

Live location updates arrive via a SignalR hub (`/hubs/location`) rather than
pure polling. A Postgres trigger on the API side fires on every location
write; a background listener relays it through SignalR to any client
subscribed to that vehicle's group. Typical latency: under 2 seconds from
device report to on-screen update. Polling still runs as a slow (60s) safety
net in case the push connection drops.

---

## Screens

| Screen | Notes |
|---|---|
| `OtpVerificationActivity` / `SignUpActivity` | Phone OTP + email verification, no password anywhere |
| `HomeActivity` | Live map, vehicle status, drawer menu (Letstrack-style, see below) |
| `VehiclesActivity` | Live map, vehicle detail sheet, action grid (History/Alerts/Nav/Details real; VoiceTrack/Value/Places placeholders; Immobilize deliberately inert) |
| `TripHistoryActivity` | Date-filtered trip list with per-trip distance/duration/addresses |
| `TripDetailActivity` | Full route playback — scrub or auto-play, start/end markers, live speed readout |
| `AlertsActivity` | Real alert history (ignition, overspeed, power-cut, low-battery), tap to mark read |
| `TagsActivity`, `CirclesActivity` | Exist; not part of tonight's build scope |

### Drawer menu

The navigation drawer (`layout_drawer_menu.xml`) matches Letstrack's reference
layout. Real, working items: **Add New** (opens Add Vehicle), **Reports**
(opens Trip History), **Chat & Support** (opens the call-center sheet),
**Log out**. Everything else (Places, Vehicle Subscriptions, App Subscription,
Refer and Earn, Shop, Help Videos, Settings, Privacy & Security) is an honest
"Coming soon" placeholder — not wired to fake data. **VoiceTrack with Alexa**
specifically shows "Not available for this app," since it's a Letstrack-owned
integration, not a ShaloTrack feature.

---

## Known Limitations (real, not hidden)

- **Ignition/ACC hardware issue on the test device:** the ACC sense wire was
  confirmed disconnected on the physical test vehicle, causing it to read a
  constant `true` regardless of real key state (a floating input pin defaults
  high). This is a wiring/installation issue, not a software bug — confirmed
  by direct byte-level inspection of raw GPS packets against the manufacturer's
  protocol spec, cross-checked against the actual parsing code. Will resolve
  itself once the physical wire is connected; no app change needed.
- **Email cannot be changed via Edit Profile.** The API's update endpoint
  doesn't accept an email field (tied to Firebase identity, not a casual
  profile edit). The form still shows the field; edits to it are silently not
  sent. A follow-up should make this field read-only in the layout.
- **Trip playback marker has no true heading data** — rotation is
  approximated from the bearing between consecutive points, since raw
  tracking points don't carry a heading value the way live location updates
  do. Looks correct in practice, not literally exact.
- **Alerts have detection but no delivery.** Ignition/Overspeed/Power-cut/
  Low-battery alerts are correctly detected server-side and appear in the
  in-app list, but nothing pushes a notification to a closed or backgrounded
  app yet.

---

## Roadmap

1. **FCM push delivery** for alerts (tokens already collected; needs
   Firebase Admin SDK wiring on the API side)
2. **Navy design pass** on Tags, Circles, and Alerts screens
3. **Geofencing** — no schema or UI exists yet, ground-up build
4. **Device Offline detection** — periodic check, not an event trigger
5. **Engine Cut** — blocked indefinitely pending a written client safety
   specification (speed threshold, confirmation flow, failure behavior).
   **Do not start this without it.**
6. **Shared tracking base class** for Home/Vehicles, to eliminate the
   duplication risk noted above

---

## Team

- **Nethmi Wijekoon** — Android UI/UX
- **Nuwan Akalanka** — Admin Portal / QA
- **Amoda Rashmika** — Cloud infrastructure / admin support
- **Suwen Jayathunga** — TCP Server, .NET Core API, Android backend, landing page
- **Chandana Deshapriya** — Supervisor
- **Polwatte Gedara Nuwan Aloka** — Owner / Founder

---

## Getting Started

1. Clone the repo.
2. Open in Android Studio (min SDK 26).
3. Requires `google-services.json` (Firebase config) and a Google Maps API
   key — request these from the team, do not commit them to the repo.
4. Build variants and signing are handled by the CI/CD pipeline — **do not
   attempt to manually configure release signing locally** unless you know
   exactly what you're doing and have confirmed it won't conflict with the
   automated `.aab` build.
