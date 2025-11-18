import React, { useEffect, useRef } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useDispatch, useSelector } from 'react-redux';
import { LocationCard } from '../components/LocationCard';
import { SpeedDisplay } from '../components/SpeedDisplay';
import { StatisticsCard } from '../components/StatisticsCard';
import { UnitToggle } from '../components/UnitToggle';
import LocationService from '../services/LocationService';
import StorageService from '../services/StorageService';
import { RootState } from '../store';
import {
    addLocationPoint,
    clearError,
    saveSession,
    setError,
    startTracking,
    stopTracking,
    updateLocation,
} from '../store/actions';
import { UNIT_SYSTEMS } from '../utils/constants';
import { convertDistance, formatDuration, getDistanceUnitLabel } from '../utils/conversions';

export default function TrackingHomeScreen() {
  const dispatch = useDispatch();
  const { isTracking, currentSession, currentLocation, unitSystem } = useSelector(
    (state: RootState) => state.tracking
  );

  const sessionRef = useRef(currentSession);

  useEffect(() => {
    sessionRef.current = currentSession;
  }, [currentSession]);

  // Request permissions on mount
  useEffect(() => {
    const requestPerms = async () => {
      const hasPermission = await LocationService.requestPermissions();
      if (!hasPermission) {
        dispatch(setError('Location permission denied'));
      }
    };
    requestPerms();

    return () => {
      LocationService.cleanup();
    };
  }, [dispatch]);

  // Handle location watching based on tracking state
  useEffect(() => {
    if (!isTracking) {
      LocationService.stopWatching();
      return;
    }

    const startWatching = async () => {
      const success = await LocationService.startWatching(
        (location) => {
          dispatch(updateLocation(location));
          dispatch(addLocationPoint(location));
        },
        (error) => {
          dispatch(setError(error));
        },
        1000 // Update every second
      );

      if (!success) {
        dispatch(setError('Failed to start tracking'));
      }
    };

    startWatching();

    return () => {
      LocationService.stopWatching();
    };
  }, [isTracking, dispatch]);

  const handleStartTracking = async () => {
    const startSuccess = await LocationService.startWatching(
      (location) => {
        dispatch(updateLocation(location));
        dispatch(addLocationPoint(location));
      },
      (error) => {
        dispatch(setError(error));
      }
    );

    if (startSuccess) {
      dispatch(startTracking());
      dispatch(clearError());
    } else {
      dispatch(setError('Failed to start location tracking'));
    }
  };

  const handleStopTracking = async () => {
    await LocationService.stopWatching();

    if (currentSession) {
      const endTime = Date.now();
      const sessionWithEnd = {
        ...currentSession,
        endTime,
      };

      const saved = await StorageService.saveSession(sessionWithEnd);
      if (saved) {
        dispatch(saveSession(sessionWithEnd));
        dispatch(clearError());
      } else {
        dispatch(setError('Failed to save session'));
      }
    }

    dispatch(stopTracking());
  };

  const handleUnitToggle = () => {
    const newUnit =
      unitSystem === UNIT_SYSTEMS.METRIC
        ? UNIT_SYSTEMS.IMPERIAL
        : UNIT_SYSTEMS.METRIC;
    dispatch({ type: 'SET_UNIT_SYSTEM', payload: newUnit } as any);
  };

  const totalDistance = currentSession
    ? convertDistance(currentSession.statistics.totalDistance, unitSystem)
    : 0;
  const distanceUnit = getDistanceUnitLabel(unitSystem);
  const duration = currentSession ? currentSession.statistics.duration : 0;

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
      {/* Header Section */}
      <View style={styles.header}>
        <Text style={styles.title}>
          {isTracking ? 'Live Tracking' : 'Location & Speed Tracker'}
        </Text>
        <Text style={styles.subtitle}>
          {isTracking ? formatDuration(duration) : 'Real-time GPS tracking with speed monitoring'}
        </Text>
      </View>

      {/* Unit Toggle */}
      <UnitToggle currentUnit={unitSystem} onToggle={handleUnitToggle} />

      {/* Current Location Display */}
      {currentLocation && (
        <>
          <SpeedDisplay
            speed={currentLocation.speed}
            unitSystem={unitSystem}
            onUnitToggle={handleUnitToggle}
          />
          <LocationCard location={currentLocation} unitSystem={unitSystem} />
        </>
      )}

      {/* Tracking Session Stats - Only show when tracking */}
      {isTracking && currentSession && (
        <>
          <View style={styles.summaryBox}>
            <View style={styles.summaryItem}>
              <Text style={styles.summaryLabel}>Total Distance</Text>
              <Text style={styles.summaryValue}>
                {totalDistance.toFixed(2)} {distanceUnit}
              </Text>
            </View>
            <View style={styles.summaryItem}>
              <Text style={styles.summaryLabel}>Data Points</Text>
              <Text style={styles.summaryValue}>{currentSession.points.length}</Text>
            </View>
          </View>

          <StatisticsCard session={currentSession} unitSystem={unitSystem} />
        </>
      )}

      {/* Action Button - Start or Stop Tracking */}
      {!isTracking ? (
        <Pressable style={[styles.button, styles.startButton]} onPress={handleStartTracking}>
          <Text style={styles.buttonText}>Start Tracking</Text>
        </Pressable>
      ) : (
        <Pressable style={[styles.button, styles.stopButton]} onPress={handleStopTracking}>
          <Text style={styles.buttonText}>Stop Tracking</Text>
        </Pressable>
      )}

      {/* Tips - Only show when not tracking */}
      {!isTracking && (
        <View style={styles.infoBox}>
          <Text style={styles.infoTitle}>Tips:</Text>
          <Text style={styles.infoText}>• Enable location services for best accuracy</Text>
          <Text style={styles.infoText}>• Keep the app open while tracking</Text>
          <Text style={styles.infoText}>• Tap speed to toggle between km/h and mph</Text>
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#1A252F',
  },
  contentContainer: {
    padding: 15,
  },
  header: {
    marginBottom: 20,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#ECF0F1',
  },
  subtitle: {
    fontSize: 14,
    color: '#95A5A6',
    marginTop: 5,
  },
  button: {
    paddingVertical: 15,
    paddingHorizontal: 20,
    borderRadius: 10,
    alignItems: 'center',
    marginVertical: 15,
    elevation: 3,
  },
  startButton: {
    backgroundColor: '#00D4FF',
  },
  stopButton: {
    backgroundColor: '#E74C3C',
  },
  buttonText: {
    color: '#FFFFFF',
    fontWeight: 'bold',
    fontSize: 16,
  },
  summaryBox: {
    backgroundColor: '#34495E',
    borderRadius: 10,
    padding: 15,
    marginVertical: 10,
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  summaryItem: {
    alignItems: 'center',
  },
  summaryLabel: {
    color: '#95A5A6',
    fontSize: 12,
    marginBottom: 5,
  },
  summaryValue: {
    color: '#00D4FF',
    fontWeight: 'bold',
    fontSize: 18,
  },
  infoBox: {
    backgroundColor: '#34495E',
    borderRadius: 10,
    padding: 15,
    marginTop: 20,
  },
  infoTitle: {
    color: '#ECF0F1',
    fontWeight: 'bold',
    marginBottom: 8,
  },
  infoText: {
    color: '#95A5A6',
    marginVertical: 4,
  },
});
