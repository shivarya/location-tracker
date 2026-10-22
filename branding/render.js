// Renders the Play Store PNGs from the SVG masters in this folder:
//   node branding/render.js mobile/node_modules      (any node_modules with @resvg/resvg-js and sharp)
// play-icon-512.png: full-bleed 512×512 square (Play applies its own mask).
// feature-graphic.png: 1024×500, flattened to 24-bit (Play rejects alpha in feature graphics).
const path = require('path');
const fs = require('fs');
const modules = path.resolve(process.argv[2] || 'node_modules');
const { Resvg } = require(path.join(modules, '@resvg/resvg-js'));
const sharp = require(path.join(modules, 'sharp'));
const here = (f) => path.join(__dirname, f);
const render = (svg, width) =>
  new Resvg(fs.readFileSync(here(svg), 'utf8'), { fitTo: { mode: 'width', value: width }, font: { loadSystemFonts: true } })
    .render().asPng();

(async () => {
  await sharp(render('speedtrack-icon.svg', 512)).png().toFile(here('play-icon-512.png'));
  await sharp(render('feature-graphic.svg', 1024)).flatten({ background: '#6D28D9' }).removeAlpha().png().toFile(here('feature-graphic.png'));
  console.log('Wrote play-icon-512.png and feature-graphic.png');
})();
