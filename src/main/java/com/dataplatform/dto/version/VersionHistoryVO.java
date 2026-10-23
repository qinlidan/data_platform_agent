package com.dataplatform.dto.version;

import com.dataplatform.enums.VersionAction;
import com.dataplatform.enums.VersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 版本历史响应
 */
@Data
@Builder
@Schema(description = "版本历史记录")
public class VersionHistoryVO {

    @Schema(description = "历史ID")
    private Long id;

    @Schema(description = "作业ID")
    private Long jobId;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "操作类型")
    private VersionAction action;

    @Schema(description = "操作描述")
    private String actionDesc;

    @Schema(description = "操作人ID")
    private Long operatorId;

    @Schema(description = "操作人名称")
    private String operatorName;

    @Schema(description = "原状态")
    private VersionStatus fromStatus;

    @Schema(description = "目标状态")
    private VersionStatus toStatus;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "关联版本号")
    private Integer relatedVersion;

    @Schema(description = "操作时间")
    private LocalDateTime createTime;
}