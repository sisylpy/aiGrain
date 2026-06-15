package com.nongxinle.ai.storeannouncement.dto;

import lombok.Data;

@Data
public class StoreAnnouncementPublishRequest {

    private Long userId;

    /** 门店锚点部门；缺省从来源对象推导 */
    private Long departmentId;

    private Long distributerId;

    /** 可选覆盖标题；缺省由服务端从来源推导 */
    private String title;
}
