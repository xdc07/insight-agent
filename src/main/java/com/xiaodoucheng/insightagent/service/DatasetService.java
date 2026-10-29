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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.redis.core.RedisTemplate;
import java.io.IOException;
import java.util.*;

@Service
public class DatasetService {

    private final DatasetMapper datasetMapper;
    private final DatasetRowMapper datasetRowMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StringRedisTemplate stringRedisTemplate;
    private final DatasetParseService datasetParseService;
    public DatasetService(DatasetMapper datasetMapper, DatasetRowMapper datasetRowMapper, StringRedisTemplate stringRedisTemplate,DatasetParseService datasetParseService) {
        this.datasetMapper = datasetMapper;
        this.datasetRowMapper = datasetRowMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.datasetParseService = datasetParseService;
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
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            return Result.fail("文件读取失败");
        }
        Dataset dataset = new Dataset();
        dataset.setUserId(UserHolder.getUser().getId());
        dataset.setName(originalFilename);
        dataset.setFileType(fileType);
        dataset.setRowCount(0);
        dataset.setColCount(0);
        dataset.setColumnsJson("[]");
        dataset.setStatus("PROCESSING");
        datasetMapper.insert(dataset);
        datasetParseService.parseAsync(dataset.getId(), bytes, fileType);
        return Result.ok(dataset.getId());
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
        if ("PROCESSING".equals(dataset.getStatus())) {
            return Result.fail("数据还在处理中，请稍等");
        }
        if ("FAILED".equals(dataset.getStatus())) {
            return Result.fail("数据解析失败，请重新上传");
        }
        String cacheKey = "insight:dataset:overview:" + id;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                Map<String, Object> cacheResult = objectMapper.readValue(cached,
                        new TypeReference<Map<String, Object>>() {});
                System.out.println("overview 命中缓存 datasetId=" + id);
                return Result.ok(cacheResult);
            } catch (JsonProcessingException e) {
                stringRedisTemplate.delete(cacheKey);
            }
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
            stat.put("missingRate", data.isEmpty() ? 0 : round2(missing * 100.0 / data.size()));
            if (numeric && !numbers.isEmpty()) {
                stat.put("type", "numeric");
                double sum = numbers.stream().mapToDouble(Double::doubleValue).sum();
                double max = numbers.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                double min = numbers.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                stat.put("sum", round2(sum));
                stat.put("avg", round2(sum / numbers.size()));
                stat.put("max", round2(max));
                stat.put("min", round2(min));
                stat.put("outliers", countOutliers(numbers));
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
        Set<String> seenRows = new HashSet<>();
        int duplicateRows = 0;
        for (Map<String, String> row : data) {
            StringBuilder key = new StringBuilder();
            for (String column : columns) {
                String v = row.get(column);
                key.append(v == null ? "" : v.trim()).append('\u0001');
            }
            if (!seenRows.add(key.toString())) {
                duplicateRows++;
            }
        }
        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("duplicateRows", duplicateRows);
        quality.put("duplicateRate", data.isEmpty() ? 0 : round2(duplicateRows * 100.0 / data.size()));
        result.put("columns", columnStats);
        result.put("quality", quality);
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result));
            System.out.println("overview 已写入缓存 datasetId=" + id);
        } catch (JsonProcessingException e) {
        }
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
    public Result chartData(Long id, String chartType, String categoryColumn, String valueColumn, String agg) {
        String type = chartType == null ? "bar" : chartType.toLowerCase();
        List<String> categories = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        if ("line".equals(type)) {
            Result t = trend(id, categoryColumn, valueColumn, agg);
            if (!Boolean.TRUE.equals(t.getSuccess())) {
                return t;
            }
            Map<String, Object> tData = (Map<String, Object>) t.getData();
            List<Map<String, Object>> series = (List<Map<String, Object>>) tData.get("series");
            for (Map<String, Object> point : series) {
                categories.add(String.valueOf(point.get("date")));
                values.add((Double) point.get("value"));
            }
        } else {
            Result g = groupBy(id, categoryColumn, valueColumn, agg);
            if (!Boolean.TRUE.equals(g.getSuccess())) {
                return g;
            }
            List<Map<String, Object>> items = (List<Map<String, Object>>) g.getData();
            for (Map<String, Object> item : items) {
                categories.add(String.valueOf(item.get("group")));
                values.add((Double) item.get("value"));
            }
            if (!"pie".equals(type)) {
                type = "bar";
            }
        }
        List<Map<String, Object>> pieData = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", categories.get(i));
            p.put("value", values.get(i));
            pieData.add(p);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chartType", type);
        result.put("categories", categories);
        result.put("seriesName", valueColumn);
        result.put("seriesData", values);
        result.put("pieData", pieData);
        return Result.ok(result);
    }
    public Result status(Long id) {
        Dataset dataset = datasetMapper.selectById(id);
        Long userId = UserHolder.getUser().getId();
        if (dataset == null || !dataset.getUserId().equals(userId)) {
            return Result.fail("数据集不存在");
        }
        return Result.ok(Map.of("status", dataset.getStatus()));
    }
    public Result delete(Long id) {
        Dataset dataset = datasetMapper.selectById(id);
        Long userId = UserHolder.getUser().getId();
        if (dataset == null || !dataset.getUserId().equals(userId)) {
            return Result.fail("数据集不存在");
        }
        if ("PROCESSING".equals(dataset.getStatus())) {
            return Result.fail("数据还在处理中，稍后再删");
        }
        datasetRowMapper.delete(new LambdaQueryWrapper<DatasetRow>()
                .eq(DatasetRow::getDatasetId, id));
        datasetMapper.deleteById(id);
        stringRedisTemplate.delete("insight:dataset:overview:" + id);
        return Result.ok();
    }
    private int countOutliers(List<Double> numbers) {
        if (numbers.size() < 4) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(numbers);
        sorted.sort(Double::compareTo);
        double q1 = percentile(sorted, 25);
        double q3 = percentile(sorted, 75);
        double iqr = q3 - q1;
        double lower = q1 - 1.5 * iqr;
        double upper = q3 + 1.5 * iqr;
        int count = 0;
        for (double v : numbers) {
            if (v < lower || v > upper) {
                count++;
            }
        }
        return count;
    }
    private double percentile(List<Double> sorted, double p) {
        double pos = p / 100.0 * (sorted.size() - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (lo == hi) {
            return sorted.get(lo);
        }
        double weight = pos - lo;
        return sorted.get(lo) * (1 - weight) + sorted.get(hi) * weight;
    }
}
