import { readdirSync, statSync } from "node:fs";
import { join } from "node:path";

const distAssetsDir = join(process.cwd(), "dist", "assets");

const budgets = {
  entryJsMax: 260 * 1024,
  totalJsMax: 650 * 1024,
  totalCssMax: 40 * 1024,
};

function toKb(bytes) {
  return `${(bytes / 1024).toFixed(2)} KB`;
}

const assetFiles = readdirSync(distAssetsDir, { withFileTypes: true })
  .filter((entry) => entry.isFile())
  .map((entry) => entry.name);

const jsFiles = assetFiles.filter((name) => name.endsWith(".js"));
const cssFiles = assetFiles.filter((name) => name.endsWith(".css"));

const entryJsFile = jsFiles.find((name) => name.startsWith("index-"));
const entryJsSize = entryJsFile ? statSync(join(distAssetsDir, entryJsFile)).size : 0;

const totalJsSize = jsFiles.reduce((sum, file) => sum + statSync(join(distAssetsDir, file)).size, 0);
const totalCssSize = cssFiles.reduce((sum, file) => sum + statSync(join(distAssetsDir, file)).size, 0);

console.log("Bundle budget report");
console.log(`- Entry JS: ${toKb(entryJsSize)} (max ${toKb(budgets.entryJsMax)})`);
console.log(`- Total JS: ${toKb(totalJsSize)} (max ${toKb(budgets.totalJsMax)})`);
console.log(`- Total CSS: ${toKb(totalCssSize)} (max ${toKb(budgets.totalCssMax)})`);

const failures = [];
if (entryJsSize > budgets.entryJsMax) {
  failures.push(`Entry JS exceeds budget: ${toKb(entryJsSize)} > ${toKb(budgets.entryJsMax)}`);
}
if (totalJsSize > budgets.totalJsMax) {
  failures.push(`Total JS exceeds budget: ${toKb(totalJsSize)} > ${toKb(budgets.totalJsMax)}`);
}
if (totalCssSize > budgets.totalCssMax) {
  failures.push(`Total CSS exceeds budget: ${toKb(totalCssSize)} > ${toKb(budgets.totalCssMax)}`);
}

if (failures.length > 0) {
  console.error("Bundle budget check failed:");
  failures.forEach((line) => console.error(`  - ${line}`));
  process.exit(1);
}

console.log("Bundle budget check passed.");
