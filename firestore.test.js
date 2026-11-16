const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-safeguar-4d266e3d-c3fc-4788-9145-8a149590a1a5";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const ADMIN_EMAIL = "nahiyanctg07@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTimestamp() {
  const { Timestamp } = require("firebase-admin/firestore");
  return Timestamp.now();
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

function dbFor(ctx) {
  return ctx.firestore();
}

test("Unauthenticated user: denied reading user profile, contacts, incidents, or danger zones", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("emergency_contacts").get());
  await assertFails(unauth.collection("danger_zones").where("verificationStatus", "==", "verified").get());
  await assertFails(unauth.collection("sos_global_alerts").get());
});

test("User profile: owner can create/read/update own profile, Bob cannot read Alice's PII", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      sosAutoSiren: true,
      locationSharingEnabled: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Bob cannot get Alice's PII
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Shadow update with ghost field must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(),
    })
  );
});

test("Emergency contacts: owner CRUD succeeds, cross-user fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const contactRef = aliceDb.collection("users").doc(ALICE_UID).collection("emergency_contacts").doc("c_1");
  await assertSucceeds(
    contactRef.set({
      userId: ALICE_UID,
      name: "Mom",
      relationship: "Mother",
      phone: "+15551234567",
      isPrimary: true,
      notifyOnSos: true,
      notifyOnGeofence: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("emergency_contacts").get()
  );
});

test("SOS incidents: terminal status locking prevents user reopening resolved incident, admin can override", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const adminDb = testEnv.authenticatedContext("admin_uid_1", {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const incRef = aliceDb.collection("users").doc(ALICE_UID).collection("sos_incidents").doc("inc_1");
  await assertSucceeds(
    incRef.set({
      userId: ALICE_UID,
      triggerType: "one_touch_sos",
      status: "active",
      latitude: 37.7749,
      longitude: -122.4194,
      accuracyMeters: 12.5,
      addressLabel: "Market St",
      sirenTriggered: true,
      batteryPercent: 85,
      notes: "Need help",
      evidenceCount: 0,
      shareCode: "share_1",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Resolve incident
  await assertSucceeds(
    incRef.update({
      status: "resolved",
      updatedAt: new Date(),
      resolvedAt: new Date(),
    })
  );

  // Alice cannot mutate after terminal state 'resolved'
  await assertFails(
    incRef.update({
      status: "active",
      updatedAt: new Date(),
    })
  );

  // Admin can update if correction needed
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).collection("sos_incidents").doc("inc_1").update({
      notes: "Verified resolved by dispatch",
      updatedAt: new Date(),
    })
  );
});

test("Evidence records: requires consentConfirmed == true", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const evRef = aliceDb.collection("users").doc(ALICE_UID).collection("evidence_records").doc("ev_1");

  // Without consent must fail
  await assertFails(
    evRef.set({
      userId: ALICE_UID,
      incidentId: "inc_1",
      captureMode: "camera_photo",
      title: "Photo Evidence",
      summary: "Scene photo",
      localEncryptedUri: "vault/ev_1.enc",
      storagePath: "",
      sha256Digest: "0123456789abcdef0123456789abcdef",
      encryptionAlgo: "AES-256-GCM",
      consentConfirmed: false,
      createdAt: new Date(),
    })
  );

  // With explicit consent succeeds
  await assertSucceeds(
    evRef.set({
      userId: ALICE_UID,
      incidentId: "inc_1",
      captureMode: "camera_photo",
      title: "Photo Evidence",
      summary: "Scene photo",
      localEncryptedUri: "vault/ev_1.enc",
      storagePath: "",
      sha256Digest: "0123456789abcdef0123456789abcdef",
      encryptionAlgo: "AES-256-GCM",
      consentConfirmed: true,
      createdAt: new Date(),
    })
  );
});

test("Danger zones: user cannot self-verify, email spoofing blocked, verified admin can approve", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_1", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  const realAdminDb = testEnv.authenticatedContext("admin_1", {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const dzRef = aliceDb.collection("danger_zones").doc("dz_1");

  // Self-verifying on create must fail
  await assertFails(
    dzRef.set({
      reporterId: ALICE_UID,
      reporterAlias: "Alice",
      title: "Dark Alley",
      description: "Streetlights broken for 3 blocks.",
      category: "poor_lighting",
      severity: "high",
      latitude: 37.78,
      longitude: -122.41,
      radiusMeters: 150,
      verificationStatus: "verified",
      confirmCount: 1,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Pending create succeeds
  await assertSucceeds(
    dzRef.set({
      reporterId: ALICE_UID,
      reporterAlias: "Alice",
      title: "Dark Alley",
      description: "Streetlights broken for 3 blocks.",
      category: "poor_lighting",
      severity: "high",
      latitude: 37.78,
      longitude: -122.41,
      radiusMeters: 150,
      verificationStatus: "pending",
      confirmCount: 1,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Unfiltered list fails query trust test for non-admin
  await assertFails(aliceDb.collection("danger_zones").get());

  // Spoofed unverified admin email cannot approve
  await assertFails(
    spoofedAdminDb.collection("danger_zones").doc("dz_1").update({
      verificationStatus: "verified",
      updatedAt: new Date(),
    })
  );

  // Verified admin can approve
  await assertSucceeds(
    realAdminDb.collection("danger_zones").doc("dz_1").update({
      verificationStatus: "verified",
      updatedAt: new Date(),
    })
  );

  // Filtered list of verified zones succeeds
  await assertSucceeds(
    aliceDb.collection("danger_zones").where("verificationStatus", "==", "verified").get()
  );
});
