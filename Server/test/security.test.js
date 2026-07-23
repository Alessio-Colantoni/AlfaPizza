const test = require("node:test");
const assert = require("node:assert/strict");

const {
  canonicalCollectionName,
  extractBearerToken,
  hashSessionToken,
  safeSecretEquals
} = require("../security");

test("collection aliases are canonicalized before authorization filters", () => {
  assert.equal(canonicalCollectionName("users"), "users");
  assert.equal(canonicalCollectionName("users.json"), "users");
  assert.equal(canonicalCollectionName("swaps.JSON"), "swaps");
  assert.equal(canonicalCollectionName("authSessions"), null);
  assert.equal(canonicalCollectionName("unknown"), null);
});

test("Bearer tokens are parsed strictly", () => {
  assert.equal(extractBearerToken("Bearer abc123"), "abc123");
  assert.equal(extractBearerToken("bearer token-value"), "token-value");
  assert.equal(extractBearerToken("Basic abc123"), "");
  assert.equal(extractBearerToken(""), "");
});

test("session tokens are stored as deterministic hashes", () => {
  assert.equal(hashSessionToken("token"), hashSessionToken("token"));
  assert.notEqual(hashSessionToken("token"), hashSessionToken("other"));
  assert.equal(hashSessionToken("token").length, 64);
});

test("cron secrets require a non-empty constant-time match", () => {
  assert.equal(safeSecretEquals("secret", "secret"), true);
  assert.equal(safeSecretEquals("secret", "wrong"), false);
  assert.equal(safeSecretEquals("", ""), false);
});
