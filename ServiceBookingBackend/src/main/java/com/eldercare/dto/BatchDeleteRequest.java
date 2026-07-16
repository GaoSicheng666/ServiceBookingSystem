package com.eldercare.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 管理员一次永久删除多条预约记录。 */
public class BatchDeleteRequest {

    @NotEmpty(message = "请至少选择一条预约记录")
    @Size(max = 100, message = "一次最多删除100条预约记录")
    private List<@Positive(message = "预约编号必须为正数") Integer> ids;

    public List<Integer> getIds() { return ids; }
    public void setIds(List<Integer> ids) { this.ids = ids; }
}
