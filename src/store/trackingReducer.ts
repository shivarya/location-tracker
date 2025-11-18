import {
  TrackingState,
  TrackingActionTypes,
  initialState,
  START_TRACKING,
  STOP_TRACKING,
  UPDATE_LOCATION,
  ADD_LOCATION_POINT,
  SET_UNIT_SYSTEM,
  SAVE_SESSION,
  LOAD_SESSIONS,
  DELETE_SESSION,
  SET_ERROR,
  CLEAR_ERROR,
  LOAD_SESSION_HISTORY,
} from './types';
import { generateUUID } from '../utils/constants';

export const trackingReducer = (
  state = initialState,
  action: TrackingActionTypes
): TrackingState => {
  switch (action.type) {
    case START_TRACKING:
      return {
        ...state,
        isTracking: true,
        currentSession: {
          id: generateUUID(),
          startTime: Date.now(),
          points: [],
          statistics: {
            totalDistance: 0,
            avgSpeed: 0,
            maxSpeed: 0,
            minSpeed: 0,
            duration: 0,
          },
        },
        error: null,
      };

    case STOP_TRACKING:
      return {
        ...state,
        isTracking: false,
      };

    case UPDATE_LOCATION:
      return {
        ...state,
        currentLocation: action.payload,
      };

    case ADD_LOCATION_POINT:
      if (!state.currentSession) return state;
      
      const updatedPoints = [...state.currentSession.points, action.payload];
      const speeds = updatedPoints.map(p => p.speed).filter(s => s > 0);
      const avgSpeed = speeds.length > 0 ? speeds.reduce((a, b) => a + b) / speeds.length : 0;
      const maxSpeed = speeds.length > 0 ? Math.max(...speeds) : 0;
      const minSpeed = speeds.length > 0 ? Math.min(...speeds) : 0;

      // Calculate total distance
      let totalDistance = 0;
      for (let i = 1; i < updatedPoints.length; i++) {
        const prev = updatedPoints[i - 1];
        const curr = updatedPoints[i];
        const dLat = ((curr.latitude - prev.latitude) * Math.PI) / 180;
        const dLon = ((curr.longitude - prev.longitude) * Math.PI) / 180;
        const a =
          Math.sin(dLat / 2) * Math.sin(dLat / 2) +
          Math.cos((prev.latitude * Math.PI) / 180) *
            Math.cos((curr.latitude * Math.PI) / 180) *
            Math.sin(dLon / 2) *
            Math.sin(dLon / 2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        totalDistance += 6371000 * c;
      }

      const now = Date.now();
      const duration = (now - state.currentSession.startTime) / 1000;

      return {
        ...state,
        currentSession: {
          ...state.currentSession,
          points: updatedPoints,
          statistics: {
            totalDistance,
            avgSpeed,
            maxSpeed,
            minSpeed,
            duration,
          },
        },
      };

    case SET_UNIT_SYSTEM:
      return {
        ...state,
        unitSystem: action.payload,
      };

    case SAVE_SESSION:
      return {
        ...state,
        sessions: [...state.sessions, action.payload],
        currentSession: null,
        isTracking: false,
      };

    case LOAD_SESSIONS:
      return {
        ...state,
        sessions: action.payload,
      };

    case DELETE_SESSION:
      return {
        ...state,
        sessions: state.sessions.filter(s => s.id !== action.payload),
      };

    case SET_ERROR:
      return {
        ...state,
        error: action.payload,
      };

    case CLEAR_ERROR:
      return {
        ...state,
        error: null,
      };

    case LOAD_SESSION_HISTORY:
      return {
        ...state,
        sessionHistory: action.payload,
      };

    default:
      return state;
  }
};
