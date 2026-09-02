import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const docsRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repoRoot = resolve(docsRoot, '..');
const gradleProperties = await readFile(resolve(repoRoot, 'gradle.properties'), 'utf8');
const version = gradleProperties
  .split(/\r?\n/)
  .map((line) => line.trim())
  .find((line) => line.startsWith('version='))
  ?.slice('version='.length)
  .trim();

if (!version) {
  throw new Error('Could not find version= in gradle.properties');
}

const output = resolve(docsRoot, 'src/data/version.json');
await mkdir(dirname(output), { recursive: true });
await writeFile(
  output,
  `${JSON.stringify({ version }, null, 2)}\n`,
  'utf8',
);
