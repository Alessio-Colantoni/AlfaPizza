const crypto = require("crypto");

const SUPPORTED_COLLECTIONS = new Set([
  "users",
  "calendars",
  "weekStructure",
  "swaps",
  "constraints"
]);

function canonicalCollectionName(name) {
  const normalized = String(name || "").replace(/\.json$/i, "");
  return SUPPORTED_COLLECTIONS.has(normalized) ? normalized : null;
}

function extractBearerToken(authorizationHeader) {
  const match = /^Bearer\s+(.+)$/i.exec(String(authorizationHeader || "").trim());
  return match ? match[1].trim() : "";
}

function createSessionToken() {
  return crypto.randomBytes(32).toString("base64url");
}

function hashSessionToken(token) {
  return crypto.createHash("sha256").update(String(token)).digest("hex");
}

function safeSecretEquals(expected, received) {
  const left = Buffer.from(String(expected || ""));
  const right = Buffer.from(String(received || ""));
  return left.length > 0 && left.length === right.length && crypto.timingSafeEqual(left, right);
}

module.exports = {
  canonicalCollectionName,
  createSessionToken,
  extractBearerToken,
  hashSessionToken,
  safeSecretEquals
};
