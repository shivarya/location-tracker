import React, { useEffect, useState, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  Pressable,
  Alert,
} from 'react-native';
import { useSelector, useDispatch } from 'react-redux';
import { RootState } from '../store';
import { deleteSession, loadSessions } from '../store/actions';
import StorageService from '../services/StorageService';
import { TrackingSession } from '../store/types';
import { Ionicons } from '@expo/vector-icons';
import { formatDuration, convertDistance, convertSpeed, getDistanceUnitLabel, getSpeedUnitLabel } from '../utils/conversions';

export default function HistoryScreen() {
  const dispatch = useDispatch();
  const { sessions, unitSystem } = useSelector((state: RootState) => state.tracking);
  const [refreshing, setRefreshing] = useState(false);

  const loadSessionsFromStorageCallback = useCallback(async () => {
    const loadedSessions = await StorageService.getSessions();
    dispatch(loadSessions(loadedSessions));
  }, [dispatch]);

  useEffect(() => {
    loadSessionsFromStorageCallback();
  }, [loadSessionsFromStorageCallback]);

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadSessionsFromStorageCallback();
    setRefreshing(false);
  };

  const handleDeleteSession = (sessionId: string) => {
    Alert.alert('Delete Session?', 'This action cannot be undone.', [
      {
        text: 'Cancel',
        onPress: () => {},
        style: 'cancel',
      },
      {
        text: 'Delete',
        onPress: async () => {
          const success = await StorageService.deleteSession(sessionId);
          if (success) {
            dispatch(deleteSession(sessionId));
          }
        },
        style: 'destructive',
      },
    ]);
  };

  const handleExportJSON = async () => {
    await StorageService.exportSessionsAsJSON();
    Alert.alert('Export Data', `Total sessions: ${sessions.length}\n\nJSON data prepared for export`, [
      { text: 'OK' },
    ]);
  };

  const handleExportCSV = async () => {
    await StorageService.exportSessionsAsCSV();
    Alert.alert('Export Data', `CSV with ${sessions.length} sessions prepared for export`, [
      { text: 'OK' },
    ]);
  };

  const SessionItem = ({ session }: { session: TrackingSession }) => {
    const startTime = new Date(session.startTime);
    const duration = session.statistics.duration;
    const distance = convertDistance(session.statistics.totalDistance, unitSystem);
    const distanceUnit = getDistanceUnitLabel(unitSystem);
    const maxSpeed = convertSpeed(session.statistics.maxSpeed, unitSystem);
    const speedUnit = getSpeedUnitLabel(unitSystem);

    return (
      <View style={styles.sessionItem}>
        <View style={styles.sessionContent}>
          <Text style={styles.sessionDate}>
            {startTime.toLocaleDateString()} {startTime.toLocaleTimeString()}
          </Text>
          <View style={styles.sessionStats}>
            <Text style={styles.sessionStat}>
              ⏱️ {formatDuration(duration)}
            </Text>
            <Text style={styles.sessionStat}>
              📍 {distance.toFixed(2)} {distanceUnit}
            </Text>
            <Text style={styles.sessionStat}>
              🚀 {maxSpeed.toFixed(1)} {speedUnit}
            </Text>
          </View>
        </View>
        <Pressable
          onPress={() => handleDeleteSession(session.id)}
          style={styles.deleteButton}
        >
          <Ionicons name="trash" size={20} color="#E74C3C" />
        </Pressable>
      </View>
    );
  };

  return (
    <View style={styles.container}>
      {sessions.length > 0 ? (
        <>
          <FlatList
            data={sessions}
            keyExtractor={(item) => item.id}
            renderItem={({ item }) => <SessionItem session={item} />}
            onRefresh={handleRefresh}
            refreshing={refreshing}
            contentContainerStyle={styles.listContent}
          />
          <View style={styles.exportButtons}>
            <Pressable style={styles.exportButton} onPress={handleExportJSON}>
              <Ionicons name="download" size={20} color="#FFFFFF" />
              <Text style={styles.exportButtonText}>Export JSON</Text>
            </Pressable>
            <Pressable style={styles.exportButton} onPress={handleExportCSV}>
              <Ionicons name="download" size={20} color="#FFFFFF" />
              <Text style={styles.exportButtonText}>Export CSV</Text>
            </Pressable>
          </View>
        </>
      ) : (
        <View style={styles.emptyContainer}>
          <Ionicons name="document-text-outline" size={64} color="#7F8C8D" />
          <Text style={styles.emptyText}>No tracking sessions yet</Text>
          <Text style={styles.emptySubText}>
            Start tracking to create your first session
          </Text>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#1A252F',
  },
  listContent: {
    padding: 15,
  },
  sessionItem: {
    backgroundColor: '#34495E',
    borderRadius: 10,
    padding: 15,
    marginBottom: 10,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    elevation: 2,
  },
  sessionContent: {
    flex: 1,
  },
  sessionDate: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#ECF0F1',
    marginBottom: 8,
  },
  sessionStats: {
    flexDirection: 'row',
    gap: 15,
  },
  sessionStat: {
    fontSize: 12,
    color: '#95A5A6',
  },
  deleteButton: {
    padding: 10,
  },
  exportButtons: {
    flexDirection: 'row',
    gap: 10,
    padding: 15,
    backgroundColor: '#2C3E50',
  },
  exportButton: {
    flex: 1,
    backgroundColor: '#00D4FF',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
    borderRadius: 8,
    gap: 8,
  },
  exportButtonText: {
    color: '#FFFFFF',
    fontWeight: 'bold',
    fontSize: 12,
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 30,
  },
  emptyText: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#ECF0F1',
    marginTop: 15,
  },
  emptySubText: {
    fontSize: 14,
    color: '#95A5A6',
    textAlign: 'center',
    marginTop: 10,
  },
});
