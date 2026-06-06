<template>
  <div class="map-container">
    <!-- Map Engine Container (OpenLayers or Cesium) -->
    <div class="map-engine">
      <slot name="engine"></slot>
    </div>

    <!-- Top Left Overlay: Search, Tree -->
    <div class="overlay-top-left">
      <slot name="top-left"></slot>
    </div>

    <!-- Top Right Overlay: Layers, Details -->
    <div class="overlay-top-right">
      <slot name="top-right"></slot>
    </div>

    <!-- Bottom Right Overlay: Tools, Add buttons -->
    <div class="overlay-bottom-right">
      <slot name="bottom-right"></slot>
    </div>

    <!-- Centered Overlays: Shot Frame, Loading -->
    <div class="overlay-center">
      <slot name="center"></slot>
    </div>

    <!-- Global Dialogs and Extra Panels -->
    <slot name="overlays"></slot>
  </div>
</template>

<script setup lang="ts">
// Shared layout for both 2D (OL) and 3D (Cesium) maps
</script>

<style scoped>
.map-container {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.map-engine {
  width: 100%;
  height: 100%;
}

.overlay-top-left {
  position: absolute;
  top: 60px;
  left: 10px;
  width: 350px;
  display: flex;
  flex-direction: column;
  z-index: 100;
  pointer-events: auto;
}

.overlay-top-right {
  position: absolute;
  top: 40px;
  right: 10px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  z-index: 100;
  pointer-events: auto;
}

.overlay-bottom-right {
  position: absolute;
  bottom: 20px;
  right: 10px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  z-index: 100;
  pointer-events: auto;
}

.overlay-center {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
}

/* Common style for overlay panels that should look like cards */
:deep(.map-card) {
  background-color: rgba(255, 255, 255, 0.9);
  border-radius: 4px;
  padding: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}
</style>
