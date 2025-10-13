package kg.geoinfo.system.geodataservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class GeodataServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(GeodataServiceApplication.class, args);
    }
}
