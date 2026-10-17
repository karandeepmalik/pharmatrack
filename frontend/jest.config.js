const path = require('path');

module.exports = {
  testEnvironment: 'jsdom',
  transform: {
    // Points at babel.jest.config.js, not the root babel.config.js — see that file's comment
    // for why (it rewrites import.meta.env for Jest only, never for the real Vite build).
    // babel-jest's configFile option doesn't expand Jest's own "<rootDir>" token, so this
    // needs a real absolute path.
    '^.+\\.[jt]sx?$': ['babel-jest', { configFile: path.resolve(__dirname, 'babel.jest.config.js') }],
  },
  moduleNameMapper: {
    '\\.(css|less|scss|sass)$': '<rootDir>/src/__mocks__/styleMock.js',
    '\\.(jpg|jpeg|png|gif|webp|svg)$': '<rootDir>/src/__mocks__/fileMock.js',
  },
  setupFilesAfterEnv: ['<rootDir>/src/setupTests.js'],
  testMatch: ['**/__tests__/**/*.[jt]s?(x)', '**/?(*.)+(spec|test).[jt]s?(x)'],
  moduleFileExtensions: ['js', 'jsx', 'ts', 'tsx', 'json'],
  reporters: [
    'default',
    ['jest-junit', {
      outputDirectory: '<rootDir>/test-results',
      outputName: 'junit.xml',
      suiteName: 'PharmaTrack Frontend Tests',
      classNameTemplate: '{classname}',
      titleTemplate: '{title}',
    }],
  ],
};
