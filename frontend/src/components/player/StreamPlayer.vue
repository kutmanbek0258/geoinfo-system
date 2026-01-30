<template>
  <div class="stream-player-container">
    <div data-vjs-player>
      <video ref="videoRef" class="video-js vjs-big-play-centered vjs-fluid"></video>
    </div>

    <div v-if="loading" class="loading-overlay">
      <v-progress-circular indeterminate color="primary"></v-progress-circular>
      <p class="mt-4">Подключение к камере...</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue';
import videojs from 'video.js';
import 'video.js/dist/video-js.css';
import LoginService from '@/services/login.service';

const props = defineProps<{
  hlsStreamUrl: string;
}>();

const videoRef = ref<HTMLVideoElement | null>(null);
const loading = ref(true);
let player: any = null;
let isRefreshing = false;

// Вспомогательная функция для формирования URL
const prepareUrl = (baseUrl: string): string => {
  const token = localStorage.getItem('access_token');
  const cleanUrl = baseUrl.replace(/\/+$/, "") + "/index.m3u8";
  return `${cleanUrl}?access_token=${token}`;
};

const initPlayer = () => {
  if (!videoRef.value) return;

  const vjsAny = videojs as any;
  // Используем актуальный onRequest вместо deprecated beforeRequest
  if (vjsAny.Vhs && vjsAny.Vhs.xhr) {
    vjsAny.Vhs.xhr.onRequest = (options: { uri: string }) => {
      const token = localStorage.getItem('access_token');
      if (token) {
        const url = new URL(options.uri, window.location.href);
        url.searchParams.set('access_token', token);
        options.uri = url.toString();
      }
      return options;
    };
  }

  const options = {
    autoplay: true,
    controls: true,
    muted: true,
    fluid: true,
    html5: {
      vhs: {
        enableLowLatency: false,
        // Это заставит плеер игнорировать подсказки LL-HLS в манифесте
        llhls: false
      }
    },
    sources: [{
      src: prepareUrl(props.hlsStreamUrl),
      type: 'application/x-mpegURL'
    }]
  };

  player = videojs(videoRef.value, options, () => {
    loading.value = false;
  });

  // Обработка ошибок через Middleware или событие error
  player.on('error', async () => {
    const error = player.error();

    // Если получили 401 (Unauthorized)
    if (error.code === 4 && !isRefreshing) {
      isRefreshing = true;
      console.warn('[VideoJS] 401 Error. Refreshing token...');

      try {
        await LoginService.refreshToken();
        console.log('[VideoJS] Token refreshed. Reloading source...');

        // Перезагружаем источник с новым токеном
        player.src({
          src: prepareUrl(props.hlsStreamUrl),
          type: 'application/x-mpegURL'
        });
        player.play();
      } catch (err) {
        console.error('[VideoJS] Refresh token failed');
        LoginService.logout();
      } finally {
        setTimeout(() => { isRefreshing = false; }, 5000);
      }
    }
  });
};

onMounted(() => {
  initPlayer();
});

onUnmounted(() => {
  if (player) {
    player.dispose();
  }
});

watch(() => props.hlsStreamUrl, (newUrl) => {
  if (player) {
    loading.value = true;
    player.src({
      src: prepareUrl(newUrl),
      type: 'application/x-mpegURL'
    });
  }
});
</script>

<style scoped>
.stream-player-container {
  width: 100%;
  position: relative;
  background: #000;
}
.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,0.6);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 10;
  color: white;
}
:deep(.video-js) {
  font-family: inherit;
}
</style>
