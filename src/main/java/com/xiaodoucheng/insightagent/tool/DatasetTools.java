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
    @Tool(description = "分析指定数据集中某个数值随时间的变化趋势：按日期列分组聚合数值列，返回按日期排序的序列、每期环比变化率（%）和整体变化率（%）。需要看走势、涨跌、趋势时调用，例如最近销售额趋势怎么样")
    public String trendAnalysis(@ToolParam(description = "数据集ID") Long datasetId,
                                @ToolParam(description = "日期列名，例如 日期") String dateColumn,
                                @ToolParam(description = "要观察的数值列名，例如 销售额") String valueColumn,
                                @ToolParam(description = "聚合方式：SUM、AVG、COUNT、MAX、MIN 之一") String agg) {
        System.out.println("trendAnalysis, datasetId=" + datasetId + ", date=" + dateColumn + ", value=" + valueColumn + ", agg=" + agg);
        var result = datasetService.trend(datasetId, dateColumn, valueColumn, agg);
        if (!Boolean.TRUE.equals(result.getSuccess())) {
            return "趋势分析失败，请检查列名是否正确";
        }
        try {
            return objectMapper.writeValueAsString(result.getData());
        } catch (JsonProcessingException e) {
            return "统计数据序列化失败";
        }
    }
    @Tool(description = "生成图表数据：返回可直接供前端 ECharts 渲染的结构化数据，含类目数组、数值数组和饼图数据。当用户想看图、画图、要柱状图/折线图/饼图时调用")
    public String chartData(@ToolParam(description = "数据集ID") Long datasetId,
                            @ToolParam(description = "图表类型：bar 柱状图、line 折线图、pie 饼图") String chartType,
                            @ToolParam(description = "类目列名，例如 门店；折线图时传日期列名") String categoryColumn,
                            @ToolParam(description = "数值列名，例如 销售额") String valueColumn,
                            @ToolParam(description = "聚合方式：SUM、AVG、COUNT、MAX、MIN 之一") String agg) {
        System.out.println("chartData, datasetId=" + datasetId + ", type=" + chartType + ", category=" + categoryColumn + ", value=" + valueColumn + ", agg=" + agg);
        var result = datasetService.chartData(datasetId, chartType, categoryColumn, valueColumn, agg);
        if (!Boolean.TRUE.equals(result.getSuccess())) {
            return "生成图表数据失败，请检查列名是否正确";
        }
        try {
            return objectMapper.writeValueAsString(result.getData());
        } catch (JsonProcessingException e) {
            return "统计数据序列化失败";
        }
    }
}
