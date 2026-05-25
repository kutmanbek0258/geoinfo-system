package kg.geoinfo.system.geoprintservice.controller;

import kg.geoinfo.system.geoprintservice.dto.PrintSpecificationDto;
import kg.geoinfo.system.geoprintservice.dto.PrintTaskDto;
import kg.geoinfo.system.geoprintservice.service.PrintTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/print/tasks")
@RequiredArgsConstructor
public class PrintTaskController {

    private final PrintTaskService printTaskService;

    @PostMapping
    public ResponseEntity<PrintTaskDto> createPrintTask(@RequestBody PrintSpecificationDto spec) {
        return ResponseEntity.accepted().body(printTaskService.createPrintTask(spec));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PrintTaskDto> getPrintTask(@PathVariable UUID id) {
        return ResponseEntity.ok(printTaskService.getPrintTask(id));
    }
}
