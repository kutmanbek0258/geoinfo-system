package kg.geoinfo.system.geoabstraction.service;

import kg.geoinfo.system.geoabstraction.models.TerrainLayer;
import kg.geoinfo.system.geoabstraction.repository.TerrainLayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupScheduler {

    private final TerrainLayerRepository terrainLayerRepository;

    private static final String TERRAIN_STORE_PATH = "/data/terrain-store";

    @Scheduled(cron = "0 0 3 * * *") // Every day at 3 AM
    public void cleanupOrphanedFiles() {
        log.info("Starting scheduled cleanup of orphaned GIS files...");
        try {
            cleanupTerrainFolders();
        } catch (Exception e) {
            log.error("Error during GIS cleanup: {}", e.getMessage(), e);
        }
        log.info("Scheduled cleanup finished.");
    }

    private void cleanupTerrainFolders() {
        File dir = new File(TERRAIN_STORE_PATH);
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("Terrain store path {} does not exist or is not a directory", TERRAIN_STORE_PATH);
            return;
        }

        Set<String> activeTerrainPrefixes = terrainLayerRepository.findAll().stream()
                .map(layer -> {
                    String url = layer.getTerrainUrl();
                    if (url == null || url.isBlank()) return null;
                    // Normalize: remove trailing slash
                    if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
                    String[] parts = url.split("/");
                    return parts.length > 0 ? parts[parts.length - 1] : null;
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        File[] folders = dir.listFiles(File::isDirectory);
        if (folders == null) return;

        int deletedCount = 0;
        for (File folder : folders) {
            String folderName = folder.getName();
            if (!activeTerrainPrefixes.contains(folderName)) {
                log.info("Deleting orphaned terrain folder: {}", folder.getAbsolutePath());
                deleteDirectory(folder);
                deletedCount++;
            }
        }
        if (deletedCount > 0) {
            log.info("Deleted {} orphaned terrain folders", deletedCount);
        }
    }

    private void deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }
}
