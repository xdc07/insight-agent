package com.xiaodoucheng.insightagent.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaodoucheng.insightagent.common.Result;
import com.xiaodoucheng.insightagent.entity.Dataset;
import com.xiaodoucheng.insightagent.entity.DatasetRow;
import com.xiaodoucheng.insightagent.mapper.DatasetMapper;
import com.xiaodoucheng.insightagent.mapper.DatasetRowMapper;
import com.xiaodoucheng.insightagent.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DatasetService {

    private final DatasetMapper datasetMapper;
    private final DatasetRowMapper datasetRowMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatasetService(DatasetMapper datasetMapper, DatasetRowMapper datasetRowMapper) {
        this.datasetMapper = datasetMapper;
        this.datasetRowMapper = datasetRowMapper;
    }

    public Result upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        String fileType = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileType = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        }
        if (!List.of("xlsx", "xls", "csv").contains(fileType)) {
            return Result.fail("只支持 xlsx、xls、csv 文件");
        }

        List<String> columns = new ArrayList<>();
        List<Map<String, String>> rows = new ArrayList<>();
        AnalysisEventListener<Map<Integer, String>> listener = new AnalysisEventListener<>() {
            @Override
            public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                for (int i = 0; i < headMap.size(); i++) {
                    columns.add(headMap.get(i));
                }
            }

            @Override
            public void invoke(Map<Integer, String> data, AnalysisContext context) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < columns.size(); i++) {
                    row.put(columns.get(i), data.get(i));
                }
                rows.add(row);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        };

        try {
            var readerBuilder = EasyExcel.read(file.getInputStream(), listener);
            if ("csv".equals(fileType)) {
                readerBuilder.excelType(ExcelTypeEnum.CSV);
            }
            readerBuilder.sheet().doRead();

            if (columns.isEmpty()) {
                return Result.fail("文件没有表头");
            }

            Dataset dataset = new Dataset();
            dataset.setUserId(UserHolder.getUser().getId());
            dataset.setName(originalFilename);
            dataset.setFileType(fileType);
            dataset.setRowCount(rows.size());
            dataset.setColCount(columns.size());
            dataset.setColumnsJson(objectMapper.writeValueAsString(columns));
            datasetMapper.insert(dataset);

            for (int i = 0; i < rows.size(); i++) {
                DatasetRow row = new DatasetRow();
                row.setDatasetId(dataset.getId());
                row.setRowIndex(i);
                row.setDataJson(objectMapper.writeValueAsString(rows.get(i)));
                datasetRowMapper.insert(row);
            }
            return Result.ok(dataset.getId());
        } catch (IOException e) {
            return Result.fail("文件解析失败");
        }
    }

    public Result list() {
        Long userId = UserHolder.getUser().getId();
        List<Dataset> datasets = datasetMapper.selectList(new LambdaQueryWrapper<Dataset>()
                .eq(Dataset::getUserId, userId)
                .orderByDesc(Dataset::getCreateTime));
        return Result.ok(datasets);
    }

    public Result detail(Long id) {
        Dataset dataset = datasetMapper.selectById(id);
        Long userId = UserHolder.getUser().getId();
        if (dataset == null || !dataset.getUserId().equals(userId)) {
            return Result.fail("数据集不存在");
        }
        List<DatasetRow> rows = datasetRowMapper.selectList(new LambdaQueryWrapper<DatasetRow>()
                .eq(DatasetRow::getDatasetId, id)
                .orderByAsc(DatasetRow::getRowIndex));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataset", dataset);
        try {
            result.put("columns", objectMapper.readValue(dataset.getColumnsJson(),
                    new TypeReference<List<String>>() {}));
            List<Map<String, String>> data = new ArrayList<>();
            for (DatasetRow row : rows) {
                data.add(objectMapper.readValue(row.getDataJson(),
                        new TypeReference<Map<String, String>>() {}));
            }
            result.put("rows", data);
        } catch (JsonProcessingException e) {
            return Result.fail("数据解析失败");
        }
        return Result.ok(result);
    }
    public Result overview(Long id) {
        Dataset dataset = datasetMapper.selectById(id);
        Long userId = UserHolder.getUser().getId();
        if (dataset == null || !dataset.getUserId().equals(userId)) {
            return Result.fail("数据集不存在");
        }
        List<DatasetRow> rows = datasetRowMapper.selectList(new LambdaQueryWrapper<DatasetRow>()
                .eq(DatasetRow::getDatasetId, id)
                .orderByAsc(DatasetRow::getRowIndex));
        List<String> columns;
        List<Map<String, String>> data = new ArrayList<>();
        try {
            columns = objectMapper.readValue(dataset.getColumnsJson(),
                    new TypeReference<List<String>>() {});
            for (DatasetRow row : rows) {
                data.add(objectMapper.readValue(row.getDataJson(),
                        new TypeReference<Map<String, String>>() {}));
            }
        } catch (JsonProcessingException e) {
            return Result.fail("数据解析失败");
        }

        List<Map<String, Object>> columnStats = new ArrayList<>();
        for (String column : columns) {
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("name", column);
            int missing = 0;
            List<Double> numbers = new ArrayList<>();
            Map<String, Integer> counts = new LinkedHashMap<>();
            boolean numeric = true;
            for (Map<String, String> row : data) {
                String value = row.get(column);
                if (value == null || value.isBlank()) {
                    missing++;
                    continue;
                }
                String v = value.trim();
                counts.merge(v, 1, Integer::sum);
                try {
                    numbers.add(Double.parseDouble(v));
                } catch (NumberFormatException e) {
                    numeric = false;
                }
            }
            stat.put("missing", missing);
            if (numeric && !numbers.isEmpty()) {
                stat.put("type", "numeric");
                double sum = numbers.stream().mapToDouble(Double::doubleValue).sum();
                double max = numbers.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                double min = numbers.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                stat.put("sum", round2(sum));
                stat.put("avg", round2(sum / numbers.size()));
                stat.put("max", round2(max));
                stat.put("min", round2(min));
            } else {
                stat.put("type", "text");
                stat.put("distinctCount", counts.size());
                List<Map<String, Object>> top = new ArrayList<>();
                counts.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(5)
                        .forEach(e -> {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("value", e.getKey());
                            item.put("count", e.getValue());
                            top.add(item);
                        });
                stat.put("topValues", top);
            }
            columnStats.add(stat);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasetId", id);
        result.put("rowCount", data.size());
        result.put("colCount", columns.size());
        result.put("columns", columnStats);
        return Result.ok(result);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
    public Result groupBy(Long id, String groupColumn, String valueColumn, String agg) {
        Dataset dataset = datasetMapper.selectById(id);
        Long userId = UserHolder.getUser().getId();
        if (dataset == null || !dataset.getUserId().equals(userId)) {
            return Result.fail("数据集不存在");
        }
        List<DatasetRow> rows = datasetRowMapper.selectList(new LambdaQueryWrapper<DatasetRow>()
                .eq(DatasetRow::getDatasetId, id)
                .orderByAsc(DatasetRow::getRowIndex));
        List<String> columns;
        List<Map<String, String>> data = new ArrayList<>();
        try {
            columns = objectMapper.readValue(dataset.getColumnsJson(),
                    new TypeReference<List<String>>() {});
            for (DatasetRow row : rows) {
                data.add(objectMapper.readValue(row.getDataJson(),
                        new TypeReference<Map<String, String>>() {}));
            }
        } catch (JsonProcessingException e) {
            return Result.fail("数据解析失败");
        }
        if (!columns.contains(groupColumn)) {
            return Result.fail("分组列不存在：" + groupColumn);
        }
        if (!columns.contains(valueColumn)) {
            return Result.fail("数值列不存在：" + valueColumn);
        }
        String aggType = agg == null ? "SUM" : agg.toUpperCase();
        Map<String, List<Double>> grouped = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, String> row : data) {
            String group = row.get(groupColumn);
            if (group == null || group.isBlank()) {
                group = "(空)";
            }
            grouped.putIfAbsent(group, new ArrayList<>());
            counts.merge(group, 1, Integer::sum);
            String raw = row.get(valueColumn);
            if (raw != null && !raw.isBlank()) {
                try {
                    grouped.get(group).add(Double.parseDouble(raw.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Double>> e : grouped.entrySet()) {
            List<Double> values = e.getValue();
            double v;
            switch (aggType) {
                case "AVG" -> v = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                case "COUNT" -> v = counts.get(e.getKey());
                case "MAX" -> v = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                case "MIN" -> v = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                default -> v = values.stream().mapToDouble(Double::doubleValue).sum();
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("group", e.getKey());
            item.put("value", Math.round(v * 100.0) / 100.0);
            item.put("count", counts.get(e.getKey()));
            result.add(item);
        }
        result.sort((a, b) -> Double.compare((Double) b.get("value"), (Double) a.get("value")));
        return Result.ok(result);
    }
    public Result trend(Long id, String dateColumn, String valueColumn, String agg) {
        Result grouped = groupBy(id, dateColumn, valueColumn, agg);
        if (!Boolean.TRUE.equals(grouped.getSuccess())) {
            return grouped;
        }
        List<Map<String, Object>> items = (List<Map<String, Object>>) grouped.getData();
        items.sort((a, b) -> String.valueOf(a.get("group")).compareTo(String.valueOf(b.get("group"))));
        List<Map<String, Object>> series = new ArrayList<>();
        Double prev = null;
        for (Map<String, Object> item : items) {
            double v = (Double) item.get("value");
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", item.get("group"));
            point.put("value", v);
            if (prev == null || prev == 0) {
                point.put("changeRate", null);
            } else {
                point.put("changeRate", Math.round((v - prev) / prev * 10000.0) / 100.0);
            }
            series.add(point);
            prev = v;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("series", series);
        if (!series.isEmpty()) {
            double first = (Double) series.get(0).get("value");
            double last = (Double) series.get(series.size() - 1).get("value");
            result.put("overallChangeRate", first == 0 ? null : Math.round((last - first) / first * 10000.0) / 100.0);
        }
        return Result.ok(result);
    }

}
