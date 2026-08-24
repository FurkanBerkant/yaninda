import assert from "node:assert/strict";
import { afterEach, describe, it } from "node:test";
import { deleteApp, initializeApp } from "firebase/app";
import {
  connectAuthEmulator,
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
  it("creates an anonymous installation session", async () => {
    const auth = createAuth();

    const credential = await signInAnonymously(auth);

    assert.equal(credential.user.isAnonymous, true);
    assert.ok(credential.user.uid);
  });

  it("gives separate installations separate identities", async () => {
    const firstCredential =
      await signInAnonymously(createAuth());
    const secondCredential =
      await signInAnonymously(createAuth());

    assert.notEqual(
      firstCredential.user.uid,
      secondCredential.user.uid,
    );
  });
});
