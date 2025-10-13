package kg.geoinfo.system.geodataservice.service.client;

import kg.geoinfo.system.geodataservice.dto.client.DocumentDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@FeignClient(name = "document-service", url = "${feign.client.config.document-service.url}", configuration = FeignClientConfiguration.class)
public interface DocumentServiceClient {

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    DocumentDto uploadDocument(@RequestPart("file") MultipartFile file,
                               @RequestParam("geoObjectId") UUID geoObjectId,
                               @RequestParam(value = "description", required = false) String description,
                               @RequestParam(value = "tags", required = false) String tags);
}
