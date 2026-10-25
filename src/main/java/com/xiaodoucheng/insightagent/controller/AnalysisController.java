package com.xiaodoucheng.insightagent.controller;

import com.xiaodoucheng.insightagent.common.Result;
import com.xiaodoucheng.insightagent.service.AnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/{datasetId}")
    public Result analyze(@PathVariable Long datasetId) {
        return analysisService.analyze(datasetId);
    }

    @GetMapping("/{datasetId}")
    public Result history(@PathVariable Long datasetId) {
        return analysisService.history(datasetId);
    }
}

