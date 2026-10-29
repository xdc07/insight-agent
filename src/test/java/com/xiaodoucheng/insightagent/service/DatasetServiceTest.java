package com.xiaodoucheng.insightagent.service;

import com.xiaodoucheng.insightagent.common.Result;
import com.xiaodoucheng.insightagent.dto.UserDTO;
import com.xiaodoucheng.insightagent.entity.Dataset;
import com.xiaodoucheng.insightagent.entity.DatasetRow;
import com.xiaodoucheng.insightagent.mapper.DatasetMapper;
import com.xiaodoucheng.insightagent.mapper.DatasetRowMapper;
import com.xiaodoucheng.insightagent.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetServiceTest {

    @Mock
    private DatasetMapper datasetMapper;
    @Mock
    private DatasetRowMapper datasetRowMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private DatasetParseService datasetParseService;

    private DatasetService datasetService;

    @BeforeEach
    void setUp() {
        datasetService = new DatasetService(datasetMapper, datasetRowMapper,
                stringRedisTemplate, datasetParseService);
        UserDTO user = new UserDTO();
        user.setId(1L);
        UserHolder.saveUser(user);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    private Dataset dataset() {
        Dataset d = new Dataset();
        d.setId(1L);
        d.setUserId(1L);
        d.setStatus("DONE");
        d.setColumnsJson("[\"日期\",\"门店\",\"销售额\"]");
        return d;
    }

    private DatasetRow row(int idx, String date, String store, int amount) {
        DatasetRow r = new DatasetRow();
        r.setDatasetId(1L);
        r.setRowIndex(idx);
        r.setDataJson("{\"日期\":\"" + date + "\",\"门店\":\"" + store
                + "\",\"销售额\":\"" + amount + "\"}");
        return r;
    }

    // 24 行构造数据：按门店合计 17510 / 12830 / 10870，按日期合计与样本一致
    private List<DatasetRow> salesRows() {
        String[] dates = {"2026-09-01", "2026-09-02", "2026-09-03", "2026-09-04",
                "2026-09-05", "2026-09-06", "2026-09-07", "2026-09-08"};
        String[] stores = {"朝阳店", "海淀店", "西城店"};
        int[][] amounts = {
                {2000, 1500, 1160},
                {1900, 1400, 1290},
                {2600, 1800, 1535},
                {2100, 1600, 1295},
                {1900, 1450, 1190},
                {2500, 1850, 1520},
                {2400, 1700, 1485},
                {2110, 1530, 1395}
        };
        List<DatasetRow> rows = new ArrayList<>();
        int idx = 0;
        for (int d = 0; d < dates.length; d++) {
            for (int s = 0; s < stores.length; s++) {
                rows.add(row(idx++, dates[d], stores[s], amounts[d][s]));
            }
        }
        return rows;
    }

    // 就是刚才验证用的 10 行：2 行重复，1 个异常值 9999
    private List<DatasetRow> qualityRows() {
        List<DatasetRow> rows = new ArrayList<>();
        rows.add(row(0, "2026-09-01", "朝阳店", 100));
        rows.add(row(1, "2026-09-02", "朝阳店", 110));
        rows.add(row(2, "2026-09-03", "海淀店", 105));
        rows.add(row(3, "2026-09-04", "海淀店", 98));
        rows.add(row(4, "2026-09-05", "西城店", 102));
        rows.add(row(5, "2026-09-06", "西城店", 108));
        rows.add(row(6, "2026-09-01", "朝阳店", 100));
        rows.add(row(7, "2026-09-07", "朝阳店", 9999));
        rows.add(row(8, "2026-09-08", "海淀店", 101));
        rows.add(row(9, "2026-09-03", "海淀店", 105));
        return rows;
    }

    @Test
    void groupByStoreSum() {
        when(datasetMapper.selectById(1L)).thenReturn(dataset());
        when(datasetRowMapper.selectList(any())).thenReturn(salesRows());

        Result result = datasetService.groupBy(1L, "门店", "销售额", "SUM");

        assertTrue(result.getSuccess());
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getData();
        assertEquals(3, items.size());
        assertEquals("朝阳店", items.get(0).get("group"));
        assertEquals(17510.0, (Double) items.get(0).get("value"), 0.001);
        assertEquals(12830.0, (Double) items.get(1).get("value"), 0.001);
        assertEquals(10870.0, (Double) items.get(2).get("value"), 0.001);
    }

    @Test
    void trendOverallChangeRate() {
        when(datasetMapper.selectById(1L)).thenReturn(dataset());
        when(datasetRowMapper.selectList(any())).thenReturn(salesRows());

        Result result = datasetService.trend(1L, "日期", "销售额", "SUM");

        assertTrue(result.getSuccess());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> series = (List<Map<String, Object>>) data.get("series");
        assertEquals(8, series.size());
        assertEquals(4660.0, (Double) series.get(0).get("value"), 0.001);
        assertEquals(5035.0, (Double) series.get(7).get("value"), 0.001);
        assertNull(series.get(0).get("changeRate"));
        assertEquals(-1.5, (Double) series.get(1).get("changeRate"), 0.001);
        assertEquals(8.05, (Double) data.get("overallChangeRate"), 0.001);
    }

    @Test
    void overviewDataQuality() {
        when(datasetMapper.selectById(1L)).thenReturn(dataset());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(datasetRowMapper.selectList(any())).thenReturn(qualityRows());

        Result result = datasetService.overview(1L);

        assertTrue(result.getSuccess());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(10, data.get("rowCount"));
        Map<String, Object> quality = (Map<String, Object>) data.get("quality");
        assertEquals(2, quality.get("duplicateRows"));
        assertEquals(20.0, (Double) quality.get("duplicateRate"), 0.001);
        List<Map<String, Object>> columns = (List<Map<String, Object>>) data.get("columns");
        Map<String, Object> amount = columns.stream()
                .filter(c -> "销售额".equals(c.get("name")))
                .findFirst().orElseThrow();
        assertEquals(10928.0, (Double) amount.get("sum"), 0.001);
        assertEquals(1092.8, (Double) amount.get("avg"), 0.001);
        assertEquals(1, amount.get("outliers"));
    }
}
