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
import java.util.List;

@Slf4j
@Service
public class AnalysisService {

    private final DatasetService datasetService;
    private final DatasetMapper datasetMapper;
    private final AnalysisRecordMapper analysisRecordMapper;
    private final ChatClient chatClient;
    private final DatasetTools datasetTools;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalysisService(DatasetService datasetService, DatasetMapper datasetMapper,
                           AnalysisRecordMapper analysisRecordMapper,
                           ChatClient.Builder chatClientBuilder, DatasetTools datasetTools) {
        this.datasetService = datasetService;
        this.datasetMapper = datasetMapper;
        this.analysisRecordMapper = analysisRecordMapper;
        this.chatClient = chatClientBuilder.build();
        this.datasetTools = datasetTools;
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
            content = chatClient.prompt()
                    .system("你是一名资深数据分析师，擅长从统计结果中提炼业务洞察。")
                    .user("下面是某个数据集的统计概况（JSON），包含行列数、每列的类型、缺失值、数值列的合计/均值/最大/最小、文本列的高频取值：\n"
                            + statsJson
                            + "\n请用中文写一段分析：先概括数据整体情况，再指出 2 到 3 个关键发现，最后给 1 到 2 条可执行的经营建议。只能使用统计中给出的数字，不要编造。")
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("调用 DeepSeek 失败", e);
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
                .tools(datasetTools)
                .call()
                .content();
    }
}

