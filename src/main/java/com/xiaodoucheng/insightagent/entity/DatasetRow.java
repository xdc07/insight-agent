package com.xiaodoucheng.insightagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dataset_row")
public class DatasetRow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasetId;
    private Integer rowIndex;
    private String dataJson;
}

