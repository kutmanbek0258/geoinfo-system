<template>
  <div class="webrtc-player-container">
    <video ref="videoElement" autoplay playsinline muted class="video-player"></video>
    <div v-if="isLoading" class="loading-overlay">
      <v-progress-circular indeterminate size="64" color="primary"></v-progress-circular>
      <div class="mt-4">Connecting to stream...</div>
    </div>
    <div v-if="error" class="error-overlay">
      <v-alert type="error" dense>
        {{ error }}
      </v-alert>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue';

const props = defineProps({
  streamUrl: {
    type: String,
    required: true,
  },
});

const emit = defineEmits(['close']);

const videoElement = ref<HTMLVideoElement | null>(null);
const peerConnection = ref<RTCPeerConnection | null>(null);
const isLoading = ref(true);
const error = ref<string | null>(null);

let reconnectTimeout: ReturnType<typeof setTimeout> | null = null;

const connect = async () => {
  if (peerConnection.value) {
    peerConnection.value.close();
  }

  isLoading.value = true;
  error.value = null;

  try {
    const pc = new RTCPeerConnection({
      iceServers: [
        {
          urls: 'stun:stun.l.google.com:19302'
        }
      ]
    });

    pc.ontrack = (event) => {
      console.log('Received remote stream track');
      if (videoElement.value) {
        if (!videoElement.value.srcObject) {
          videoElement.value.srcObject = new MediaStream();
        }
        (videoElement.value.srcObject as MediaStream).addTrack(event.track);
        isLoading.value = false;
      }
    };

    pc.oniceconnectionstatechange = () => {
      if (pc.iceConnectionState === 'disconnected' || pc.iceConnectionState === 'failed') {
        console.error('ICE connection failed. Attempting to reconnect...');
        error.value = 'Connection lost. Attempting to reconnect...';
        // Simple reconnect logic
        if (!reconnectTimeout) {
            reconnectTimeout = setTimeout(() => {
            reconnectTimeout = null;
            connect();
          }, 3000);
        }
      } else if (pc.iceConnectionState === 'connected') {
        console.log('ICE connection established.');
        error.value = null;
        isLoading.value = false;
      }
    };

    pc.addTransceiver('video', { direction: 'recvonly' });
    pc.addTransceiver('audio', { direction: 'recvonly' });

    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);

    const response = await fetch(props.streamUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/sdp',
      },
      body: offer.sdp,
    });

    if (!response.ok) {
        throw new Error(`Failed to connect to WHEP endpoint: ${response.status} ${response.statusText}`);
    }

    const answerSdp = await response.text();
    await pc.setRemoteDescription(
      new RTCSessionDescription({ type: 'answer', sdp: answerSdp })
    );

    peerConnection.value = pc;

  } catch (e: any) {
    console.error('WebRTC connection failed:', e);
    error.value = `Failed to establish stream: ${e.message}`;
    isLoading.value = false;
  }
};

onMounted(() => {
  if (props.streamUrl) {
    connect();
  }
});

onUnmounted(() => {
  if (reconnectTimeout) {
    clearTimeout(reconnectTimeout);
  }
  if (peerConnection.value) {
    peerConnection.value.close();
  }
});

watch(() => props.streamUrl, (newUrl) => {
  if (newUrl) {
    connect();
  }
});
</script>

<style scoped>
.webrtc-player-container {
  width: 100%;
  height: 100%;
  position: relative;
  background-color: #000;
  display: flex;
  justify-content: center;
  align-items: center;
}

.video-player {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.loading-overlay,
.error-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background-color: rgba(0, 0, 0, 0.7);
  color: white;
}
</style>
