package com.xiaodoucheng.insightagent.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaodoucheng.insightagent.entity.Dataset;
import com.xiaodoucheng.insightagent.entity.DatasetRow;
import com.xiaodoucheng.insightagent.mapper.DatasetMapper;
import com.xiaodoucheng.insightagent.mapper.DatasetRowMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DatasetParseService {



    private final DatasetMapper datasetMapper;
    private final DatasetRowMapper datasetRowMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatasetParseService(DatasetMapper datasetMapper, DatasetRowMapper datasetRowMapper) {
        this.datasetMapper = datasetMapper;
        this.datasetRowMapper = datasetRowMapper;
    }

    @Async("datasetExecutor")
    public void parseAsync(Long datasetId, byte[] bytes, String fileType) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            return;
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
            var readerBuilder = EasyExcel.read(new ByteArrayInputStream(bytes), listener);
            if ("csv".equals(fileType)) {
                readerBuilder.excelType(ExcelTypeEnum.CSV);
            }
            readerBuilder.sheet().doRead();
            if (columns.isEmpty()) {
                dataset.setStatus("FAILED");
                datasetMapper.updateById(dataset);
                return;
            }
            dataset.setRowCount(rows.size());
            dataset.setColCount(columns.size());
            dataset.setColumnsJson(objectMapper.writeValueAsString(columns));
            for (int i = 0; i < rows.size(); i++) {
                DatasetRow row = new DatasetRow();
                row.setDatasetId(datasetId);
                row.setRowIndex(i);
                row.setDataJson(objectMapper.writeValueAsString(rows.get(i)));
                datasetRowMapper.insert(row);
            }
            dataset.setStatus("DONE");
            datasetMapper.updateById(dataset);
            log.info("数据集 {} 解析完成，共 {} 行", datasetId, rows.size());
        } catch (IOException e) {
            log.error("数据集 {} 解析失败", datasetId, e);
            dataset.setStatus("FAILED");
            datasetMapper.updateById(dataset);
        }
    }
}
