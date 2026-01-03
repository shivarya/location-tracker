import * as Location from 'expo-location';
import * as TaskManager from 'expo-task-manager';
import { LocationPoint } from '../store/types';
import { BACKGROUND_LOCATION_TASK } from '../tasks/backgroundLocation';

class LocationService {
  private locationSubscription: Location.LocationSubscription | null = null;
  private onLocationUpdate: ((location: LocationPoint) => void) | null = null;
  private onError: ((error: string) => void) | null = null;
  private isBackgroundTracking: boolean = false;

  /**
   * Request location permissions including background
   */
  async requestPermissions(): Promise<boolean> {
    try {
      const foregroundPermission = await Location.requestForegroundPermissionsAsync();
      if (foregroundPermission.status !== 'granted') {
        return false;
      }

      // Request background permission for tracking when screen is off
      const backgroundPermission = await Location.requestBackgroundPermissionsAsync();
      if (backgroundPermission.status !== 'granted') {
        console.warn('Background location permission not granted');
        // Still return true - app can work with foreground only
        return true;
      }

      return true;
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
   * Check if background location permissions are granted
   */
  async checkBackgroundPermissions(): Promise<boolean> {
    try {
      const background = await Location.getBackgroundPermissionsAsync();
      return background.status === 'granted';
    } catch (error) {
      console.error('Check background permissions error:', error);
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
   * Get last known location async
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
   * Start background location tracking with foreground service
   */
  async startBackgroundTracking(): Promise<boolean> {
    try {
      const hasBackgroundPermission = await this.checkBackgroundPermissions();
      if (!hasBackgroundPermission) {
        console.warn('Background location permission not granted');
        return false;
      }

      // Check if already registered
      const isRegistered = await TaskManager.isTaskRegisteredAsync(BACKGROUND_LOCATION_TASK);
      if (isRegistered) {
        console.log('Background location task already registered');
        this.isBackgroundTracking = true;
        return true;
      }

      // Start background location updates with foreground service
      await Location.startLocationUpdatesAsync(BACKGROUND_LOCATION_TASK, {
        accuracy: Location.Accuracy.High,
        timeInterval: 5000, // Update every 5 seconds in background
        distanceInterval: 10, // Or every 10 meters
        foregroundService: {
          notificationTitle: 'SpeedTrack',
          notificationBody: 'Recording your route in background',
          notificationColor: '#00D4FF',
        },
        pausesUpdatesAutomatically: false,
        activityType: Location.ActivityType.Fitness,
        showsBackgroundLocationIndicator: true,
      });

      this.isBackgroundTracking = true;
      console.log('Background location tracking started');
      return true;
    } catch (error) {
      console.error('Start background tracking error:', error);
      return false;
    }
  }

  /**
   * Stop background location tracking
   */
  async stopBackgroundTracking(): Promise<void> {
    try {
      const isRegistered = await TaskManager.isTaskRegisteredAsync(BACKGROUND_LOCATION_TASK);
      if (isRegistered) {
        await Location.stopLocationUpdatesAsync(BACKGROUND_LOCATION_TASK);
        console.log('Background location tracking stopped');
      }
      this.isBackgroundTracking = false;
    } catch (error) {
      console.error('Stop background tracking error:', error);
    }
  }

  /**
   * Check if background tracking is active
   */
  isBackgroundTrackingActive(): boolean {
    return this.isBackgroundTracking;
  }

  /**
   * Cleanup
   */
  async cleanup(): Promise<void> {
    await this.stopWatching();
    await this.stopBackgroundTracking();
    this.onLocationUpdate = null;
    this.onError = null;
  }
}

export default new LocationService();
