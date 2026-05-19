package kg.geoinfo.system.geoabstraction.service.geoserver;

import kg.geoinfo.system.geoabstraction.config.GeoServerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeoServerInitializer implements CommandLineRunner {

    private final GeoServerClient geoServerClient;
    private final GeoServerProperties properties;

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting GeoServer initialization...");
        
        String workspace = properties.getWorkspace();
        if (!geoServerClient.workspaceExists(workspace)) {
            log.info("Creating workspace: {}", workspace);
            geoServerClient.createWorkspace(workspace);
        } else {
            log.info("Workspace {} already exists", workspace);
        }

        initializeStyles();
        
        log.info("GeoServer initialization completed.");
    }

    private void initializeStyles() {
        log.info("Initializing GeoServer styles...");
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath:geoserver/styles/*.sld");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null) {
                    String styleName = filename.replace(".sld", "");
                    if (!geoServerClient.styleExists(styleName)) {
                        String sldContent = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                        geoServerClient.createStyle(styleName, sldContent);
                    } else {
                        log.info("Style {} already exists", styleName);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to initialize GeoServer styles: {}", e.getMessage());
        }
    }
}
