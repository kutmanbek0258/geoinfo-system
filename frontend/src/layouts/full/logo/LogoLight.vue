<script setup lang="ts">
import { ref } from 'vue';
import { RouterLink } from 'vue-router';
const primary = ref('rgb(var(--v-theme-primary))');
const secondary = ref('rgb(var(--v-theme-secondary))');
</script>
<template>
    <div class="logo">
        <RouterLink to="/">
          <svg
              width="174"
              height="26"
              viewBox="0 0 174 26"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
          >
            <!-- Globe -->
            <circle cx="13" cy="13" r="12" fill="#2394E0"/>

            <!-- Continents -->
            <path
                d="M10 5
                   L13 4
                   L16 5
                   L18 7
                   L17 10
                   L15 11
                   L14 14
                   L12 18
                   L10 20
                   L8 18
                   L7 15
                   L6 11
                   L7 8
                   Z"
                fill="#A1E367"
            />

            <path
                d="M17 14
                   L19 15
                   L18 18
                   L16 19
                   L15 17
                   Z"
                fill="#A1E367"
            />

            <!-- Text -->
            <text
                x="32"
                y="18"
                font-family="Arial, Helvetica, sans-serif"
                font-size="14"
                font-weight="700"
            >
              <tspan fill="#2394E0">GeoInfo</tspan>
              <tspan fill="#A1E367">Systems</tspan>
            </text>
          </svg>
        </RouterLink>
    </div>
</template>
