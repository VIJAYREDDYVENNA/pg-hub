/** Component tests (Jest + React Testing Library). Pure-logic tests live in *.test.mjs and run with `node --test`. */
module.exports = {
  testEnvironment: 'jsdom',
  testTimeout: 30000,
  testMatch: ['<rootDir>/src/**/*.test.{js,jsx}'],
  transform: { '^.+\.m?jsx?$': 'babel-jest' },
  moduleFileExtensions: ['js', 'jsx', 'mjs', 'json'],
  moduleNameMapper: { '\.(css|svg|png|jpe?g|webp|gif)$': '<rootDir>/src/test/fileMock.js' },
  setupFilesAfterEnv: ['<rootDir>/src/test/setupTests.js'],
  testPathIgnorePatterns: ['/node_modules/', '/build/'],
};
