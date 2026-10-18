import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { UNIT_SYSTEMS } from '../utils/constants';

interface UnitToggleProps {
  currentUnit: string;
  onToggle: (unit: string) => void;
}

export const UnitToggle: React.FC<UnitToggleProps> = ({
  currentUnit,
  onToggle,
}) => {
  const isMetric = currentUnit === UNIT_SYSTEMS.METRIC;

  return (
    <View style={styles.container}>
      <Pressable
        style={[styles.button, isMetric && styles.buttonActive]}
        onPress={() => onToggle(UNIT_SYSTEMS.METRIC)}
      >
        <Text
          style={[styles.buttonText, isMetric && styles.buttonTextActive]}
        >
          Metric (km/h)
        </Text>
      </Pressable>
      <Pressable
        style={[styles.button, !isMetric && styles.buttonActive]}
        onPress={() => onToggle(UNIT_SYSTEMS.IMPERIAL)}
      >
        <Text
          style={[styles.buttonText, !isMetric && styles.buttonTextActive]}
        >
          Imperial (mph)
        </Text>
      </Pressable>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    marginVertical: 10,
    gap: 10,
  },
  button: {
    flex: 1,
    paddingVertical: 10,
    paddingHorizontal: 15,
    borderRadius: 8,
    backgroundColor: '#34495E',
    borderWidth: 2,
    borderColor: '#7F8C8D',
    alignItems: 'center',
  },
  buttonActive: {
    backgroundColor: '#00D4FF',
    borderColor: '#00D4FF',
  },
  buttonText: {
    color: '#95A5A6',
    fontWeight: '600',
    fontSize: 12,
  },
  buttonTextActive: {
    color: '#FFFFFF',
  },
});
