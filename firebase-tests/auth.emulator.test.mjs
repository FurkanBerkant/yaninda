import assert from "node:assert/strict";
import { afterEach, describe, it } from "node:test";
import { deleteApp, initializeApp } from "firebase/app";
import {
  connectAuthEmulator,
  createUserWithEmailAndPassword,
  getAuth,
  signInAnonymously,
} from "firebase/auth";

const apps = [];

function createAuth() {
  const app = initializeApp(
    {
      apiKey: "demo-api-key",
      authDomain: "demo-yaninda.firebaseapp.com",
      projectId: "demo-yaninda",
    },
    `auth-test-${crypto.randomUUID()}`,
  );
  apps.push(app);
  const auth = getAuth(app);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  return auth;
}

afterEach(async () => {
  await Promise.all(apps.splice(0).map((app) => deleteApp(app)));
});

describe("Yaninda Auth emulator", () => {
  it("creates a permanent caregiver account", async () => {
    const auth = createAuth();
    const email = `caregiver-${crypto.randomUUID()}@example.test`;

    const credential = await createUserWithEmailAndPassword(
      auth,
      email,
      "safe-test-password",
    );

    assert.equal(credential.user.email, email);
    assert.equal(credential.user.isAnonymous, false);
  });

  it("creates an anonymous primary-device session", async () => {
    const auth = createAuth();

    const credential = await signInAnonymously(auth);

    assert.equal(credential.user.isAnonymous, true);
    assert.ok(credential.user.uid);
  });
});
