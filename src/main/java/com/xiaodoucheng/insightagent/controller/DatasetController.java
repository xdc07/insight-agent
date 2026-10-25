package com.xiaodoucheng.insightagent.controller;

import com.xiaodoucheng.insightagent.common.Result;
import com.xiaodoucheng.insightagent.service.DatasetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

}
