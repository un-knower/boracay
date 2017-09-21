package com.hex.bigdata.udsp.im.service;

import com.hex.bigdata.udsp.common.util.UdspCommonUtil;
import com.hex.bigdata.udsp.im.constant.RealtimeStatus;
import com.hex.bigdata.udsp.im.dao.RealtimeTotalMapper;
import com.hex.bigdata.udsp.im.model.RealtimeTotalInfo;
import com.hex.bigdata.udsp.im.provider.impl.model.modeling.MqModel;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * Created by JunjieM on 2017-9-14.
 */
@Service
public class RealtimeTotalService {
    private static Logger logger = LogManager.getLogger(RealtimeTotalService.class);
    private static final String REALTIME_TOTAL_KEY = "REALTIME_TOTAL";
    private static final String HOST_KEY = UdspCommonUtil.getLocalIpFromInetAddress();

    @Autowired
    private RealtimeTotalMapper realtimeTotalMapper;

    public void readyStart(MqModel model) {
        try {
            String id = model.getId();
            RealtimeTotalInfo realtimeInfo = new RealtimeTotalInfo();
            realtimeInfo.setStartHost(HOST_KEY);
            Date nowDate = new Date();
            realtimeInfo.setStartTime(nowDate);
            realtimeInfo.setStatus(RealtimeStatus.READY_START);
            realtimeInfo.setUpdateTime(nowDate);
            realtimeInfo.setModel(model);
            insert(id, realtimeInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void starting(String id) {
        try {
            RealtimeTotalInfo realtimeInfo = select(id);
            realtimeInfo.setStatus(RealtimeStatus.STARTING);
            realtimeInfo.setUpdateTime(new Date());
            update(id, realtimeInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void running(String id) {
        try {
            RealtimeTotalInfo realtimeInfo = select(id);
            realtimeInfo.setStatus(RealtimeStatus.RUNNING);
            Date nowDate = new Date();
            realtimeInfo.setRunTime(nowDate);
            realtimeInfo.setUpdateTime(nowDate);
            update(id, realtimeInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startFail(String id) {
        try {
            RealtimeTotalInfo realtimeInfo = select(id);
            realtimeInfo.setStatus(RealtimeStatus.START_FAIL);
            realtimeInfo.setUpdateTime(new Date());
            update(id, realtimeInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void readyStop(String id) {
        try {
            RealtimeTotalInfo realtimeInfo = select(id);
            realtimeInfo.setStopHost(HOST_KEY);
            Date nowDate = new Date();
            realtimeInfo.setStopTime(nowDate);
            realtimeInfo.setStatus(RealtimeStatus.READY_STOP);
            realtimeInfo.setUpdateTime(nowDate);
            update(id, realtimeInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stoping(String id) {
        try {
            RealtimeTotalInfo realtimeInfo = select(id);
            realtimeInfo.setStatus(RealtimeStatus.STOPING);
            realtimeInfo.setUpdateTime(new Date());
            update(id, realtimeInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopSuccess(String id) {
        try {
            RealtimeTotalInfo realtimeInfo = select(id);
            realtimeInfo.setStatus(RealtimeStatus.STOP_SUCCESS);
            Date nowDate = new Date();
            realtimeInfo.setUpdateTime(nowDate);
            realtimeInfo.setEndTime(nowDate);
            update(id, realtimeInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopFail(String id) {
        try {
            RealtimeTotalInfo realtimeInfo = select(id);
            realtimeInfo.setStatus(RealtimeStatus.STOP_FAIL);
            realtimeInfo.setUpdateTime(new Date());
            update(id, realtimeInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void runFail(String id) {
        try {
            RealtimeTotalInfo realtimeInfo = select(id);
            realtimeInfo.setStatus(RealtimeStatus.RUN_FAIL);
            realtimeInfo.setUpdateTime(new Date());
            update(id, realtimeInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void insert(String id, RealtimeTotalInfo realtimeInfo) {
        realtimeTotalMapper.insert(REALTIME_TOTAL_KEY + ":" + id, realtimeInfo);
    }

    public void update(String id, RealtimeTotalInfo realtimeInfo) {
        realtimeTotalMapper.update(REALTIME_TOTAL_KEY + ":" + id, realtimeInfo);
    }

    public RealtimeTotalInfo select(String id) {
        return realtimeTotalMapper.select(REALTIME_TOTAL_KEY + ":" + id);
    }

    public List<RealtimeTotalInfo> selectList() {
        return realtimeTotalMapper.selectLike(REALTIME_TOTAL_KEY);
    }

    public void delete(String id) {
        realtimeTotalMapper.delete(REALTIME_TOTAL_KEY + ":" + id);
    }
}