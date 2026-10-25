package com.xiaodoucheng.insightagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dataset")
public class Dataset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String fileType;
    private Integer rowCount;
    private Integer colCount;
    private String columnsJson;
    private LocalDateTime createTime;
}
