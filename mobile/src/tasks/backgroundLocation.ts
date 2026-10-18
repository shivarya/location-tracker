import * as TaskManager from 'expo-task-manager';
import * as Location from 'expo-location';
import AsyncStorage from '@react-native-async-storage/async-storage';

export const BACKGROUND_LOCATION_TASK = 'background-location-task';

// Task must be defined at top-level module scope for background execution
TaskManager.defineTask(BACKGROUND_LOCATION_TASK, async ({ data, error }) => {
  if (error) {
    console.error('Background location error:', error.message);
    return;
  }

  if (data) {
    const { locations } = data as { locations: Location.LocationObject[] };

    if (locations && locations.length > 0) {
      try {
        // Store locations for later sync with Redux store when app returns to foreground
        const existingData = await AsyncStorage.getItem('background_locations');
        const existingLocations = existingData ? JSON.parse(existingData) : [];

        const newPoints = locations.map((loc) => ({
          latitude: loc.coords.latitude,
          longitude: loc.coords.longitude,
          altitude: loc.coords.altitude || 0,
          speed: loc.coords.speed || 0,
          accuracy: loc.coords.accuracy || 0,
          heading: loc.coords.heading || 0,
          timestamp: loc.timestamp,
        }));

        await AsyncStorage.setItem(
          'background_locations',
          JSON.stringify([...existingLocations, ...newPoints])
        );

        console.log(`Background: Stored ${newPoints.length} location points`);
      } catch (error) {
        console.error('Failed to store background locations:', error);
      }
    }
  }
});
