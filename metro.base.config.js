/* eslint-disable @typescript-eslint/no-var-requires */
const path = require('path')

console.log('Metro bundler watching folder: ' + path.resolve(__dirname))

module.exports = {
  watchFolders: [path.resolve(__dirname)],
  transformer: {
    getTransformOptions: async () => ({
      transform: {
        experimentalImportSupport: true,
        inlineRequires: false,
      },
    }),
  },
}
