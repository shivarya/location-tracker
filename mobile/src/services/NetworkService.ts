import NetInfo from '@react-native-community/netinfo';

class NetworkService {
  private isConnected: boolean = true;
  private listeners: Set<(isOnline: boolean) => void> = new Set();
  private unsubscribe: (() => void) | null = null;

  /**
   * Initialize network monitoring
   */
  initialize() {
    if (this.unsubscribe) {
      return; // Already initialized
    }

    this.unsubscribe = NetInfo.addEventListener((state) => {
      const connected = state.isConnected ?? false;
      
      if (this.isConnected !== connected) {
        this.isConnected = connected;
        console.log(`Network status changed: ${connected ? 'Online' : 'Offline'}`);
        
        // Notify all listeners
        this.listeners.forEach((listener) => {
          listener(connected);
        });
      }
    });
  }

  /**
   * Get current network status
   */
  async getNetworkStatus(): Promise<boolean> {
    try {
      const state = await NetInfo.fetch();
      this.isConnected = state.isConnected ?? false;
      return this.isConnected;
    } catch (error) {
      console.error('Failed to fetch network status:', error);
      return false;
    }
  }

  /**
   * Check if device is online
   */
  isOnline(): boolean {
    return this.isConnected;
  }

  /**
   * Add listener for network status changes
   */
  addListener(callback: (isOnline: boolean) => void) {
    this.listeners.add(callback);
    
    // Return unsubscribe function
    return () => {
      this.listeners.delete(callback);
    };
  }

  /**
   * Remove listener
   */
  removeListener(callback: (isOnline: boolean) => void) {
    this.listeners.delete(callback);
  }

  /**
   * Cleanup all listeners
   */
  cleanup() {
    if (this.unsubscribe) {
      this.unsubscribe();
      this.unsubscribe = null;
    }
    this.listeners.clear();
  }
}

export default new NetworkService();
