package com.hex.bigdata.udsp.im.model;

import com.hex.bigdata.udsp.im.constant.BatchStatus;

import java.util.Date;

/**
 * Created by JunjieM on 2017-9-21.
 */
public class BatchInfo {
    private String id; // 主键
    private String host; // 主机
    private BatchStatus status; // 准备构建、正在构建、构建成功、构建失败
    private Date startTime; // 开始的时间
    private Date endTime; // 结束的时间
    private Date updateTime; // 更新的时间
    private int percent; // 进度
    private String message; // 信息

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public BatchStatus getStatus() {
        return status;
    }

    public void setStatus(BatchStatus status) {
        this.status = status;
    }

    public int getPercent() {
        return percent;
    }

    public void setPercent(int percent) {
        this.percent = percent;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}