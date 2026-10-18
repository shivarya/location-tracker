import React, { useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, Pressable, Alert } from 'react-native';
import { useDispatch, useSelector } from 'react-redux';
import Constants from 'expo-constants';
import { RootState } from '../store';
import { setUnitSystem, loadSessions } from '../store/actions';
import StorageService from '../services/StorageService';
import { UnitToggle } from '../components/UnitToggle';
import { Ionicons } from '@expo/vector-icons';

export default function SettingsScreen() {
  const dispatch = useDispatch();
  const { unitSystem, sessions } = useSelector((state: RootState) => state.tracking);

  useEffect(() => {
    // Load unit preference on mount
    const loadPreferences = async () => {
      const savedUnit = await StorageService.getUnitPreference();
      if (savedUnit) {
        dispatch(setUnitSystem(savedUnit));
      }
    };
    loadPreferences();
  }, [dispatch]);

  const handleUnitChange = async (newUnit: string) => {
    dispatch(setUnitSystem(newUnit));
    await StorageService.setUnitPreference(newUnit);
  };

  const handleClearAllData = () => {
    Alert.alert('Clear All Data?', 'This action will delete all tracking sessions permanently.', [
      {
        text: 'Cancel',
        style: 'cancel',
      },
      {
        text: 'Clear All',
        onPress: async () => {
          await StorageService.clearAllSessions();
          dispatch(loadSessions([]));
          Alert.alert('Success', 'All data has been cleared');
        },
        style: 'destructive',
      },
    ]);
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Units</Text>
        <UnitToggle currentUnit={unitSystem} onToggle={handleUnitChange} />
        <Text style={styles.sectionDescription}>
          Choose between Metric (km/h, m) and Imperial (mph, ft) units
        </Text>
      </View>

      <View style={styles.divider} />

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>About</Text>
        <View style={styles.infoItem}>
          <Ionicons name="information-circle-outline" size={20} color="#00D4FF" />
          <View style={styles.infoContent}>
            <Text style={styles.infoLabel}>Total Sessions</Text>
            <Text style={styles.infoValue}>{sessions.length}</Text>
          </View>
        </View>

        <View style={styles.infoItem}>
          <Ionicons name="bar-chart-outline" size={20} color="#00D4FF" />
          <View style={styles.infoContent}>
            <Text style={styles.infoLabel}>App Version</Text>
            <Text style={styles.infoValue}>{Constants.expoConfig?.version || '1.0.5'}</Text>
          </View>
        </View>
      </View>

      <View style={styles.divider} />

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>GPS Tracking</Text>
        <View style={styles.featureList}>
          <View style={styles.featureItem}>
            <Ionicons name="checkmark-circle" size={18} color="#27AE60" />
            <Text style={styles.featureText}>Real-time speed tracking (m/s)</Text>
          </View>
          <View style={styles.featureItem}>
            <Ionicons name="checkmark-circle" size={18} color="#27AE60" />
            <Text style={styles.featureText}>Latitude & Longitude coordinates</Text>
          </View>
          <View style={styles.featureItem}>
            <Ionicons name="checkmark-circle" size={18} color="#27AE60" />
            <Text style={styles.featureText}>Altitude measurement</Text>
          </View>
          <View style={styles.featureItem}>
            <Ionicons name="checkmark-circle" size={18} color="#27AE60" />
            <Text style={styles.featureText}>Compass heading & direction</Text>
          </View>
          <View style={styles.featureItem}>
            <Ionicons name="checkmark-circle" size={18} color="#27AE60" />
            <Text style={styles.featureText}>GPS accuracy indicator</Text>
          </View>
          <View style={styles.featureItem}>
            <Ionicons name="checkmark-circle" size={18} color="#27AE60" />
            <Text style={styles.featureText}>Session history storage</Text>
          </View>
        </View>
      </View>

      <View style={styles.divider} />

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Data Management</Text>
        <Pressable style={styles.dangerButton} onPress={handleClearAllData}>
          <Ionicons name="trash-outline" size={20} color="#FFFFFF" />
          <Text style={styles.dangerButtonText}>Clear All Data</Text>
        </Pressable>
        <Text style={styles.dangerDescription}>
          Delete all saved tracking sessions and data
        </Text>
      </View>

      <View style={styles.footer}>
        <Text style={styles.footerText}>Location Tracker v1.0.0</Text>
        <Text style={styles.footerText}>© 2025 All Rights Reserved</Text>
      </View>
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
  section: {
    marginBottom: 20,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#ECF0F1',
    marginBottom: 12,
  },
  sectionDescription: {
    fontSize: 12,
    color: '#95A5A6',
    marginTop: 10,
  },
  divider: {
    height: 1,
    backgroundColor: '#34495E',
    marginVertical: 15,
  },
  infoItem: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#34495E',
    borderRadius: 8,
    padding: 15,
    marginBottom: 10,
  },
  infoContent: {
    flex: 1,
    marginLeft: 12,
  },
  infoLabel: {
    fontSize: 14,
    color: '#95A5A6',
    marginBottom: 4,
  },
  infoValue: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#ECF0F1',
  },
  featureList: {
    backgroundColor: '#34495E',
    borderRadius: 8,
    padding: 15,
  },
  featureItem: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  featureText: {
    marginLeft: 12,
    fontSize: 14,
    color: '#ECF0F1',
    flex: 1,
  },
  dangerButton: {
    backgroundColor: '#E74C3C',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
    borderRadius: 8,
    gap: 10,
  },
  dangerButtonText: {
    color: '#FFFFFF',
    fontWeight: 'bold',
    fontSize: 14,
  },
  dangerDescription: {
    fontSize: 12,
    color: '#95A5A6',
    marginTop: 8,
  },
  footer: {
    marginTop: 40,
    alignItems: 'center',
  },
  footerText: {
    fontSize: 12,
    color: '#7F8C8D',
    marginVertical: 2,
  },
});
