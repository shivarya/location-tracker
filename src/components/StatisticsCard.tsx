import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { convertDistance, getDistanceUnitLabel, formatDuration } from '../utils/conversions';
import { TrackingSession } from '../store/types';

interface StatisticsCardProps {
  session: TrackingSession | null;
  unitSystem: string;
}

export const StatisticsCard: React.FC<StatisticsCardProps> = ({
  session,
  unitSystem,
}) => {
  if (!session) {
    return null;
  }

  const totalDistance = convertDistance(
    session.statistics.totalDistance,
    unitSystem
  );
  const distanceUnit = getDistanceUnitLabel(unitSystem);

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Session Statistics</Text>

      <View style={styles.grid}>
        <View style={styles.statBox}>
          <Text style={styles.statLabel}>Duration</Text>
          <Text style={styles.statValue}>
            {formatDuration(session.statistics.duration)}
          </Text>
        </View>

        <View style={styles.statBox}>
          <Text style={styles.statLabel}>Distance</Text>
          <Text style={styles.statValue}>
            {totalDistance.toFixed(2)} {distanceUnit}
          </Text>
        </View>

        <View style={styles.statBox}>
          <Text style={styles.statLabel}>Avg Speed</Text>
          <Text style={styles.statValue}>
            {session.statistics.avgSpeed.toFixed(1)} m/s
          </Text>
        </View>

        <View style={styles.statBox}>
          <Text style={styles.statLabel}>Max Speed</Text>
          <Text style={styles.statValue}>
            {session.statistics.maxSpeed.toFixed(1)} m/s
          </Text>
        </View>

        <View style={styles.statBox}>
          <Text style={styles.statLabel}>Min Speed</Text>
          <Text style={styles.statValue}>
            {session.statistics.minSpeed.toFixed(1)} m/s
          </Text>
        </View>

        <View style={styles.statBox}>
          <Text style={styles.statLabel}>Points</Text>
          <Text style={styles.statValue}>{session.points.length}</Text>
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#2C3E50',
    borderRadius: 10,
    padding: 15,
    marginBottom: 15,
    elevation: 2,
  },
  title: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#ECF0F1',
    marginBottom: 12,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
  },
  statBox: {
    width: '48%',
    backgroundColor: '#34495E',
    borderRadius: 8,
    padding: 12,
    marginBottom: 10,
    alignItems: 'center',
  },
  statLabel: {
    fontSize: 12,
    color: '#95A5A6',
    marginBottom: 5,
    fontWeight: '600',
  },
  statValue: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#00D4FF',
  },
});
