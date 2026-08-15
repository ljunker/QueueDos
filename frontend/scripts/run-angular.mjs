import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const versionFile = new URL('../../VERSION', import.meta.url);
const version = readFileSync(versionFile, 'utf8').trim();
const semanticVersionPattern = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*)(?:\.(?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*))*))?$/;

if (!semanticVersionPattern.test(version)) {
  throw new Error(`VERSION must contain a SemVer value without a leading 'v' or build metadata, but was '${version}'.`);
}

const angularArguments = process.argv.slice(2);
if (angularArguments.length === 0) {
  throw new Error('An Angular command such as build or serve is required.');
}

const angularCli = fileURLToPath(new URL('../node_modules/@angular/cli/bin/ng.js', import.meta.url));
const frontendDirectory = fileURLToPath(new URL('..', import.meta.url));
const result = spawnSync(
  process.execPath,
  [angularCli, ...angularArguments, '--define', `QUEUEDOS_VERSION=${JSON.stringify(version)}`],
  { cwd: frontendDirectory, stdio: 'inherit' }
);

if (result.error) {
  throw result.error;
}

process.exit(result.status ?? 1);
