import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

interface GPSStrengthIndicatorProps {
  accuracy: number; // Accuracy in meters
}

export const GPSStrengthIndicator: React.FC<GPSStrengthIndicatorProps> = ({ accuracy }) => {
  const getStrengthLevel = () => {
    if (accuracy <= 5) return { level: 'Excellent', color: '#27AE60', bars: 4, icon: 'cellular' };
    if (accuracy <= 10) return { level: 'Good', color: '#2ECC71', bars: 3, icon: 'cellular' };
    if (accuracy <= 20) return { level: 'Fair', color: '#F39C12', bars: 2, icon: 'cellular' };
    if (accuracy <= 50) return { level: 'Poor', color: '#E67E22', bars: 1, icon: 'cellular' };
    return { level: 'Weak', color: '#E74C3C', bars: 0, icon: 'cellular-outline' };
  };

  const strength = getStrengthLevel();

  return (
    <View style={styles.container}>
      <View style={styles.iconContainer}>
        <Ionicons name={strength.icon as any} size={20} color={strength.color} />
      </View>
      <View style={styles.infoContainer}>
        <View style={styles.topRow}>
          <Text style={[styles.strengthText, { color: strength.color }]}>
            {strength.level}
          </Text>
          <View style={styles.barsContainer}>
            {[1, 2, 3, 4].map((bar) => (
              <View
                key={bar}
                style={[
                  styles.bar,
                  bar < 4 && styles.barMargin,
                  {
                    backgroundColor: bar <= strength.bars ? strength.color : '#34495E',
                    height: 4 + bar * 3,
                  },
                ]}
              />
            ))}
          </View>
        </View>
        <Text style={styles.accuracyText}>±{accuracy.toFixed(1)}m accuracy</Text>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#2C3E50',
    borderRadius: 10,
    padding: 12,
    marginBottom: 15,
    flexDirection: 'row',
    alignItems: 'center',
    elevation: 2,
  },
  iconContainer: {
    marginRight: 12,
    width: 30,
    alignItems: 'center',
  },
  infoContainer: {
    flex: 1,
  },
  topRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  strengthText: {
    fontSize: 16,
    fontWeight: 'bold',
  },
  barsContainer: {
    flexDirection: 'row',
    alignItems: 'flex-end',
  },
  bar: {
    width: 6,
    borderRadius: 2,
  },
  barMargin: {
    marginRight: 3,
  },
  accuracyText: {
    fontSize: 12,
    color: '#95A5A6',
  },
});
