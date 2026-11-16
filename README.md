# SafeGuard SOS — Women & Child Smart Emergency & Location Tracker

Production-oriented Android (Kotlin + Jetpack Compose + MVVM) and Cloud Firestore application with real-time SOS alerting, GPS tracking, geofencing, audible siren, consent-based AES-256-GCM encrypted evidence capture, safe-place discovery, crowdsourced danger zones, admin moderation dashboard, and a companion Node.js FCM dispatch API.

---

## 1. Architecture Overview

- **Android App (`app/`)**:
  - **UI**: Jetpack Compose + Material 3 (`Outfit`, `Plus Jakarta Sans`, `JetBrains Mono`), adaptive edge-to-edge layouts (`NavigationBar` / `NavigationRail`).
  - **Authentication**: Firebase Auth with Google Sign-In via Jetpack `CredentialManager` (`GetSignInWithGoogleOption` + silent `GetGoogleIdOption`).
  - **Database**: Cloud Firestore (named database `ai-studio-android-safeguar-4d266e3d-c3fc-4788-9145-8a149590a1a5`) with zero-trust `firestore.rules`.
  - **Location & Geofencing**: `FusedLocationProviderClient`, `GeofencingClient`, `GeofenceBroadcastReceiver`, and visible Android 14+ `SosLocationService` (`foregroundServiceType="location"`).
  - **Siren & Haptics**: Hardware `AudioTrack` sweeping alarm synthesizer (`USAGE_ALARM`) + SOS Morse vibration pattern (`... --- ...`).
  - **Encrypted Evidence Vault**: Hardware-backed Android Keystore (`AES/GCM/NoPadding`, 256-bit) + SHA-256 integrity digests + explicit consent confirmation gate. Uses zero-permission Android Photo Picker (`PickVisualMedia`), CameraX (`CAMERA`), and explicit foreground audio recording (`RECORD_AUDIO`). **Zero covert recording.**
- **Companion Node.js Backend (`backend/`)**:
  - Express + Helmet + Rate Limiting + Zod validation + Firebase Admin SDK (`verifyIdToken`, FCM `sendEachForMulticast`, admin moderation).

---

## 2. Cloud Firestore Database Schema (`firebase-blueprint.json`)

| Collection Path | Purpose | Access Control |
|---|---|---|
| `/users/{userId}` | Private profile, medical notes, safety toggles, FCM token | Owner only (`list` restricted to Admin) |
| `/users/{userId}/emergency_contacts/{contactId}` | Trusted SOS & geofence contacts | Owner only |
| `/users/{userId}/geofences/{geofenceId}` | Safe zones & restricted zones | Owner only |
| `/users/{userId}/sos_incidents/{incidentId}` | Private SOS history with terminal state locking | Owner read/create/active-update; Admin override |
| `/users/{userId}/sos_incidents/{incidentId}/location_breadcrumbs/{crumbId}` | Append-only live GPS breadcrumbs | Owner create/read; Admin read; immutable |
| `/users/{userId}/evidence_records/{evidenceId}` | Consent-confirmed (`consentConfirmed == true`) encrypted evidence metadata | Owner only |
| `/live_shares/{shareId}` | Non-PII live location beacon shareable by `shareId` | Signed-in `get` by ID; owner/admin `list` & write |
| `/danger_zones/{zoneId}` | Crowdsourced community danger reports | Signed-in read `verified` or own; user confirm increment; Admin moderate |
| `/safe_places/{placeId}` | Verified police stations, hospitals, 24/7 shelters | Signed-in read `verifiedByAdmin == true`; Admin write |
| `/sos_global_alerts/{alertId}` | Sanitized SOS emergency feed (no phone/email PII) | Owner or Admin |

---

## 3. Companion Node.js API Documentation (`backend/server.js`)

All protected routes require `Authorization: Bearer <Firebase_ID_Token>`.

- **`GET /api/v1/health`**
  - Returns `{ status: "ok", fcmConfigured: true, databaseId, timestamp }`.
- **`POST /api/v1/sos/dispatch`** *(Auth Required)*
  - Body: `{ incidentId, shareId, latitude, longitude, triggerType, message, recipientTokens[] }`
  - Dispatches high-priority FCM push notifications to trusted emergency contacts.
- **`POST /api/v1/geofence/notify`** *(Auth Required)*
  - Body: `{ geofenceName, transition: "entered" | "exited", latitude, longitude, recipientTokens[] }`
  - Sends geofence transition push alerts.
- **`POST /api/v1/admin/danger-zones/:zoneId/moderate`** *(Verified Admin Required)*
  - Body: `{ verificationStatus: "verified" | "rejected" | "resolved" }`
  - Approves, rejects, or resolves community danger zone reports.
- **`GET /api/v1/admin/overview`** *(Verified Admin Required)*
  - Returns `{ activeSosCount, pendingDangerZonesCount }`.

---

## 4. Setup Instructions & External Credentials

1. **Google Maps SDK for Android (`MAPS_API_KEY`)**:
   - Obtain a Google Maps SDK for Android key in Google Cloud Console (restricted to package `com.aistudio.safeguard.wctkxr` and SHA-1 `76:5D:0A:4A:91:B3:2E:4C:95:B6:3F:B2:0B:C4:26:8E:C8:94:53:21`).
   - Add `MAPS_API_KEY` in the **AI Studio Secrets panel** (do NOT put keys in `local.properties` or source code).
   - Without a configured `MAPS_API_KEY`, the map screen displays an interactive live GPS tactical coordinate & geofence radar view rather than Google Map tiles.
2. **Firebase Cloud Messaging (FCM) & Firebase Storage**:
   - Firestore Enterprise & Google Sign-In are provisioned automatically.
   - To enable live push delivery and cloud encrypted evidence upload, enable **Firebase Cloud Messaging** and **Firebase Storage** in the Firebase Console (`miniutes-summary-generator`).
3. **Node.js Backend Deployment**:
   - Set `GOOGLE_APPLICATION_CREDENTIALS` and `BACKEND_BASE_URL` (in AI Studio Secrets) to your deployed HTTPS Cloud Run / Node.js URL (`cd backend && npm install && npm start`).
4. **Admin Access**:
   - Verified Google account `nahiyanctg07@gmail.com` or UIDs registered in `/admins/{uid}` (via Firebase Console / Admin SDK) unlock Admin Dashboard moderation and SOS dispatch controls.

---

## 5. Android OS Limitations & Privacy Commitments

- **No Covert Recording**: Camera and microphone access are strictly foreground, user-initiated, and gated by explicit in-app consent confirmation (`consentConfirmed == true`).
- **Background Location & Foreground Services**: Continuous GPS streaming during SOS runs inside a visible `ForegroundService` (`foregroundServiceType="location"`) with a persistent system notification. OS geofences require `ACCESS_FINE_LOCATION` (and Android OS settings may prompt for "Allow all the time" for background geofence triggers when the app is closed).
- **SMS / Phone Calls**: To respect Google Play restricted permission policies, SOS contact messaging uses `Intent.ACTION_SEND` / `ACTION_SENDTO` (SMS/ShareSheet) and emergency dialing uses zero-permission `Intent.ACTION_DIAL` (`tel:911` / `tel:112`).

---

## 6. Running Tests

- **Firestore Security Rules (JS)**:
  ```bash
  FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
  ```
- **Android Robolectric & Repository Rule Tests**:
  ```bash
  gradle :app:testDebugUnitTest
  ```
