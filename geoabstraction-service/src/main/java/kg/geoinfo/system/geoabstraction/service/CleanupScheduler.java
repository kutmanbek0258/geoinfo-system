package kg.geoinfo.system.geoabstraction.service;

import kg.geoinfo.system.geoabstraction.models.ImageryLayer;
import kg.geoinfo.system.geoabstraction.models.TerrainLayer;
import kg.geoinfo.system.geoabstraction.repository.ImageryLayerRepository;
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

    private final ImageryLayerRepository imageryLayerRepository;
    private final TerrainLayerRepository terrainLayerRepository;

    private static final String RASTER_STORE_PATH = "/data/gdal-store";
    private static final String TERRAIN_STORE_PATH = "/data/terrain-store";

    @Scheduled(cron = "0 0 3 * * *") // Every day at 3 AM
    public void cleanupOrphanedFiles() {
        log.info("Starting scheduled cleanup of orphaned GIS files...");
        try {
            cleanupRasterFiles();
            cleanupTerrainFolders();
        } catch (Exception e) {
            log.error("Error during GIS cleanup: {}", e.getMessage(), e);
        }
        log.info("Scheduled cleanup finished.");
    }

    private void cleanupRasterFiles() {
        File dir = new File(RASTER_STORE_PATH);
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("Raster store path {} does not exist or is not a directory", RASTER_STORE_PATH);
            return;
        }

        Set<String> activeLayerNames = imageryLayerRepository.findAll().stream()
                .map(ImageryLayer::getLayerName)
                .collect(Collectors.toSet());

        File[] files = dir.listFiles();
        if (files == null) return;

        int deletedCount = 0;
        for (File file : files) {
            String fileName = file.getName();
            if (fileName.endsWith(".tif")) {
                String layerName = fileName.substring(0, fileName.length() - 4);
                if (!activeLayerNames.contains(layerName)) {
                    log.info("Deleting orphaned raster file: {}", file.getAbsolutePath());
                    deleteFileWithSidecars(file);
                    deletedCount++;
                }
            }
        }
        if (deletedCount > 0) {
            log.info("Deleted {} orphaned raster files", deletedCount);
        }
    }

    private void deleteFileWithSidecars(File file) {
        String baseName = file.getAbsolutePath();
        file.delete();
        new File(baseName + ".aux.xml").delete();
        new File(baseName + ".ovr").delete();
        new File(baseName + ".msk").delete();
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
