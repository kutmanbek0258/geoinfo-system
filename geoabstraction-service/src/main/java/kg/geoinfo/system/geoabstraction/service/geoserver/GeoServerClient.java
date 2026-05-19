package kg.geoinfo.system.geoabstraction.service.geoserver;

import kg.geoinfo.system.geoabstraction.config.GeoServerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoServerClient {

    private final RestTemplate geoServerRestTemplate;
    private final GeoServerProperties properties;

    public void createWorkspace(String name) {
        String url = properties.getUrl() + "/rest/workspaces";

        String xml = """
        <workspace>
            <name>%s</name>
        </workspace>
        """.formatted(name);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setAccept(List.of(MediaType.APPLICATION_XML));

        HttpEntity<String> request = new HttpEntity<>(xml, headers);

        try {

            ResponseEntity<String> response = geoServerRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            log.info("Workspace {} response: {}", name, response.getStatusCode());

        } catch (Exception e) {
            log.error("Failed to create workspace {}", name, e); // ❗ ВАЖНО
        }
    }

    public boolean workspaceExists(String name) {
        String url = String.format("%s/rest/workspaces/%s", properties.getUrl(), name);
        try {
            ResponseEntity<String> response = geoServerRestTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            return false;
        }
    }

    public void createStyle(String name, String sldContent) {
        String url = String.format("%s/rest/styles?name=%s", properties.getUrl(), name);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "vnd.ogc.sld+xml"));
        
        HttpEntity<String> entity = new HttpEntity<>(sldContent, headers);
        
        try {
            ResponseEntity<String> response = geoServerRestTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode() == HttpStatus.CREATED) {
                log.info("Style {} created successfully", name);
            }
        } catch (Exception e) {
            log.error("Failed to create style {}: {}", name, e.getMessage());
        }
    }

    public boolean styleExists(String name) {
        String url = String.format("%s/rest/styles/%s", properties.getUrl(), name);
        try {
            ResponseEntity<String> response = geoServerRestTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            return false;
        }
    }

    public void createCoverageStore(String workspace, String storeName, String filePath) {
        String url = String.format("%s/rest/workspaces/%s/coveragestores", properties.getUrl(), workspace);
        String xml = String.format(
            "<coverageStore>" +
            "  <name>%s</name>" +
            "  <type>GeoTIFF</type>" +
            "  <enabled>true</enabled>" +
            "  <workspace>%s</workspace>" +
            "  <url>file:%s</url>" +
            "</coverageStore>", storeName, workspace, filePath);
            
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        
        HttpEntity<String> entity = new HttpEntity<>(xml, headers);
        
        try {
            geoServerRestTemplate.postForEntity(url, entity, String.class);
            log.info("CoverageStore {} created successfully in workspace {}", storeName, workspace);
        } catch (Exception e) {
            log.error("Failed to create CoverageStore {}: {}", storeName, e.getMessage());
        }
    }

    public void publishLayer(String workspace, String storeName, String layerName, String styleName) {
        String url = String.format("%s/rest/workspaces/%s/coveragestores/%s/coverages", properties.getUrl(), workspace, storeName);
        String xml = String.format(
            "<coverage>" +
            "  <name>%s</name>" +
            "  <title>%s</title>" +
            "  <defaultStyle><name>%s</name></defaultStyle>" +
            "</coverage>", layerName, layerName, styleName);
            
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        
        HttpEntity<String> entity = new HttpEntity<>(xml, headers);
        
        try {
            geoServerRestTemplate.postForEntity(url, entity, String.class);
            log.info("Layer {} published successfully with style {}", layerName, styleName);
        } catch (Exception e) {
            log.error("Failed to publish layer {}: {}", layerName, e.getMessage());
        }
    }
}
