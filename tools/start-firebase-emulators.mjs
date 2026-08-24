import {
  existsSync,
} from "node:fs";

import {
  spawn,
} from "node:child_process";

import {
  dirname,
  join,
} from "node:path";

import {
  fileURLToPath,
} from "node:url";

const toolsDirectory =
  dirname(fileURLToPath(import.meta.url));

const projectDirectory =
  dirname(toolsDirectory);

const dataDirectory =
  join(
    projectDirectory,
    "firebase-emulator-data",
  );

const firebaseCli =
  join(
    projectDirectory,
    "node_modules",
    "firebase-tools",
    "lib",
    "bin",
    "firebase.js",
  );

if (!existsSync(firebaseCli)) {
  console.error(
    "Firebase araçları bulunamadı. Önce repository root'unda npm install çalıştırın.",
  );

  process.exit(1);
}

const argumentsForFirebase = [
  firebaseCli,
  "emulators:start",
  "--only",
  "auth,firestore,functions",
  "--project",
  "yaninda-18369",
  `--export-on-exit=${dataDirectory}`,
];

const exportMetadata =
  join(
    dataDirectory,
    "firebase-export-metadata.json",
  );

if (existsSync(exportMetadata)) {
  argumentsForFirebase.push(
    `--import=${dataDirectory}`,
  );
}

const emulatorProcess =
  spawn(
    process.execPath,
    argumentsForFirebase,
    {
      cwd: projectDirectory,
      stdio: "inherit",
    },
  );

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(
    signal,
    () => {
      emulatorProcess.kill(signal);
    },
  );
}

emulatorProcess.on(
  "exit",
  (code, signal) => {
    if (signal) {
      process.kill(process.pid, signal);
      return;
    }

    process.exitCode = code ?? 1;
  },
);
