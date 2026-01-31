<template>
  <div class="stream-player-container">
    <video ref="videoRef" class="stream-video" controls autoplay muted></video>

    <div v-if="loading" class="loading-overlay">
      <v-progress-circular indeterminate color="primary"></v-progress-circular>
      <p class="mt-4">{{ statusMessage }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue';
import Hls from 'hls.js';
import type { ErrorData, HlsConfig } from 'hls.js';
import LoginService from '@/services/login.service';

const props = defineProps<{
  streamHlsUrl: string; // The base URL for the stream from the backend
}>();

const videoRef = ref<HTMLVideoElement | null>(null);
const loading = ref(true);
let hls: Hls | null = null;
let isRetrying = false; // Flag to prevent infinite retry loops on 401 errors
const statusMessage = ref('Loading stream...');

/**
 * Gets the current token from localStorage and appends it to the given URL.
 * @returns The URL with the token appended as a query parameter, or the original URL if no token is found.
 */
const getUrlWithToken = (baseUrl: string): string => {
  const token = localStorage.getItem('access_token');
  return `${baseUrl}/index.m3u8?access_token=${token}`;
};

const getHlsConfig = (): Partial<HlsConfig> => ({
  // Начинаем воспроизведение, как только готов 1-й сегмент
  initialLiveManifestSize: 1,
  // Настройки ретраев для манифеста (важно для sourceOnDemand)
  manifestLoadingMaxRetry: 30,      // Пробуем в течение ~30 секунд
  manifestLoadingRetryDelay: 1000,  // Пауза 1 сек между попытками
  // Настройки для фрагментов
  fragLoadingMaxRetry: 10,
  fragLoadingRetryDelay: 1000,
});

const initPlayer = () => {
  if (!videoRef.value) {
    console.error('[StreamPlayer] Video element not found.');
    return;
  }
  if (!Hls.isSupported()) {
    console.error('[StreamPlayer] HLS is not supported in this browser.');
    return;
  }
  console.log('[StreamPlayer] Initializing player...');

  if (hls) {
    hls.destroy();
  }

  hls = new Hls(getHlsConfig());

  // --- HLS Event Handlers ---

  hls.on(Hls.Events.ERROR, async (_event: string, data: ErrorData) => {
    console.error('[StreamPlayer] HLS Error:', data);

    if (data.type === Hls.ErrorTypes.NETWORK_ERROR && data.response?.code === 401 && !isRetrying) {
      isRetrying = true;
      console.warn('[StreamPlayer] HLS stream returned 401. Attempting to refresh token...');

      try {
        await LoginService.refreshToken();
        console.log('[StreamPlayer] Token refreshed successfully. Reloading stream with new token.');

        if (hls) {
          const newUrl = getUrlWithToken(props.streamHlsUrl);
          console.log(`[StreamPlayer] Reloading source with new URL: ${newUrl}`);
          hls.loadSource(newUrl);
        }
      } catch (refreshError) {
        console.error('[StreamPlayer] Failed to refresh token after HLS 401 error.', refreshError);
        LoginService.logout();
      } finally {
        setTimeout(() => { isRetrying = false; }, 5000); // Prevent rapid retries
      }
    } else if (data.fatal) {
      switch (data.type) {
        case Hls.ErrorTypes.NETWORK_ERROR:
          statusMessage.value = 'Waiting for camera...';
          console.warn('[StreamPlayer] Network error, retrying...', data);
          hls?.startLoad();
          break;
        case Hls.ErrorTypes.MEDIA_ERROR:
          console.warn('[StreamPlayer] Media error, recovering...');
          hls?.recoverMediaError();
          break;
        default:
          initPlayer(); // Реинициализация при критических сбоях
          break;
      }
    }
  });

  hls.on(Hls.Events.MANIFEST_PARSED, () => {
    console.log('[StreamPlayer] Manifest parsed, playback should start.');
    loading.value = false;
  });

  hls.on(Hls.Events.FRAG_LOADING, (_event, data) => {
    console.log(`[StreamPlayer] Loading fragment: ${data.frag.sn}`);
  });

  // --- Initial Load ---

  const initialUrl = getUrlWithToken(props.streamHlsUrl);
  console.log(`[StreamPlayer] Loading initial source: ${initialUrl}`);
  hls.loadSource(initialUrl);
  hls.attachMedia(videoRef.value);
};

onMounted(() => {
  console.log('[StreamPlayer] Component mounted.');
  initPlayer();
});

onUnmounted(() => {
  console.log('[StreamPlayer] Component unmounted, destroying player.');
  if (hls) {
    hls.destroy();
  }
});

watch(() => props.streamHlsUrl, (newUrl, oldUrl) => {
  if (newUrl !== oldUrl) {
    console.log(`[StreamPlayer] Stream URL changed. Re-initializing player.` + newUrl);
    loading.value = true;
    isRetrying = false;
    initPlayer();
  }
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

.stream-video {
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
