import { CONVERSIONS, UNIT_SYSTEMS } from './constants';

/**
 * Convert speed from m/s to km/h or mph
 */
export const convertSpeed = (
  speedMs: number,
  targetUnit: string = UNIT_SYSTEMS.METRIC
): number => {
  if (targetUnit === UNIT_SYSTEMS.METRIC) {
    return speedMs * CONVERSIONS.MS_TO_KMH;
  }
  return speedMs * CONVERSIONS.MS_TO_MPH;
};

/**
 * Convert distance from meters to km, miles, or feet
 */
export const convertDistance = (
  distanceM: number,
  targetUnit: string = UNIT_SYSTEMS.METRIC
): number => {
  if (targetUnit === UNIT_SYSTEMS.METRIC) {
    return distanceM * CONVERSIONS.METERS_TO_KM;
  }
  return distanceM * CONVERSIONS.METERS_TO_MILES;
};

/**
 * Convert altitude from meters to feet or keep in meters
 */
export const convertAltitude = (
  altitudeM: number,
  targetUnit: string = UNIT_SYSTEMS.METRIC
): number => {
  if (targetUnit === UNIT_SYSTEMS.METRIC) {
    return altitudeM;
  }
  return altitudeM * CONVERSIONS.METERS_TO_FEET;
};

/**
 * Get unit label for speed
 */
export const getSpeedUnitLabel = (unit: string): string => {
  return unit === UNIT_SYSTEMS.METRIC ? 'km/h' : 'mph';
};

/**
 * Get unit label for distance
 */
export const getDistanceUnitLabel = (unit: string): string => {
  return unit === UNIT_SYSTEMS.METRIC ? 'km' : 'mi';
};

/**
 * Get unit label for altitude
 */
export const getAltitudeUnitLabel = (unit: string): string => {
  return unit === UNIT_SYSTEMS.METRIC ? 'm' : 'ft';
};

/**
 * Calculate distance between two coordinates using Haversine formula
 */
export const calculateDistance = (
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number
): number => {
  const R = CONVERSIONS.EARTH_RADIUS_M;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
};

/**
 * Format a number to fixed decimal places
 */
export const formatNumber = (num: number, decimals: number = 2): number => {
  return Math.round(num * Math.pow(10, decimals)) / Math.pow(10, decimals);
};

/**
 * Format time duration to HH:MM:SS
 */
export const formatDuration = (seconds: number): string => {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = Math.floor(seconds % 60);

  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(
    2,
    '0'
  )}:${String(secs).padStart(2, '0')}`;
};

/**
 * Get compass direction from bearing (0-360 degrees)
 */
export const getDirection = (bearing: number): string => {
  const directions = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
  const index = Math.round(bearing / 45) % 8;
  return directions[index];
};

/**
 * Format coordinates with proper precision
 */
export const formatCoordinate = (value: number, decimals: number = 4): string => {
  return value.toFixed(decimals);
};
