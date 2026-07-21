# 📍 shalotrack-mobile

**ShaloTrack Lanka** — GPS vehicle tracking, built for real people, not just spec
sheets. Designed with elderly-user accessibility as a core constraint: OTP-only
login, no passwords, ever.

<p>
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Language-Java-ED8B00?logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-blue" />
  <img src="https://img.shields.io/badge/Auth-Firebase%20OTP-FFA000?logo=firebase&logoColor=white" />
  <img src="https://img.shields.io/badge/Maps-Google%20Maps%20SDK-4285F4?logo=googlemaps&logoColor=white" />
  <img src="https://img.shields.io/badge/Realtime-SignalR-512BD4" />
  <img src="https://img.shields.io/badge/Live%20Update%20Latency-%3C2s-brightgreen" />
</p>

---

## 🚨 Read this before you touch anything

> **The CI/CD pipeline is off-limits.** It automates the `.aab` build used for
> Google Play Store uploads. Do not modify pipeline config without explicit
> sign-off. Play Store credentials were shared separately (with Suwen) — they
> are **not**, and must never be, in this repository. No keystores, no
> signing keys, no Play Console tokens, ever committed here.

---

## 📑 Contents

- [Live Status](#-live-status)
- [Tech Stack](#-tech-stack)
- [Architecture](#-architecture)
- [Real-Time Push](#-real-time-push-option-b)
- [Screens](#-screens)
- [Known Limitations](#-known-limitations-real-not-hidden)
- [Roadmap](#-roadmap)
- [Team](#-team)
- [Getting Started](#-getting-started)

---

## 🟢 Live Status

| Area | Status |
|---|:---:|
| Authentication (Firebase Phone OTP + email verification) | ✅ |
| Live vehicle tracking (Home + Vehicles) | ✅ |
| Real-time push updates (SignalR) | ✅ |
| Vehicle trail / route line | ✅ |
| Reverse geocoding (real addresses, not raw coordinates) | ✅ |
| Link Vehicle (customer-safe device linking by IMEI) | ✅ |
| Vehicle Information (IMEI, GPS device status) | ✅ |
| Trip History (list, stats, addresses) | ✅ |
| Trip Playback (route animation, scrubbing, start/end markers) | ✅ |
| Alerts (real-time detection + list, mark-as-read) | ✅ |
| Edit Profile | ✅ |
| Drawer menu (Letstrack-style) | ✅ |
| Alerts push delivery (FCM) | 🚧 |
| Navy design system rollout | 🚧 |
| Geofencing | ⏳ |
| Device Offline detection | ⏳ |
| Subscription / billing | ⏳ |
| **Engine Cut / Immobilize** | ⛔ **Blocked — needs written client safety spec. Do not build without it.** |

<details>
<summary><b>🔍 What "🚧 Partial" actually means for each item</b></summary>

<br>

- **Alerts push delivery** — FCM tokens are collected and stored correctly.
  Nothing sends an actual push yet. If the app is closed, you won't know an
  alert happened until you reopen it.
- **Navy design system** — applied to Home, Vehicles, and Trip History.
  Tags, Circles, and Alerts still use the old color scheme.

</details>

---

## 🛠 Tech Stack

| Layer | Choice |
|---|---|
| Language | Java |
| Min SDK | 26 (Android 8.0) |
| Networking | Retrofit2 + OkHttp, Gson |
| Maps | Google Maps SDK for Android |
| Real-time | Microsoft SignalR Android client |
| Auth | Firebase Phone Auth (OTP) + email verification — **no password anywhere** |
| Push | Firebase Cloud Messaging (token collection built, delivery pending) |
| UI | Standard Views (no Compose), Material Components, `RecyclerView`, `CardView`, `DrawerLayout` |

---

## 🏗 Architecture

All data comes from the ShaloTrack C# API (`api.shalotrack.com`), authenticated
via Firebase JWT bearer tokens on every request. There's no local login — the
app talks to Firebase directly.

**Reusable pieces worth knowing:**

| Class | What it does |
|---|---|
| `VehicleTrailRenderer` | Live trail polyline + animated, rotating car marker. Shared by Home & Vehicles. |
| `AddressResolver` | Debounced reverse-geocoding (free `Geocoder`, not the paid API — deliberate cost call). |
| `RealtimeLocationClient` | SignalR wrapper with auto-reconnect + slow poll fallback. |

<details>
<summary><b>⚠️ A known architectural risk (flagged, not yet fixed)</b></summary>

<br>

`HomeActivity` and `VehiclesActivity` each run their **own independent**
tracking loop instead of sharing one. This has already caused real bugs — a
fix applied to one screen (e.g. the `AddressResolver` wiring) didn't
automatically apply to the other. A shared base class or fragment would
eliminate this entire class of bug. Recommended cleanup once features
stabilize, not urgent today.

</details>

---

## ⚡ Real-Time Push (Option B)

Live location arrives via a SignalR hub (`/hubs/location`), not pure polling.
A Postgres trigger fires on every location write on the API side; a background
listener relays it through SignalR to any client subscribed to that vehicle's
group.

```
Real device → Gateway → Postgres write → NOTIFY trigger → SignalR push → App
```

**Typical latency: under 2 seconds**, device to screen. A 60-second poll still
runs quietly as a safety net if the push connection ever drops.

---

## 📱 Screens

| Screen | Notes |
|---|---|
| `OtpVerificationActivity` / `SignUpActivity` | Phone OTP + email verification |
| `HomeActivity` | Live map, drawer menu, vehicle status |
| `VehiclesActivity` | Live map, detail sheet, action grid |
| `TripHistoryActivity` | Date-filtered trips with distance/duration/addresses |
| `TripDetailActivity` | Full route playback — scrub or auto-play |
| `AlertsActivity` | Real alert history, tap to mark read |
| `TagsActivity`, `CirclesActivity` | Exist, not part of this build cycle |

<details>
<summary><b>🍔 What's actually wired in the drawer menu</b></summary>

<br>

Matches Letstrack's reference layout. **Real:** Add New (Add Vehicle), Reports
(Trip History), Chat & Support, Log out. **Honest placeholders** ("Coming
soon"): Places, Vehicle Subscriptions, App Subscription, Refer and Earn, Shop,
Help Videos, Settings, Privacy & Security. **VoiceTrack with Alexa** shows
"Not available for this app" — it's a Letstrack-owned integration, not a
ShaloTrack feature.

</details>

<details>
<summary><b>🎮 What's real in the Vehicles action grid</b></summary>

<br>

**Real:** History, Nav (opens Google Maps to last known position), Details
(vehicle info dialog). **Placeholders:** VoiceTrack, Value, Places.
**Deliberately inert:** Immobilize — shows a safety message, never sends a
real command. See Engine Cut in Roadmap.

</details>

---

## 🐛 Known Limitations (real, not hidden)

<details open>
<summary><b>Click to collapse</b></summary>

<br>

- **Test device's ACC wire is disconnected** — confirmed by direct byte-level
  inspection of raw GPS packets against the manufacturer's protocol spec,
  cross-checked against the actual parsing code. A floating sense pin defaults
  `HIGH`, so ignition reads a constant `true` regardless of real key state.
  **This is a wiring/installation issue, not a software bug** — resolves
  itself the moment the physical wire is connected.
- **Email can't be changed via Edit Profile.** The API's update endpoint
  doesn't accept an email field (tied to Firebase identity). The form still
  shows it; edits are silently not sent. Should be made read-only in the
  layout as a follow-up.
- **Trip playback rotation is approximated**, not exact — raw tracking points
  don't carry a heading value the way live updates do, so direction is
  computed from the bearing between consecutive points. Looks right in
  practice.
- **Alerts detect but don't deliver.** Ignition/Overspeed/Power-cut/Low-battery
  alerts are correctly detected and appear in-app, but nothing pushes a
  notification if the app is closed or backgrounded.

</details>

---

## 🗺 Roadmap

1. **FCM push delivery** for alerts — tokens already collected, needs
   Firebase Admin SDK wiring server-side
2. **Navy design pass** on Tags, Circles, Alerts
3. **Geofencing** — no schema or UI yet, ground-up build
4. **Device Offline detection** — periodic check, not an event trigger
5. **Shared tracking base class** for Home/Vehicles
6. **Engine Cut** — ⛔ blocked indefinitely pending a written client safety
   spec (speed threshold, confirmation flow, failure behavior). **Do not
   start without it.**

---

## 👥 Team

| Name | Role |
|---|---|
| Nethmi Wijekoon | Android UI/UX |
| Nuwan Akalanka | Admin Portal / QA |
| Amoda Rashmika | Cloud infrastructure / admin support |
| Suwen Jayathunga | TCP Server, .NET Core API, Android backend, landing page |
| Chandana Deshapriya | Supervisor |
| Polwatte Gedara Nuwan Aloka | Owner / Founder |

---

## 🚀 Getting Started

1. Clone the repo, open in Android Studio (min SDK 26).
2. You'll need `google-services.json` and a Google Maps API key — request from
   the team, **never commit these**.
3. Release signing and the `.aab` build are handled entirely by CI/CD.
   **Don't configure release signing locally** unless you're certain it won't
   conflict with the automated pipeline.

---

<p align="center">
  <sub>Built with real hardware, real debugging, and a lot of evidence-checking.</sub>
</p>
