package com.xiaodoucheng.insightagent.controller;

import com.xiaodoucheng.insightagent.common.Result;
import com.xiaodoucheng.insightagent.service.DatasetService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/dataset")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @PostMapping("/upload")
    public Result upload(@RequestParam("file") MultipartFile file) {
        return datasetService.upload(file);
    }

    @GetMapping("/list")
    public Result list() {
        return datasetService.list();
    }

    @GetMapping("/{id}")
    public Result detail(@PathVariable Long id) {
        return datasetService.detail(id);
    }
    @GetMapping("/{id}/overview")
    public Result overview(@PathVariable Long id) {
        return datasetService.overview(id);
    }
    @GetMapping("/{id}/status")
    public Result status(@PathVariable Long id) {
        return datasetService.status(id);
    }
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        return datasetService.delete(id);
    }
}
