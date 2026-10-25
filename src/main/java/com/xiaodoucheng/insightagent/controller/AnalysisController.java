package com.xiaodoucheng.insightagent.controller;

import com.xiaodoucheng.insightagent.common.Result;
import com.xiaodoucheng.insightagent.service.AnalysisService;
import org.springframework.web.bind.annotation.*;

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
    @PostMapping("/chat/{datasetId}")
    public Result chat(@PathVariable Long datasetId, @RequestParam String question) {
        return Result.ok(analysisService.chat(datasetId, question));
    }
}

