import {
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
  LocationPoint,
  TrackingSession,
  StartTrackingAction,
  StopTrackingAction,
  UpdateLocationAction,
  AddLocationPointAction,
  SetUnitSystemAction,
  SaveSessionAction,
  LoadSessionsAction,
  DeleteSessionAction,
  SetErrorAction,
  ClearErrorAction,
  LoadSessionHistoryAction,
} from './types';

export const startTracking = (): StartTrackingAction => ({
  type: START_TRACKING,
});

export const stopTracking = (): StopTrackingAction => ({
  type: STOP_TRACKING,
});

export const updateLocation = (location: LocationPoint): UpdateLocationAction => ({
  type: UPDATE_LOCATION,
  payload: location,
});

export const addLocationPoint = (point: LocationPoint): AddLocationPointAction => ({
  type: ADD_LOCATION_POINT,
  payload: point,
});

export const setUnitSystem = (unit: string): SetUnitSystemAction => ({
  type: SET_UNIT_SYSTEM,
  payload: unit,
});

export const saveSession = (session: TrackingSession): SaveSessionAction => ({
  type: SAVE_SESSION,
  payload: session,
});

export const loadSessions = (sessions: TrackingSession[]): LoadSessionsAction => ({
  type: LOAD_SESSIONS,
  payload: sessions,
});

export const deleteSession = (sessionId: string): DeleteSessionAction => ({
  type: DELETE_SESSION,
  payload: sessionId,
});

export const setError = (error: string): SetErrorAction => ({
  type: SET_ERROR,
  payload: error,
});

export const clearError = (): ClearErrorAction => ({
  type: CLEAR_ERROR,
});

export const loadSessionHistory = (
  sessions: TrackingSession[]
): LoadSessionHistoryAction => ({
  type: LOAD_SESSION_HISTORY,
  payload: sessions,
});
