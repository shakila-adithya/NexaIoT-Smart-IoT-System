import assert from "node:assert/strict";
import test from "node:test";

import { startActivation } from "../src/api/provisioningApi.js";

function storage(values = {}) {
  return {
    getItem: (key) => values[key] || null,
    removeItem: () => {},
  };
}

test("activation request uses the authenticated backend contract", async () => {
  const previousSessionStorage = globalThis.sessionStorage;
  const previousLocalStorage = globalThis.localStorage;
  const previousFetch = globalThis.fetch;
  let request;

  globalThis.sessionStorage = storage({ nexaiot_token: "test-token" });
  globalThis.localStorage = storage();
  globalThis.fetch = async (url, options) => {
    request = { url, options };
    return new Response(null, { status: 204 });
  };

  try {
    await startActivation("device-1", "operation-1");
    assert.equal(request.url, "http://localhost:8081/api/devices/device-1/enrollment-operations/operation-1/activation/challenge");
    assert.equal(request.options.method, "POST");
    assert.equal(request.options.body, undefined);
    assert.equal(request.options.headers.Authorization, "Bearer test-token");
    assert.equal(request.options.headers["Content-Type"], "application/json");
  } finally {
    globalThis.sessionStorage = previousSessionStorage;
    globalThis.localStorage = previousLocalStorage;
    globalThis.fetch = previousFetch;
  }
});

test("activation request propagates network failures", async () => {
  const previousSessionStorage = globalThis.sessionStorage;
  const previousLocalStorage = globalThis.localStorage;
  const previousFetch = globalThis.fetch;

  globalThis.sessionStorage = storage();
  globalThis.localStorage = storage();
  globalThis.fetch = async () => {
    throw new Error("network unavailable");
  };

  try {
    await assert.rejects(() => startActivation("device-1", "operation-1"), /network unavailable/);
  } finally {
    globalThis.sessionStorage = previousSessionStorage;
    globalThis.localStorage = previousLocalStorage;
    globalThis.fetch = previousFetch;
  }
});
