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
    @Tool(description = "对指定数据集做分组统计：按分组列分组，对数值列做聚合。需要比较不同类别的数值时调用，例如各地区的销售额总和、各渠道的平均订单量")
    public String groupStats(@ToolParam(description = "数据集ID") Long datasetId,
                             @ToolParam(description = "分组列名，例如 地区") String groupColumn,
                             @ToolParam(description = "要统计的数值列名，例如 销售额") String valueColumn,
                             @ToolParam(description = "聚合方式，取值 SUM 求和、AVG 平均、COUNT 计数、MAX 最大、MIN 最小") String agg) {
        System.out.println("groupStats, datasetId=" + datasetId + ", group=" + groupColumn + ", value=" + valueColumn + ", agg=" + agg);
        var result = datasetService.groupBy(datasetId, groupColumn, valueColumn, agg);
        if (!Boolean.TRUE.equals(result.getSuccess())) {
            return "分组统计失败，请检查列名是否正确";
        }
        try {
            return objectMapper.writeValueAsString(result.getData());
        } catch (JsonProcessingException e) {
            return "统计数据序列化失败";
        }
    }
}
