const appJson = require('./app.json');

module.exports = ({ config }) => {
  const baseConfig = appJson.expo;
  const googleMapsApiKey =
    process.env.GOOGLE_MAPS_ANDROID_API_KEY ||
    process.env.EXPO_PUBLIC_GOOGLE_MAPS_ANDROID_API_KEY ||
    baseConfig?.android?.config?.googleMaps?.apiKey ||
    'REPLACE_WITH_GOOGLE_MAPS_ANDROID_API_KEY';

  return {
    ...baseConfig,
    android: {
      ...baseConfig.android,
      config: {
        ...(baseConfig.android?.config || {}),
        googleMaps: {
          ...((baseConfig.android?.config || {}).googleMaps || {}),
          apiKey: googleMapsApiKey,
        },
      },
    },
  };
};
