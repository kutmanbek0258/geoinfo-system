import api from './api';

export class StreamService {
  /**
   * Starts a stream for a given geo-object.
   * @param geoObjectId The UUID of the geo-object (camera).
   * @returns A promise that resolves with the WebRTC URL.
   */
  async startStream(geoObjectId: string): Promise<string> {
    try {
      const response = await api.post(`/streams/start`, { geoObjectId });
      console.log(response.data)
      return response.data.streamHlsUrl;
    } catch (error) {
      console.error('Error starting stream:', error);
      throw error;
    }
  }

  /**
   * Stops a stream for a given geo-object.
   * @param geoObjectId The UUID of the geo-object (camera).
   * @returns A promise that resolves when the stream is stopped.
   */
  async stopStream(geoObjectId: string): Promise<void> {
    try {
      await api.post(`/streams/stop`, { geoObjectId });
    } catch (error) {
      console.error('Error stopping stream:', error);
      throw error;
    }
  }
}

export default new StreamService();
