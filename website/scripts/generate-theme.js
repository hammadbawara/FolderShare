// scripts/generate-theme.js
// Uses @material/material-color-utilities to generate Material Design 3 Light Theme tokens
import { argbFromHex, hexFromArgb, themeFromSourceColor, CorePalette } from '@material/material-color-utilities';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Folder Share brand primary seed color (matching BaseBlue Color(0xFF2563EB) in Compose Material 3)
const SEED_COLOR = '#2563EB';

const theme = themeFromSourceColor(argbFromHex(SEED_COLOR));
const palette = CorePalette.of(argbFromHex(SEED_COLOR));
const light = theme.schemes.light.toJSON();

let css = `/**
 * Material Design 3 Light Theme Tokens
 * Generated strictly by @material/material-color-utilities
 * Source Seed Color: ${SEED_COLOR}
 */

:root {
  /* M3 Light Color System */
`;

for (const [key, val] of Object.entries(light)) {
  const kebab = key.replace(/([a-z0-9]|(?=[A-Z]))([A-Z])/g, '$1-$2').toLowerCase();
  css += `  --md-sys-color-${kebab}: ${hexFromArgb(val)};\n`;
}

css += `
  /* M3 Surface Container Roles */
  --md-sys-color-surface-container-lowest: ${hexFromArgb(palette.n1.tone(100))};
  --md-sys-color-surface-container-low: ${hexFromArgb(palette.n1.tone(96))};
  --md-sys-color-surface-container: ${hexFromArgb(palette.n1.tone(94))};
  --md-sys-color-surface-container-high: ${hexFromArgb(palette.n1.tone(92))};
  --md-sys-color-surface-container-highest: ${hexFromArgb(palette.n1.tone(90))};
}
`;

const outputPath = path.resolve(__dirname, '../src/styles/material-theme.css');
fs.writeFileSync(outputPath, css, 'utf-8');
console.log(`Successfully generated Material 3 theme at ${outputPath}`);
