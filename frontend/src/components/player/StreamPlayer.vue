<template>
  <div class="stream-player-container">
    <iframe
        v-if="playerUrl"
        :src="playerUrl"
        class="stream-iframe"
        allow="autoplay; fullscreen"
    ></iframe>
    <div v-else class="loading-overlay">
      <v-progress-circular indeterminate size="64" color="primary"></v-progress-circular>
      <div class="mt-4">Loading Stream...</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps({
  webRtcUrl: {
    type: String,
    required: true,
  },
});

const playerUrl = computed(() => {
  if (!props.webRtcUrl) {
    return '';
  }
  // The backend provides a WebRTC URL (e.g., http://.../path/whep),
  // but the iframe player page is at the base path.
  return props.webRtcUrl;
});
</script>

<style scoped>
.stream-player-container {
  width: 100%;
  height: 100%;
  position: relative;
  background-color: #000;
  display: flex;
  justify-content: center;
  align-items: center;
}

.stream-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.loading-overlay {
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
