const { test } = require("node:test");
const assert = require("node:assert/strict");

function isValidCoordinate(lat, lng) {
  return (
    typeof lat === "number" &&
    typeof lng === "number" &&
    Number.isFinite(lat) &&
    Number.isFinite(lng) &&
    lat >= -90 &&
    lat <= 90 &&
    lng >= -180 &&
    lng <= 180
  );
}

function isValidIncidentId(id) {
  return typeof id === "string" && id.length >= 1 && id.length <= 128 && /^[a-zA-Z0-9_-]+$/.test(id);
}

test("SOS dispatch validation rejects invalid coordinates and malformed IDs", () => {
  assert.equal(isValidCoordinate(120, 0), false);
  assert.equal(isValidCoordinate(37.7749, -122.4194), true);
  assert.equal(isValidIncidentId("sos_123-abc"), true);
  assert.equal(isValidIncidentId("../escape"), false);
});
