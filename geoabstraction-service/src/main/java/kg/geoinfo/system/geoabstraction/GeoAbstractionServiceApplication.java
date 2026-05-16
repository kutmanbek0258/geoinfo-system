package kg.geoinfo.system.geoabstraction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class GeoAbstractionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeoAbstractionServiceApplication.class, args);
    }
}