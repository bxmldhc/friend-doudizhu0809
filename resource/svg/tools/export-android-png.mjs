import fs from "node:fs/promises";
import path from "node:path";
import sharp from "sharp";

const resourceRoot = path.resolve(new URL("../../", import.meta.url).pathname);
const svgRoot = path.join(resourceRoot, "svg");
const outputRoot = path.join(resourceRoot, "png", "android", "drawable-nodpi");

async function walk(dir) {
  const entries = await fs.readdir(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) files.push(...await walk(fullPath));
    else if (entry.isFile() && entry.name.toLowerCase().endsWith(".svg")) files.push(fullPath);
  }
  return files;
}

const files = await walk(svgRoot);
await fs.mkdir(outputRoot, { recursive: true });

for (const file of files) {
  const relative = path.relative(svgRoot, file);
  const parts = relative.split(path.sep);
  const baseName = parts.pop().replace(/\.svg$/i, "").toLowerCase();
  const outputName = `${parts.join("-")}-${baseName}.png`;
  const outputPath = path.join(outputRoot, outputName);
  await sharp(file, { density: 96 }).png().toFile(outputPath);
  console.log(outputName);
}
