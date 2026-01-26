package kg.geoinfo.system.streamservice.client;

import kg.geoinfo.system.streamservice.dto.MediaMtxPathConfigDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "mediamtx-client", url = "${mediamtx.api.url}")
public interface MediaMtxClient {

    @PostMapping("/v3/config/paths/add/{path}")
    void addPath(@PathVariable("path") String path, @RequestBody MediaMtxPathConfigDto config);

    @DeleteMapping("/v3/config/paths/delete/{path}")
    void deletePath(@PathVariable("path") String path);
}
