import '@testing-library/jest-dom';

// react-router v7 references TextEncoder/TextDecoder at module load time; jsdom's test
// environment doesn't provide them (they're Node/browser globals, not jsdom-polyfilled).
// Node's own util module has provided both since Node 8.3/11.0, so this is safe everywhere
// the test suite runs (local, CI).
const { TextEncoder, TextDecoder } = require('util');
global.TextEncoder = TextEncoder;
global.TextDecoder = TextDecoder;
