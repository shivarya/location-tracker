import * as Location from 'expo-location';
import { LocationPoint } from '../store/types';

class LocationService {
  private locationSubscription: Location.LocationSubscription | null = null;
  private onLocationUpdate: ((location: LocationPoint) => void) | null = null;
  private onError: ((error: string) => void) | null = null;

  /**
   * Request location permissions
   */
  async requestPermissions(): Promise<boolean> {
    try {
      const foregroundPermission = await Location.requestForegroundPermissionsAsync();
      if (foregroundPermission.status !== 'granted') {
        return false;
      }

      // Request background location permission
      const backgroundPermission =
        await Location.requestBackgroundPermissionsAsync();
      return backgroundPermission.status === 'granted';
    } catch (error) {
      console.error('Permission error:', error);
      return false;
    }
  }

  /**
   * Check if location permissions are granted
   */
  async checkPermissions(): Promise<boolean> {
    try {
      const foreground = await Location.getForegroundPermissionsAsync();
      return foreground.status === 'granted';
    } catch (error) {
      console.error('Check permissions error:', error);
      return false;
    }
  }

  /**
   * Start watching location updates
   */
  async startWatching(
    onUpdate: (location: LocationPoint) => void,
    onLocationError?: (error: string) => void,
    updateInterval: number = 1000
  ): Promise<boolean> {
    try {
      // Stop any existing subscription
      if (this.locationSubscription) {
        this.locationSubscription.remove();
      }

      this.onLocationUpdate = onUpdate;
      this.onError = onLocationError || null;

      // Enable background location updates
      await Location.enableNetworkProviderAsync();

      // Start watching position
      this.locationSubscription = await Location.watchPositionAsync(
        {
          accuracy: Location.Accuracy.High,
          timeInterval: updateInterval,
          distanceInterval: 0,
        },
        (location) => {
          const point: LocationPoint = {
            latitude: location.coords.latitude,
            longitude: location.coords.longitude,
            altitude: location.coords.altitude || 0,
            speed: location.coords.speed || 0,
            accuracy: location.coords.accuracy || 0,
            heading: location.coords.heading || 0,
            timestamp: location.timestamp || Date.now(),
          };
          this.onLocationUpdate?.(point);
        }
      );

      return true;
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Unknown error occurred';
      console.error('Start watching error:', error);
      this.onError?.(`Failed to start location tracking: ${errorMessage}`);
      return false;
    }
  }

  /**
   * Stop watching location updates
   */
  async stopWatching(): Promise<void> {
    try {
      if (this.locationSubscription) {
        this.locationSubscription.remove();
        this.locationSubscription = null;
      }
    } catch (error) {
      console.error('Stop watching error:', error);
    }
  }

  /**
   * Get current location once
   */
  async getCurrentLocation(): Promise<LocationPoint | null> {
    try {
      const location = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.High,
      });

      return {
        latitude: location.coords.latitude,
        longitude: location.coords.longitude,
        altitude: location.coords.altitude || 0,
        speed: location.coords.speed || 0,
        accuracy: location.coords.accuracy || 0,
        heading: location.coords.heading || 0,
        timestamp: location.timestamp || Date.now(),
      };
    } catch (error) {
      console.error('Get current location error:', error);
      return null;
    }
  }

  /**
   * Get satellite count (if available)
   */
  async getLastKnownLocationAsync(): Promise<LocationPoint | null> {
    try {
      const location = await Location.getLastKnownPositionAsync();
      if (!location) return null;

      return {
        latitude: location.coords.latitude,
        longitude: location.coords.longitude,
        altitude: location.coords.altitude || 0,
        speed: location.coords.speed || 0,
        accuracy: location.coords.accuracy || 0,
        heading: location.coords.heading || 0,
        timestamp: location.timestamp || Date.now(),
      };
    } catch (error) {
      console.error('Get last known location error:', error);
      return null;
    }
  }

  /**
   * Cleanup
   */
  async cleanup(): Promise<void> {
    await this.stopWatching();
    this.onLocationUpdate = null;
    this.onError = null;
  }
}

export default new LocationService();
