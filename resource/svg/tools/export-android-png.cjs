const fs = require("node:fs");
const path = require("node:path");
const sharp = require("sharp");

const resourceRoot = path.resolve(__dirname, "../..");
const svgRoot = path.join(resourceRoot, "svg");
const outputRoot = path.join(resourceRoot, "png", "android", "drawable-nodpi");

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) return walk(fullPath);
    return entry.isFile() && entry.name.toLowerCase().endsWith(".svg") ? [fullPath] : [];
  });
}

fs.mkdirSync(outputRoot, { recursive: true });
Promise.all(walk(svgRoot).map(async (file) => {
  const relative = path.relative(svgRoot, file);
  const parts = relative.split(path.sep);
  const baseName = parts.pop().replace(/\.svg$/i, "").toLowerCase();
  const outputName = `${parts.join("-")}-${baseName}.png`;
  const outputPath = path.join(outputRoot, outputName);
  await sharp(file).png().toFile(outputPath);
  console.log(outputName);
})).catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
