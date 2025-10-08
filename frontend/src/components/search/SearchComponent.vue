<template>
  <div class="search-container">
    <input
      type="text"
      v-model="searchQuery"
      @input="onSearchInput"
      placeholder="Search by name, description..."
      class="search-input"
    />
    <div v-if="isLoading" class="spinner">Loading...</div>
    <ul v-if="results && results.content.length" class="search-results">
      <li v-for="result in results.content" :key="result.id" @click="onResultClick(result)" class="result-item">
        <strong>{{ result.name }}</strong> ({{ result.type }})
        <p v-if="result.description">{{ result.description }}</p>
      </li>
    </ul>
    <div v-if="results && !results.content.length && searchQuery" class="no-results">
        No results found.
    </div>
    <div v-if="error" class="error">{{ error }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useStore } from 'vuex';
import debounce from 'lodash/debounce';
import type { SearchResult } from '@/types/api';

const store = useStore();
const searchQuery = ref('');

// Get data from Vuex store
const isLoading = computed(() => store.state.search.isLoading);
const results = computed(() => store.state.search.results);
const error = computed(() => store.state.search.error);

// Debounced search function
const debouncedSearch = debounce(() => {
  store.dispatch('search/performSearch', { query: searchQuery.value });
}, 500); // 500ms delay

const onSearchInput = () => {
  debouncedSearch();
};

// Action on result click
const onResultClick = (result: SearchResult) => {
  console.log('Clicked on:', result.id);
  // Dispatch an action to select the feature in the geodata store
  store.dispatch('geodata/selectFeature', result.id);
  // Clear search results after selection
  searchQuery.value = '';
  store.commit('search/CLEAR_RESULTS');
};
</script>

<style scoped>
.search-container {
  position: relative;
  width: 300px;
}

.search-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 1rem;
}

.spinner, .error, .no-results {
  padding: 10px;
  color: #888;
}

.search-results {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  background-color: white;
  border: 1px solid #ccc;
  border-top: none;
  border-radius: 0 0 4px 4px;
  list-style: none;
  padding: 0;
  margin: 0;
  max-height: 400px;
  overflow-y: auto;
  z-index: 1000;
}

.result-item {
  padding: 10px 12px;
  cursor: pointer;
}

.result-item:hover {
  background-color: #f0f0f0;
}

.result-item p {
  margin: 4px 0 0;
  font-size: 0.9em;
  color: #555;
}
</style>
