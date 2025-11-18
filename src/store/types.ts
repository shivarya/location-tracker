import { UNIT_SYSTEMS } from '../utils/constants';

export interface LocationPoint {
  latitude: number;
  longitude: number;
  altitude: number;
  speed: number;
  accuracy: number;
  heading: number;
  timestamp: number;
}

export interface TrackingSession {
  id: string;
  startTime: number;
  endTime?: number;
  points: LocationPoint[];
  statistics: {
    totalDistance: number;
    avgSpeed: number;
    maxSpeed: number;
    minSpeed: number;
    duration: number;
  };
}

export interface TrackingState {
  isTracking: boolean;
  currentSession: TrackingSession | null;
  sessions: TrackingSession[];
  unitSystem: string;
  currentLocation: LocationPoint | null;
  sessionHistory: TrackingSession[];
  error: string | null;
}

export const initialState: TrackingState = {
  isTracking: false,
  currentSession: null,
  sessions: [],
  unitSystem: UNIT_SYSTEMS.METRIC,
  currentLocation: null,
  sessionHistory: [],
  error: null,
};

// Action types
export const START_TRACKING = 'START_TRACKING';
export const STOP_TRACKING = 'STOP_TRACKING';
export const UPDATE_LOCATION = 'UPDATE_LOCATION';
export const ADD_LOCATION_POINT = 'ADD_LOCATION_POINT';
export const SET_UNIT_SYSTEM = 'SET_UNIT_SYSTEM';
export const SAVE_SESSION = 'SAVE_SESSION';
export const LOAD_SESSIONS = 'LOAD_SESSIONS';
export const DELETE_SESSION = 'DELETE_SESSION';
export const SET_ERROR = 'SET_ERROR';
export const CLEAR_ERROR = 'CLEAR_ERROR';
export const LOAD_SESSION_HISTORY = 'LOAD_SESSION_HISTORY';

// Action Interfaces
export interface StartTrackingAction {
  type: typeof START_TRACKING;
}

export interface StopTrackingAction {
  type: typeof STOP_TRACKING;
}

export interface UpdateLocationAction {
  type: typeof UPDATE_LOCATION;
  payload: LocationPoint;
}

export interface AddLocationPointAction {
  type: typeof ADD_LOCATION_POINT;
  payload: LocationPoint;
}

export interface SetUnitSystemAction {
  type: typeof SET_UNIT_SYSTEM;
  payload: string;
}

export interface SaveSessionAction {
  type: typeof SAVE_SESSION;
  payload: TrackingSession;
}

export interface LoadSessionsAction {
  type: typeof LOAD_SESSIONS;
  payload: TrackingSession[];
}

export interface DeleteSessionAction {
  type: typeof DELETE_SESSION;
  payload: string;
}

export interface SetErrorAction {
  type: typeof SET_ERROR;
  payload: string;
}

export interface ClearErrorAction {
  type: typeof CLEAR_ERROR;
}

export interface LoadSessionHistoryAction {
  type: typeof LOAD_SESSION_HISTORY;
  payload: TrackingSession[];
}

export type TrackingActionTypes =
  | StartTrackingAction
  | StopTrackingAction
  | UpdateLocationAction
  | AddLocationPointAction
  | SetUnitSystemAction
  | SaveSessionAction
  | LoadSessionsAction
  | DeleteSessionAction
  | SetErrorAction
  | ClearErrorAction
  | LoadSessionHistoryAction;
