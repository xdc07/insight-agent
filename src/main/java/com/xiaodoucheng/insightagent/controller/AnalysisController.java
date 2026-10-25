package com.xiaodoucheng.insightagent.controller;

import com.xiaodoucheng.insightagent.common.Result;
import com.xiaodoucheng.insightagent.service.AnalysisService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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
    @PostMapping("/chart/{datasetId}")
    public Result chart(@PathVariable Long datasetId,
                        @RequestParam String chartType,
                        @RequestParam String categoryColumn,
                        @RequestParam String valueColumn,
                        @RequestParam(defaultValue = "SUM") String agg) {
        return analysisService.chartData(datasetId, chartType, categoryColumn, valueColumn, agg);
    }
    @PostMapping(value = "/chat/stream/{datasetId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@PathVariable Long datasetId, @RequestParam String question) {
        return analysisService.chatStream(datasetId, question);
    }

}

