package com.xiaodoucheng.insightagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaodoucheng.insightagent.common.Result;
import com.xiaodoucheng.insightagent.entity.AnalysisRecord;
import com.xiaodoucheng.insightagent.entity.Dataset;
import com.xiaodoucheng.insightagent.mapper.AnalysisRecordMapper;
import com.xiaodoucheng.insightagent.mapper.DatasetMapper;
import com.xiaodoucheng.insightagent.tool.DatasetTools;
import com.xiaodoucheng.insightagent.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import com.xiaodoucheng.insightagent.tool.DatasetTools;
import reactor.core.publisher.Flux;
import com.xiaodoucheng.insightagent.utils.UserHolder;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Supplier;


@Slf4j
@Service
public class AnalysisService {

    private final DatasetService datasetService;
    private final DatasetMapper datasetMapper;
    private final AnalysisRecordMapper analysisRecordMapper;
    private final ChatClient chatClient;
    private final DatasetTools datasetTools;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageChatMemoryAdvisor memoryAdvisor;
    public AnalysisService(DatasetService datasetService, DatasetMapper datasetMapper,
                           AnalysisRecordMapper analysisRecordMapper,
                           ChatClient.Builder chatClientBuilder, DatasetTools datasetTools,ChatMemory chatMemory) {
        this.datasetService = datasetService;
        this.datasetMapper = datasetMapper;
        this.analysisRecordMapper = analysisRecordMapper;
        this.chatClient = chatClientBuilder.build();
        this.datasetTools = datasetTools;
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    public Result analyze(Long datasetId) {
        Result overviewResult = datasetService.overview(datasetId);
        if (!Boolean.TRUE.equals(overviewResult.getSuccess())) {
            return overviewResult;
        }
        String statsJson;
        try {
            statsJson = objectMapper.writeValueAsString(overviewResult.getData());
        } catch (JsonProcessingException e) {
            return Result.fail("统计数据序列化失败");
        }
        String content;
        try {
            content = callWithRetry(() -> chatClient.prompt()
                    .system("你是一名资深数据分析师，擅长从统计结果中提炼业务洞察。")
                    .user("下面是某个数据集的统计概况（JSON），包含行列数、每列的类型、缺失值、数值列的合计/均值/最大/最小、文本列的高频取值：\n"
                            + statsJson
                            + "\n请用中文写一段分析：先概括数据整体情况，再指出 2 到 3 个关键发现，最后给 1 到 2 条可执行的经营建议。只能使用统计中给出的数字，不要编造。")
                    .call()
                    .content(), "一键分析");
        } catch (Exception e) {
            log.error("调用 DeepSeek 失败（重试后仍失败）", e);
            return Result.fail("模型调用失败，先检查 DeepSeek 余额和网络");
        }

        AnalysisRecord record = new AnalysisRecord();
        record.setUserId(UserHolder.getUser().getId());
        record.setDatasetId(datasetId);
        record.setStatsJson(statsJson);
        record.setContent(content);
        analysisRecordMapper.insert(record);
        return Result.ok(record);
    }

    public Result history(Long datasetId) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        Long userId = UserHolder.getUser().getId();
        if (dataset == null || !dataset.getUserId().equals(userId)) {
            return Result.fail("数据集不存在");
        }
        List<AnalysisRecord> records = analysisRecordMapper.selectList(new LambdaQueryWrapper<AnalysisRecord>()
                .eq(AnalysisRecord::getDatasetId, datasetId)
                .orderByDesc(AnalysisRecord::getCreateTime));
        return Result.ok(records);
    }
    public String chat(Long datasetId, String question) {
        return chatClient.prompt()
                .system("你是一名数据分析助手。回答涉及具体数字时，必须调用工具获取真实统计，不许编造。当前数据集ID是：" + datasetId)
                .user(question)
                .advisors(memoryAdvisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, UserHolder.getUser().getId() + ":" + datasetId))
                .tools(datasetTools)
                .toolContext(Map.of("userId", UserHolder.getUser().getId()))
                .call()
                .content();
    }
    public Result chartData(Long datasetId, String chartType, String categoryColumn, String valueColumn, String agg) {
        return datasetService.chartData(datasetId, chartType, categoryColumn, valueColumn, agg);
    }
    public Flux<String> chatStream(Long datasetId, String question) {
        return chatClient.prompt()
                .system("你是一名数据分析助手。回答涉及具体数字时，必须调用工具获取真实统计，不许编造。当前数据集ID是：" + datasetId)
                .user(question)
                .advisors(memoryAdvisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, UserHolder.getUser().getId() + ":" + datasetId))
                .tools(datasetTools)
                .toolContext(Map.of("userId", UserHolder.getUser().getId()))
                .stream()
                .content();
    }
    public Result report(Long datasetId) {
        Result overviewResult = datasetService.overview(datasetId);
        if (!Boolean.TRUE.equals(overviewResult.getSuccess())) {
            return overviewResult;
        }
        Map<String, Object> ov = (Map<String, Object>) overviewResult.getData();
        List<Map<String, Object>> cols = (List<Map<String, Object>>) ov.get("columns");

        String numericCol = null;
        String textCol = null;
        String dateCol = null;
        List<Map<String, Object>> numericCols = new ArrayList<>();
        for (Map<String, Object> col : cols) {
            String name = (String) col.get("name");
            if ("numeric".equals(col.get("type"))) {
                if (numericCol == null) {
                    numericCol = name;
                }
                numericCols.add(col);
            } else {
                if (dateCol == null && (name.contains("日期") || name.contains("时间"))) {
                    dateCol = name;
                } else if (textCol == null) {
                    textCol = name;
                }
            }
        }

        List<Map<String, Object>> kpis = new ArrayList<>();
        kpis.add(kpi("总行数", ov.get("rowCount")));
        kpis.add(kpi("总列数", ov.get("colCount")));
        for (int i = 0; i < Math.min(2, numericCols.size()); i++) {
            Map<String, Object> col = numericCols.get(i);
            kpis.add(kpi(col.get("name") + "总和", col.get("sum")));
            kpis.add(kpi(col.get("name") + "平均", col.get("avg")));
        }

        List<Map<String, Object>> charts = new ArrayList<>();
        if (textCol != null && numericCol != null) {
            Result bar = datasetService.chartData(datasetId, "bar", textCol, numericCol, "SUM");
            if (Boolean.TRUE.equals(bar.getSuccess())) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", "各" + textCol + "的" + numericCol + "总和");
                item.put("data", bar.getData());
                charts.add(item);
            }
        }
        if (dateCol != null && numericCol != null) {
            Result line = datasetService.chartData(datasetId, "line", dateCol, numericCol, "SUM");
            if (Boolean.TRUE.equals(line.getSuccess())) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", numericCol + "随" + dateCol + "的趋势");
                item.put("data", line.getData());
                charts.add(item);
            }
        }

        String conclusion;
        try {
            String statsJson = objectMapper.writeValueAsString(ov);
            conclusion = callWithRetry(() -> chatClient.prompt()
                    .system("你是一名资深数据分析师。根据给定的数据集统计概况 JSON，写一段中文分析结论，200 字左右，包含两到三个核心发现和一条可执行建议。只输出正文，不要加标题，不许编造 JSON 里没有的数字。")
                    .user(statsJson)
                    .call()
                    .content(), "报告结论");
        } catch (Exception e) {
            conclusion = "结论生成失败，请稍后重试。";
        }


        Dataset dataset = datasetMapper.selectById(datasetId);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("datasetId", datasetId);
        report.put("datasetName", dataset == null ? "" : dataset.getName());
        report.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        report.put("kpis", kpis);
        report.put("charts", charts);
        report.put("conclusion", conclusion);
        return Result.ok(report);
    }

    private Map<String, Object> kpi(String name, Object value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("value", value);
        return item;
    }
    private String callWithRetry(Supplier<String> call, String scene) {
        Exception last = null;
        for (int i = 1; i <= 3; i++) {
            try {
                return call.get();
            } catch (Exception e) {
                last = e;
                log.warn("{} 第 {} 次调用失败", scene, i, e);
                if (i < 3) {
                    try {
                        Thread.sleep(500L * i);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        throw new RuntimeException(last);
    }

}

