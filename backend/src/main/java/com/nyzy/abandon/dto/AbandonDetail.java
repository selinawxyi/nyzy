package com.nyzy.abandon.dto;

import com.nyzy.abandon.entity.AbandonParcel;
import com.nyzy.abandon.entity.AbandonReason;
import com.nyzy.abandon.entity.AbandonTask;
import lombok.Data;

import java.util.List;

/** 撂荒地块详情 (含原因填报与治理任务) */
@Data
public class AbandonDetail {
    private AbandonParcel parcel;
    private List<AbandonReason> reasons;
    private List<AbandonTask> tasks;
}
