import AsyncStorage from '@react-native-async-storage/async-storage';
import { TrackingSession } from '../store/types';
import { STORAGE_KEYS } from '../utils/constants';

class StorageService {
  /**
   * Save tracking session
   */
  async saveSession(session: TrackingSession): Promise<boolean> {
    try {
      const sessions = await this.getSessions();
      sessions.push(session);
      await AsyncStorage.setItem(STORAGE_KEYS.SESSIONS, JSON.stringify(sessions));
      return true;
    } catch (error) {
      console.error('Save session error:', error);
      return false;
    }
  }

  /**
   * Get all saved sessions
   */
  async getSessions(): Promise<TrackingSession[]> {
    try {
      const data = await AsyncStorage.getItem(STORAGE_KEYS.SESSIONS);
      return data ? JSON.parse(data) : [];
    } catch (error) {
      console.error('Get sessions error:', error);
      return [];
    }
  }

  /**
   * Get single session by ID
   */
  async getSession(sessionId: string): Promise<TrackingSession | null> {
    try {
      const sessions = await this.getSessions();
      return sessions.find(s => s.id === sessionId) || null;
    } catch (error) {
      console.error('Get session error:', error);
      return null;
    }
  }

  /**
   * Update session
   */
  async updateSession(session: TrackingSession): Promise<boolean> {
    try {
      const sessions = await this.getSessions();
      const index = sessions.findIndex(s => s.id === session.id);
      if (index !== -1) {
        sessions[index] = session;
        await AsyncStorage.setItem(STORAGE_KEYS.SESSIONS, JSON.stringify(sessions));
        return true;
      }
      return false;
    } catch (error) {
      console.error('Update session error:', error);
      return false;
    }
  }

  /**
   * Delete session
   */
  async deleteSession(sessionId: string): Promise<boolean> {
    try {
      const sessions = await this.getSessions();
      const filtered = sessions.filter(s => s.id !== sessionId);
      await AsyncStorage.setItem(STORAGE_KEYS.SESSIONS, JSON.stringify(filtered));
      return true;
    } catch (error) {
      console.error('Delete session error:', error);
      return false;
    }
  }

  /**
   * Clear all sessions
   */
  async clearAllSessions(): Promise<boolean> {
    try {
      await AsyncStorage.removeItem(STORAGE_KEYS.SESSIONS);
      return true;
    } catch (error) {
      console.error('Clear sessions error:', error);
      return false;
    }
  }

  /**
   * Save unit preference
   */
  async setUnitPreference(unit: string): Promise<boolean> {
    try {
      await AsyncStorage.setItem(STORAGE_KEYS.UNIT_PREFERENCE, unit);
      return true;
    } catch (error) {
      console.error('Save unit preference error:', error);
      return false;
    }
  }

  /**
   * Get unit preference
   */
  async getUnitPreference(): Promise<string | null> {
    try {
      return await AsyncStorage.getItem(STORAGE_KEYS.UNIT_PREFERENCE);
    } catch (error) {
      console.error('Get unit preference error:', error);
      return null;
    }
  }

  /**
   * Export sessions as JSON
   */
  async exportSessionsAsJSON(): Promise<string> {
    try {
      const sessions = await this.getSessions();
      return JSON.stringify(sessions, null, 2);
    } catch (error) {
      console.error('Export sessions error:', error);
      return '[]';
    }
  }

  /**
   * Export sessions as CSV
   */
  async exportSessionsAsCSV(): Promise<string> {
    try {
      const sessions = await this.getSessions();
      let csv = 'Session ID,Start Time,End Time,Duration (s),Total Distance (m),Avg Speed (m/s),Max Speed (m/s),Min Speed (m/s)\n';

      sessions.forEach(session => {
        const startTime = new Date(session.startTime).toISOString();
        const endTime = session.endTime ? new Date(session.endTime).toISOString() : 'N/A';
        const row = [
          session.id,
          startTime,
          endTime,
          session.statistics.duration.toFixed(2),
          session.statistics.totalDistance.toFixed(2),
          session.statistics.avgSpeed.toFixed(2),
          session.statistics.maxSpeed.toFixed(2),
          session.statistics.minSpeed.toFixed(2),
        ].join(',');
        csv += row + '\n';
      });

      return csv;
    } catch (error) {
      console.error('Export CSV error:', error);
      return '';
    }
  }
}

export default new StorageService();
