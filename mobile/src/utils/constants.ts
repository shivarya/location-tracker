// Unit conversion constants
export const CONVERSIONS = {
  // Speed conversions
  MS_TO_KMH: 3.6,
  MS_TO_MPH: 2.237,
  KMH_TO_MPH: 0.621371,
  MPH_TO_KMH: 1.60934,

  // Distance conversions
  METERS_TO_KM: 0.001,
  METERS_TO_MILES: 0.000621371,
  METERS_TO_FEET: 3.28084,
  KM_TO_MILES: 0.621371,

  // Earth radius for distance calculations
  EARTH_RADIUS_M: 6371000,
  EARTH_RADIUS_KM: 6371,
};

// UI Constants
export const UPDATE_INTERVAL = 1000; // 1 second update interval
export const LOCATION_ACCURACY_THRESHOLD = 50; // meters

// Screen titles
export const SCREENS = {
  HOME: 'Home',
  TRACKING: 'Tracking',
  HISTORY: 'History',
  SETTINGS: 'Settings',
};

// Storage keys
export const STORAGE_KEYS = {
  SESSIONS: 'tracking_sessions',
  UNIT_PREFERENCE: 'unit_preference',
  THEME: 'theme_preference',
};

// Unit preferences
export const UNIT_SYSTEMS = {
  METRIC: 'metric', // km/h, m, km
  IMPERIAL: 'imperial', // mph, ft, miles
};

// UUID v4 generator without crypto module for React Native
export const generateUUID = (): string => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};
