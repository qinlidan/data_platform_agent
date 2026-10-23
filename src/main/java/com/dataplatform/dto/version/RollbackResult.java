package com.dataplatform.dto.version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 版本回滚结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollbackResult {

    /**
     * 结果码：0-成功，其他为失败
     */
    private Integer resultCode;

    /**
     * 结果消息
     */
    private String resultMsg;

    /**
     * 新创建的版本号
     */
    private Integer newVersion;

    /**
     * 回滚到的目标版本号
     */
    private Integer targetVersion;

    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return resultCode != null && resultCode == 0;
    }
}
