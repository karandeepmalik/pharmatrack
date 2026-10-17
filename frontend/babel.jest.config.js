// Separate from babel.config.js on purpose: Vite's own build never touches this file (Vite
// handles import.meta.env natively, independent of Babel), so rewriting import.meta.env here
// to process.env (via the one extra plugin below) only ever affects the Jest/CommonJS test
// run — it can't accidentally leak into the real browser bundle and break env-var injection
// there.
module.exports = {
  presets: [
    ['@babel/preset-env', { targets: { node: 'current' } }],
    ['@babel/preset-react', { runtime: 'automatic' }],
  ],
  plugins: ['transform-vite-meta-env'],
};
