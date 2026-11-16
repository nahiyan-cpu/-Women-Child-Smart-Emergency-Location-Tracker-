const express = require("express");
const helmet = require("helmet");
const cors = require("cors");
const rateLimit = require("express-rate-limit");
const { z } = require("zod");
const admin = require("firebase-admin");

const PORT = process.env.PORT || 3000;
const DATABASE_ID =
  process.env.FIRESTORE_DATABASE_ID ||
  "ai-studio-android-safeguar-4d266e3d-c3fc-4788-9145-8a149590a1a5";
const BOOTSTRAP_ADMIN_EMAIL = process.env.ADMIN_EMAIL || "nahiyanctg07@gmail.com";

if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.applicationDefault(),
  });
}

const { getFirestore } = require("firebase-admin/firestore");
const db = getFirestore(admin.app(), DATABASE_ID);

const app = express();
app.use(helmet());
app.use(cors({ origin: process.env.CORS_ORIGIN ? process.env.CORS_ORIGIN.split(",") : false }));
app.use(express.json({ limit: "64kb" }));

const apiLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 60,
  standardHeaders: true,
  legacyHeaders: false,
});
app.use("/api/", apiLimiter);

// --- Firebase ID Token Verification Middleware ---
async function requireAuth(req, res, next) {
  const header = req.headers.authorization || "";
  if (!header.startsWith("Bearer ")) {
    return res.status(401).json({ error: "Missing Bearer ID token" });
  }
  const idToken = header.slice(7).trim();
  try {
    const decoded = await admin.auth().verifyIdToken(idToken);
    req.user = decoded;
    return next();
  } catch (err) {
    return res.status(401).json({ error: "Invalid or expired Firebase ID token" });
  }
}

async function requireAdmin(req, res, next) {
  if (!req.user) {
    return res.status(401).json({ error: "Authentication required" });
  }
  const isBootstrappedAdmin =
    req.user.email_verified === true && req.user.email === BOOTSTRAP_ADMIN_EMAIL;
  if (isBootstrappedAdmin) {
    return next();
  }
  const adminDoc = await db.collection("admins").doc(req.user.uid).get();
  if (adminDoc.exists) {
    return next();
  }
  return res.status(403).json({ error: "Admin privilege required" });
}

// --- Schemas ---
const SosDispatchSchema = z.object({
  incidentId: z.string().min(1).max(128).regex(/^[a-zA-Z0-9_-]+$/),
  shareId: z.string().min(1).max(128).regex(/^[a-zA-Z0-9_-]+$/),
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
  triggerType: z.enum(["one_touch_sos", "geofence_breach", "manual_check_in"]),
  message: z.string().max(300).default("Emergency SOS activated"),
  recipientTokens: z.array(z.string().min(10).max(512)).max(20).optional().default([]),
});

const GeofenceNotifySchema = z.object({
  geofenceName: z.string().min(1).max(60),
  transition: z.enum(["entered", "exited"]),
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
  recipientTokens: z.array(z.string().min(10).max(512)).max(20).optional().default([]),
});

const ModerateZoneSchema = z.object({
  verificationStatus: z.enum(["verified", "rejected", "resolved"]),
});

// --- Routes ---
app.get("/api/v1/health", (_req, res) => {
  res.json({
    status: "ok",
    fcmConfigured: true,
    databaseId: DATABASE_ID,
    timestamp: new Date().toISOString(),
  });
});

app.post("/api/v1/sos/dispatch", requireAuth, async (req, res) => {
  const parsed = SosDispatchSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: "Validation failed", details: parsed.error.flatten() });
  }
  const data = parsed.data;
  const mapsLink = `https://maps.google.com/?q=${data.latitude},${data.longitude}`;
  let notifiedCount = 0;

  if (data.recipientTokens.length > 0) {
    const multicast = {
      tokens: data.recipientTokens,
      notification: {
        title: "URGENT: SafeGuard SOS Alert",
        body: `${data.message} (${mapsLink})`,
      },
      data: {
        incidentId: data.incidentId,
        shareId: data.shareId,
        latitude: String(data.latitude),
        longitude: String(data.longitude),
        triggerType: data.triggerType,
      },
      android: { priority: "high" },
    };
    const result = await admin.messaging().sendEachForMulticast(multicast);
    notifiedCount = result.successCount;
  }

  return res.status(200).json({
    dispatched: true,
    notifiedCount,
    dispatchId: `dsp_${data.incidentId}`,
    message: `SOS alert recorded for ${req.user.uid}`,
  });
});

app.post("/api/v1/geofence/notify", requireAuth, async (req, res) => {
  const parsed = GeofenceNotifySchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: "Validation failed", details: parsed.error.flatten() });
  }
  const data = parsed.data;
  let notifiedCount = 0;
  if (data.recipientTokens.length > 0) {
    const result = await admin.messaging().sendEachForMulticast({
      tokens: data.recipientTokens,
      notification: {
        title: `Geofence ${data.transition}: ${data.geofenceName}`,
        body: `Location update at ${data.latitude.toFixed(5)}, ${data.longitude.toFixed(5)}`,
      },
      android: { priority: "high" },
    });
    notifiedCount = result.successCount;
  }
  return res.json({ dispatched: true, notifiedCount });
});

app.post("/api/v1/admin/danger-zones/:zoneId/moderate", requireAuth, requireAdmin, async (req, res) => {
  const zoneId = req.params.zoneId;
  if (!/^[a-zA-Z0-9_-]{1,128}$/.test(zoneId)) {
    return res.status(400).json({ error: "Invalid zoneId" });
  }
  const parsed = ModerateZoneSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: "Invalid verificationStatus" });
  }
  await db.collection("danger_zones").doc(zoneId).update({
    verificationStatus: parsed.data.verificationStatus,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  return res.json({ updated: true, zoneId, verificationStatus: parsed.data.verificationStatus });
});

app.get("/api/v1/admin/overview", requireAuth, requireAdmin, async (_req, res) => {
  const [activeSosSnap, pendingZonesSnap] = await Promise.all([
    db.collection("sos_global_alerts").where("status", "==", "active").get(),
    db.collection("danger_zones").where("verificationStatus", "==", "pending").get(),
  ]);
  return res.json({
    activeSosCount: activeSosSnap.size,
    pendingDangerZonesCount: pendingZonesSnap.size,
  });
});

if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`SafeGuard SOS dispatch API listening on port ${PORT}`);
  });
}

module.exports = { app };
