import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { convertAltitude, getAltitudeUnitLabel, formatCoordinate } from '../utils/conversions';
import { LocationPoint } from '../store/types';

interface LocationCardProps {
  location: LocationPoint | null;
  unitSystem: string;
}

export const LocationCard: React.FC<LocationCardProps> = ({
  location,
  unitSystem,
}) => {
  if (!location) {
    return (
      <View style={styles.container}>
        <Text style={styles.noData}>Waiting for location data...</Text>
      </View>
    );
  }

  const altitude = convertAltitude(location.altitude, unitSystem);
  const altitudeUnit = getAltitudeUnitLabel(unitSystem);

  return (
    <View style={styles.container}>
      <View style={styles.row}>
        <View style={styles.column}>
          <Text style={styles.label}>Latitude</Text>
          <Text style={styles.value}>{formatCoordinate(location.latitude)}</Text>
        </View>
        <View style={styles.column}>
          <Text style={styles.label}>Longitude</Text>
          <Text style={styles.value}>{formatCoordinate(location.longitude)}</Text>
        </View>
      </View>

      <View style={styles.divider} />

      <View style={styles.row}>
        <View style={styles.column}>
          <Text style={styles.label}>Altitude</Text>
          <Text style={styles.value}>
            {altitude.toFixed(1)} {altitudeUnit}
          </Text>
        </View>
        <View style={styles.column}>
          <Text style={styles.label}>Accuracy</Text>
          <Text style={styles.value}>{location.accuracy.toFixed(1)} m</Text>
        </View>
      </View>

      <View style={styles.divider} />

      <View style={styles.row}>
        <View style={styles.column}>
          <Text style={styles.label}>Heading</Text>
          <Text style={styles.value}>{location.heading.toFixed(0)}°</Text>
        </View>
        <View style={styles.column}>
          <Text style={styles.label}>Time</Text>
          <Text style={styles.value}>
            {new Date(location.timestamp).toLocaleTimeString()}
          </Text>
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#34495E',
    borderRadius: 10,
    padding: 15,
    marginBottom: 15,
    elevation: 2,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 10,
  },
  column: {
    flex: 1,
  },
  label: {
    fontSize: 12,
    color: '#95A5A6',
    marginBottom: 4,
    fontWeight: '600',
  },
  value: {
    fontSize: 14,
    color: '#ECF0F1',
    fontWeight: '500',
  },
  divider: {
    height: 1,
    backgroundColor: '#2C3E50',
    marginVertical: 8,
  },
  noData: {
    color: '#95A5A6',
    textAlign: 'center',
    fontSize: 14,
    paddingVertical: 20,
  },
});
