import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { convertSpeed, getSpeedUnitLabel } from '../utils/conversions';

interface SpeedDisplayProps {
  speed: number; // in m/s
  unitSystem: string;
  onUnitToggle?: () => void;
}

export const SpeedDisplay: React.FC<SpeedDisplayProps> = ({
  speed,
  unitSystem,
  onUnitToggle,
}) => {
  const displaySpeed = convertSpeed(speed, unitSystem);
  const unit = getSpeedUnitLabel(unitSystem);

  return (
    <View style={styles.container}>
      <Pressable onPress={onUnitToggle} style={styles.pressable}>
        <Text style={styles.label}>Current Speed</Text>
        <View style={styles.speedContainer}>
          <Text style={styles.speed}>{displaySpeed.toFixed(1)}</Text>
          <Text style={styles.unit}>{unit}</Text>
        </View>
        <Text style={styles.tapHint}>Tap to toggle units</Text>
      </Pressable>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    padding: 20,
    backgroundColor: '#2C3E50',
    borderRadius: 15,
    marginBottom: 15,
    elevation: 3,
  },
  pressable: {
    alignItems: 'center',
  },
  label: {
    fontSize: 14,
    color: '#95A5A6',
    marginBottom: 10,
    fontWeight: '600',
  },
  speedContainer: {
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'center',
  },
  speed: {
    fontSize: 56,
    fontWeight: 'bold',
    color: '#00D4FF',
  },
  unit: {
    fontSize: 20,
    color: '#00D4FF',
    marginLeft: 10,
    fontWeight: '600',
  },
  tapHint: {
    fontSize: 12,
    color: '#7F8C8D',
    marginTop: 10,
  },
});
