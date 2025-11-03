import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import openapiTS, { astToString } from 'openapi-typescript';

const SPEC_URL = process.env.OPENAPI_URL ?? 'http://localhost:18080/v3/api-docs';
const OUTPUT_PATH = process.env.OPENAPI_OUTPUT ?? 'src/lib/generated/openapi-types.ts';

async function generate() {
  const schema = await openapiTS(SPEC_URL, {
    alphabetize: true,
    enum: 'typescript',
  });

  const output = Array.isArray(schema) ? astToString(schema) : String(schema);

  await mkdir(path.dirname(OUTPUT_PATH), { recursive: true });
  await writeFile(OUTPUT_PATH, output, 'utf8');

  console.log(`OpenAPI types written to ${OUTPUT_PATH}`);
}

generate().catch(error => {
  console.error('Failed to generate OpenAPI types:', error);
  process.exitCode = 1;
});
