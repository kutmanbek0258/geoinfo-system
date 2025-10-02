<script setup lang="ts">
import Shadow from "@/components/style-components/shadow/Shadow.vue";
import ProjectManager from "@/components/projects/ProjectsManager.vue";
import ProjectsManager from "@/components/projects/ProjectsManager.vue";
import ImageryLayersManager from "@/components/imagery/ImageryLayersManager.vue"
// export default {
//   components: {ProjectsManager}
// }


</script>

<template>
    <v-row>
      <v-col cols="12">
        <ProjectsManager/>
      </v-col>
      <v-col cols="12">
        <ImageryLayersManager/>
      </v-col>
    </v-row>
</template> 
