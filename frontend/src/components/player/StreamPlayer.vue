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
  streamHlsUrl: string;
}>();

const videoRef = ref<HTMLVideoElement | null>(null);
const loading = ref(true);
const statusMessage = ref('Loading stream...');

let hls: Hls | null = null;
let isRefreshing = false; // "Мьютекс" для процесса обновления
let consecutive401Errors = 0; // Счетчик для предотвращения бесконечной петли

/**
 * Оптимизированный конфиг HLS
 */
const getHlsConfig = (): Partial<HlsConfig> => ({
  initialLiveManifestSize: 1,
  manifestLoadingMaxRetry: 30,
  manifestLoadingRetryDelay: 1000,
  fragLoadingMaxRetry: 10,
  fragLoadingRetryDelay: 1000,

  // КЛЮЧЕВОЙ МОМЕНТ: Динамическая подстановка токена в каждый запрос
  xhrSetup: (xhr, url) => {
    const token = localStorage.getItem('access_token');
    if (token) {
      const urlObj = new URL(url);
      urlObj.searchParams.set('access_token', token);
      xhr.open('GET', urlObj.toString(), true);
    }
  }
});

const handle401Error = async () => {
  if (isRefreshing) return; // Если обновление уже идет, выходим

  if (consecutive401Errors >= 3) {
    console.error('[StreamPlayer] Too many 401 errors. Stopping to prevent loop.');
    LoginService.logout();
    return;
  }

  isRefreshing = true;
  consecutive401Errors++;
  statusMessage.value = "Updating session...";

  try {
    console.warn(`[StreamPlayer] 401 Unauthorized. Attempt ${consecutive401Errors} to refresh token...`);

    // Останавливаем загрузку на время обновления
    hls?.stopLoad();

    await LoginService.refreshToken();

    console.log('[StreamPlayer] Token refreshed. Resuming playback.');

    // После успешного обновления сбрасываем счетчик через некоторое время
    setTimeout(() => { consecutive401Errors = 0; }, 10000);

    // Перезагружаем источник, чтобы hls.js перечитал манифест с новым токеном через xhrSetup
    hls?.startLoad();
  } catch (err) {
    console.error('[StreamPlayer] Refresh token failed.', err);
    LoginService.logout();
  } finally {
    isRefreshing = false;
  }
};

const initPlayer = () => {
  if (!videoRef.value || !Hls.isSupported()) return;

  if (hls) hls.destroy();

  hls = new Hls(getHlsConfig());

  hls.on(Hls.Events.ERROR, async (_event, data) => {
    const responseCode = data.response?.code;

    // 1. Ошибка 403 (Прогрев камеры при sourceOnDemand)
    if (responseCode === 403) {
      loading.value = true;
      statusMessage.value = "Camera is warming up...";
      setTimeout(() => hls?.startLoad(), 2000);
      return;
    }

    // 2. Ошибка 401 (Токен протух)
    if (responseCode === 401) {
      await handle401Error();
      return;
    }

    // 3. Фатальные ошибки
    if (data.fatal) {
      switch (data.type) {
        case Hls.ErrorTypes.NETWORK_ERROR:
          hls?.startLoad();
          break;
        case Hls.ErrorTypes.MEDIA_ERROR:
          hls?.recoverMediaError();
          break;
        default:
          initPlayer();
          break;
      }
    }
  });

  hls.on(Hls.Events.MANIFEST_PARSED, () => {
    loading.value = false;
    videoRef.value?.play().catch(() => console.log("Autoplay blocked"));
  });

  // Загружаем чистый URL без токена (xhrSetup сам добавит его)
  const cleanUrl = props.streamHlsUrl.endsWith('.m3u8')
      ? props.streamHlsUrl
      : `${props.streamHlsUrl}/index.m3u8`;

  hls.loadSource(cleanUrl);
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
