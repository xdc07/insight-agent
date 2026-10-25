package com.xiaodoucheng.insightagent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaodoucheng.insightagent.service.DatasetService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class DatasetTools {

    private final DatasetService datasetService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatasetTools(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @Tool(description = "获取指定数据集的数据概况：总行数、总列数、每列的类型与缺失值数量、数值列的和/平均/最大/最小、文本列的高频取值。需要了解某个数据集整体情况时调用")
    public String getDatasetOverview(@ToolParam(description = "数据集ID") Long datasetId) {
        System.out.println("【工具调用】getDatasetOverview, datasetId=" + datasetId);
        var result = datasetService.overview(datasetId);
        if (!Boolean.TRUE.equals(result.getSuccess())) {
            return "获取数据概况失败，请检查数据集ID是否正确";
        }
        try {
            return objectMapper.writeValueAsString(result.getData());
        } catch (JsonProcessingException e) {
            return "统计数据序列化失败";
        }
    }
}
