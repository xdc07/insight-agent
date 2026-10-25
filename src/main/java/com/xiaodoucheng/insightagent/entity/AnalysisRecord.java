package com.xiaodoucheng.insightagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("analysis_record")
public class AnalysisRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long datasetId;
    private String statsJson;
    private String content;
    private LocalDateTime createTime;
}
