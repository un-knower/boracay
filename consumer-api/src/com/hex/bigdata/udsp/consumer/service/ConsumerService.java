package com.hex.bigdata.udsp.consumer.service;

import com.hex.bigdata.udsp.common.api.model.Page;
import com.hex.bigdata.udsp.common.constant.*;
import com.hex.bigdata.udsp.common.model.QueueIsFullResult;
import com.hex.bigdata.udsp.common.service.ComDatasourceService;
import com.hex.bigdata.udsp.common.util.*;
import com.hex.bigdata.udsp.consumer.dao.ResponseMapper;
import com.hex.bigdata.udsp.consumer.model.ConsumeRequest;
import com.hex.bigdata.udsp.consumer.model.Request;
import com.hex.bigdata.udsp.consumer.model.Response;
import com.hex.bigdata.udsp.iq.service.IqApplicationService;
import com.hex.bigdata.udsp.mc.model.Current;
import com.hex.bigdata.udsp.mc.model.McConsumeLog;
import com.hex.bigdata.udsp.mc.service.CurrentService;
import com.hex.bigdata.udsp.mc.service.McConsumeLogService;
import com.hex.bigdata.udsp.mc.service.RunQueueService;
import com.hex.bigdata.udsp.mc.service.WaitQueueService;
import com.hex.bigdata.udsp.mm.service.MmApplicationService;
import com.hex.bigdata.udsp.olq.dto.OlqApplicationDto;
import com.hex.bigdata.udsp.olq.service.OlqApplicationService;
import com.hex.bigdata.udsp.rc.model.RcService;
import com.hex.bigdata.udsp.rc.model.RcUserService;
import com.hex.bigdata.udsp.rc.service.RcServiceService;
import com.hex.bigdata.udsp.rc.service.RcUserServiceService;
import com.hex.bigdata.udsp.rts.service.RtsConsumerService;
import com.hex.bigdata.udsp.rts.service.RtsProducerService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 消费的服务
 */
@Service
public class ConsumerService {

    private static Logger logger = LogManager.getLogger(ConsumerService.class);

    private static final FastDateFormat format = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS");

    @Autowired
    private RcUserServiceService rcUserServiceService;
    @Autowired
    private McConsumeLogService mcConsumeLogService;
    @Autowired
    private IqApplicationService iqApplicationService;
    @Autowired
    private ComDatasourceService comDatasourceService;
    @Autowired
    private RtsProducerService rtsProducerService;
    @Autowired
    private RtsConsumerService rtsConsumerService;
    @Autowired
    private RtsSyncService rtsSyncService;
    @Autowired
    private MmSyncService mmSyncService;
    @Autowired
    private MmApplicationService mmApplicationService;
    @Autowired
    private RunQueueService runQueueService;
    @Autowired
    private CurrentService mcCurrentService;
    @Autowired
    private OlqApplicationService olqApplicationService;
    @Autowired
    private WaitQueueService mcWaitQueueService;
    @Autowired
    private IqSyncService iqSyncService;
    @Autowired
    private OlqSyncService olqSyncService;
    @Autowired
    private ImSyncService imSyncService;
    @Autowired
    private LoggingService loggingService;
    @Autowired
    private WaitingService waitingService;
    @Autowired
    private ResponseMapper responseMapper;
    @Autowired
    private RcServiceService rcServiceService;
    @Autowired
    private CurrentService currentService;

    /**
     * 消费前检查
     *
     * @param request
     * @param udspUser
     * @param rcService
     * @return
     */
    public ConsumeRequest checkConsume(Request request, String udspUser, RcService rcService, long bef) {
        ConsumeRequest consumeRequest = new ConsumeRequest();
        consumeRequest.setRequest(request); // 必须先设置request
        // 没有注册服务
        if (rcService == null) {
            consumeRequest.setError(ErrorCode.ERROR_000004);
            return consumeRequest;
        }
        // 服务停用
        if (ServiceStatus.STOP.getValue().equals(rcService.getStatus())) {
            consumeRequest.setError(ErrorCode.ERROR_000017);
            return consumeRequest;
        }
        String type = request.getType();
        String entity = request.getEntity();
        String ip = request.getRequestIp();
        Map<String, String> paraMap = request.getData();
        String consumeId = request.getConsumeId();
        String serviceId = rcService.getPkId();
        String appType = rcService.getType();
        String appId = rcService.getAppId();
        String appName = getAppName(appType, appId);
        request.setAppName(appName);
        request.setAppId(appId);
        request.setAppType(appType);
        RcUserService rcUserService = rcUserServiceService.selectByUserIdAndServiceId(udspUser, serviceId);
        // 没有授权服务
        if (rcUserService == null) {
            consumeRequest.setError(ErrorCode.ERROR_000008);
            return consumeRequest;
        }
        consumeRequest.setRcUserService(rcUserService);
        int maxSyncNum = rcUserService.getMaxSyncNum();
        int maxAsyncNum = rcUserService.getMaxAsyncNum();
        String ipSection = rcUserService.getIpSection();
        // IP段控制
        if (StringUtils.isNotBlank(ipSection)) {
            if (StringUtils.isBlank(ip)) {
                consumeRequest.setError(ErrorCode.ERROR_000006);
                return consumeRequest;
            }
            if (!IpAddressUtil.isIpAddress(ip, ipSection)) {
                consumeRequest.setError(ErrorCode.ERROR_000006);
                return consumeRequest;
            }
        }
        // OLQ_APP
        if (ServiceType.OLQ_APP.getValue().equals(appType)
                && ConsumerEntity.START.getValue().equalsIgnoreCase(entity)) {
            OlqApplicationDto olqApplicationDto = olqApplicationService.selectFullAppInfo(appId);
            try {
                olqApplicationService.checkParam(olqApplicationDto.getParams(), paraMap);
            } catch (Exception e) {
                consumeRequest.setError(ErrorCode.ERROR_000009);
                consumeRequest.setMessage(e.toString());
                return consumeRequest;
            }
            String dsId = olqApplicationDto.getOlqApplication().getOlqDsId();
            String sql = olqApplicationService.getExecuteSQL(olqApplicationDto, paraMap);
            consumeRequest.getRequest().setAppId(dsId);
            consumeRequest.getRequest().setSql(sql);
        }
        // 并发判断
        Current mcCurrent = getCurrent(request, maxSyncNum, maxAsyncNum);
        consumeRequest.setMcCurrent(mcCurrent);
        int maxCurrentNum = ConsumerType.SYNC.getValue().equalsIgnoreCase(type) ? maxSyncNum : maxAsyncNum;
        if (maxCurrentNum == -1) { // 运行队列(同步/异步)没有限制
            currentService.insert(mcCurrent);
        } else { // 运行队列(同步/异步)有限制
            if (!runQueueService.addCurrent(mcCurrent)) { // 运行队列已满
                QueueIsFullResult isFullResult = checkWaitQueueIsFull(mcCurrent, rcUserService);
                consumeRequest.setQueueIsFullResult(isFullResult);
                if (isFullResult == null) { // 未开启等待队列
                    consumeRequest.setError(ErrorCode.ERROR_000018);
                } else if (isFullResult.isWaitQueueIsFull()) { // 等待队列已满
                    consumeRequest.setError(ErrorCode.ERROR_000016);
                } else { // 等待队列开启且未满
                    waitingService.isWaiting(consumeRequest, bef);
                }
            }
        }
        // 重新设置request
        consumeId = (StringUtils.isNotBlank(consumeId) ? consumeId : mcCurrent.getPkId());
        request.setConsumeId(consumeId);
        consumeRequest.setRequest(request);
        return consumeRequest;
    }

    /**
     * 消费
     *
     * @param consumeRequest
     * @param bef
     * @return
     */
    public Response consume(ConsumeRequest consumeRequest, long bef) {
        long runBef = System.currentTimeMillis();
        Response response = new Response();
        ErrorCode errorCode = consumeRequest.getError();
        String message = consumeRequest.getMessage();
        // 错误处理
        // 这个里必须在try finally之前，因为这里处理的错误是运行队列已满的处理，是不需要finally中减去并发的
        if (errorCode != null) {
            loggingService.writeResponseLog(response, consumeRequest, bef, 0,
                    errorCode.getValue(), (StringUtils.isBlank(message) ? errorCode.getName() : message));
            return response;
        }
        // 消费处理
        Request request = consumeRequest.getRequest();
        Current mcCurrent = consumeRequest.getMcCurrent();
        String consumeId = request.getConsumeId();
        String appType = request.getAppType();
        String appId = request.getAppId();
        String type = request.getType();
        String entity = request.getEntity();
        Page page = request.getPage();
        String sql = request.getSql();
        Map<String, String> paraMap = request.getData();
        String udspUser = request.getUdspUser();
        response.setConsumeId(consumeId); // 必须先设置consumeId
        try {
            // ------------------------数据缓存处理【START】-----------------------------
            String isCache = "1";
            String cacheId = null;
            long cacheTime = 60;
            RcService rcService = rcServiceService.selectByAppTypeAndAppId(appType, appId);
            if (rcService != null) {
                isCache = rcService.getIsCache();
                cacheTime = rcService.getTimeout();
                if ("0".equals(isCache) && ConsumerType.SYNC.getValue().equalsIgnoreCase(type)
                        && !ConsumerEntity.STATUS.getValue().equalsIgnoreCase(entity)) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("appType", appType);
                    map.put("appId", appId);
                    map.put("type", type);
                    map.put("entity", entity);
                    map.put("page", page);
                    map.put("sql", sql);
                    map.put("data", paraMap);
                    cacheId = MD5Util.MD5_16(JSONUtil.parseMap2JSON(map));
                    response = responseMapper.select(cacheId);
                    if (response != null) {
                        loggingService.writeResponseLog(request, response, bef, runBef, true); // 写消费信息到数据库
                        return response;
                    }
                }
            }
            // ------------------------数据缓存处理【END】-----------------------------
            // 生成随机的文件名
            String fileName = CreateFileUtil.getFileName();
            // 根据类型进入不同的处理逻辑
            if (ServiceType.IQ.getValue().equalsIgnoreCase(appType)) {
                // 开始iq消费
                if (ConsumerEntity.STATUS.getValue().equalsIgnoreCase(entity)) {
                    logger.debug("execute IQ STATUS");
                    response = status(consumeId);
                } else if (ConsumerType.ASYNC.getValue().equalsIgnoreCase(type)
                        && ConsumerEntity.START.getValue().equalsIgnoreCase(entity)) {
                    logger.debug("execute IQ ASYNC START");
                    ThreadPool.execute(new IqAsyncService(consumeRequest, fileName, bef));
                } else if (ConsumerType.SYNC.getValue().equalsIgnoreCase(type)
                        && ConsumerEntity.START.getValue().equalsIgnoreCase(entity)) {
                    logger.debug("execute IQ SYNC START");
                    response = iqSyncService.syncStartForTimeout(consumeRequest, bef);
                }
            } else if (ServiceType.OLQ.getValue().equalsIgnoreCase(appType)
                    || ServiceType.OLQ_APP.getValue().equals(appType)) {
                // 开始olq、olqApp消费
                if (ConsumerEntity.STATUS.getValue().equalsIgnoreCase(entity)) {
                    logger.debug("execute OLQ STATUS");
                    response = status(consumeId);
                } else if (ConsumerType.ASYNC.getValue().equalsIgnoreCase(type)
                        && ConsumerEntity.START.getValue().equalsIgnoreCase(entity)) {
                    logger.debug("execute OLQ ASYNC START");
                    ThreadPool.execute(new OlqAsyncService(consumeRequest, fileName, bef));
                } else if (ConsumerType.SYNC.getValue().equalsIgnoreCase(type)
                        && ConsumerEntity.START.getValue().equalsIgnoreCase(entity)) {
                    logger.debug("execute OLQ SYNC START");
                    response = olqSyncService.syncStartForTimeout(consumeRequest, bef);
                }
            } else if (ServiceType.MM.getValue().equalsIgnoreCase(appType)) {
                // 开始MM消费
                if (ConsumerEntity.STATUS.getValue().equalsIgnoreCase(entity)) {
                    logger.debug("execute MM STATUS");
                    request.setConsumeId(consumeId); // 设置消费id到request对象，传输给远程的服务
                    response = mmSyncService.status(request);
                } else if (ConsumerEntity.START.getValue().equalsIgnoreCase(entity)) {
                    logger.debug("execute MM SYNC or ASYNC START");
                    response = mmSyncService.start(request);
                }
            } else if (ServiceType.RTS_PRODUCER.getValue().equalsIgnoreCase(appType)) {
                logger.debug("execute RTS_PRODUCER SYNC START");
                response = rtsSyncService.startProducer(appId, request.getDataStream());
            } else if (ServiceType.RTS_CONSUMER.getValue().equalsIgnoreCase(appType)) {
                logger.debug("execute RTS_CONSUMER SYNC START");
                response = rtsSyncService.startConsumer(appId, request.getTimeout());
            } else if (ServiceType.IM.getValue().equalsIgnoreCase(appType)) {
                logger.debug("execute IM SYNC START");
                response = imSyncService.start(appId, paraMap);
            }
            // Response后续处理
            if (ConsumerType.ASYNC.getValue().equalsIgnoreCase(type)
                    && ConsumerEntity.START.getValue().equalsIgnoreCase(entity)
                    && !ServiceType.MM.getValue().equalsIgnoreCase(appType)) {
                if ("admin".equals(udspUser)) { // 防止Linux系统有admin用户
                    udspUser = "udspadmin";
                }
                String ftpFilePath = FTPClientConfig.getRootpath() + "/" + udspUser + "/" + FTPClientConfig.getUsername()
                        + "/" + com.hex.goframe.util.DateUtil.format(new Date(), "yyyyMMdd")
                        + "/" + fileName + CreateFileUtil.DATA_FILE_SUFFIX;
                response.setResponseContent(ftpFilePath);
                response.setStatusCode(StatusCode.SUCCESS.getValue());
                response.setStatus(Status.SUCCESS.getValue());
            } else {
                // -------------------把获取的数据插入缓存【START】---------------------
                if ("0".equals(isCache)
                        && ConsumerType.SYNC.getValue().equalsIgnoreCase(type)
                        && !ConsumerEntity.STATUS.getValue().equalsIgnoreCase(entity)
                        && Status.SUCCESS.getValue().equals(response.getStatus())) {
                    responseMapper.insertTimeout(cacheId, response, cacheTime * 1000);
                }
                // -------------------把获取的数据插入缓存【END】---------------------
                loggingService.writeResponseLog(request, response, bef, runBef, false); // 写消费信息到数据库
            }
            return response;
        } finally {
            // 减少并发
            if (ConsumerType.SYNC.getValue().equalsIgnoreCase(type)
                    || !ConsumerEntity.START.getValue().equalsIgnoreCase(entity)
                    || ServiceType.MM.getValue().equalsIgnoreCase(appType)) {
                if (mcCurrent.getMaxCurrentNum() == -1) { // 运行队列(同步/异步)没有限制
                    currentService.delete(mcCurrent.getPkId());
                } else { // 运行队列(同步/异步)有限制
                    runQueueService.reduceCurrent(mcCurrent);
                }
            }
        }
    }

    public String getAppName(String appType, String appId) {
        String appName = "";
        if (ServiceType.IQ.getValue().equalsIgnoreCase(appType)) {
            appName = iqApplicationService.select(appId).getName();
        } else if (ServiceType.OLQ.getValue().equalsIgnoreCase(appType)) {
            appName = comDatasourceService.select(appId).getName();
        } else if (ServiceType.MM.getValue().equalsIgnoreCase(appType)) {
            appName = mmApplicationService.select(appId).getName();
        } else if (ServiceType.RTS_PRODUCER.getValue().equalsIgnoreCase(appType)) {
            appName = rtsProducerService.select(appId).getName();
        } else if (ServiceType.RTS_CONSUMER.getValue().equalsIgnoreCase(appType)) {
            appName = rtsConsumerService.select(appId).getName();
        } else if (ServiceType.OLQ_APP.getValue().equalsIgnoreCase(appType)) {
            appName = olqApplicationService.select(appId).getName();
        }
        return appName;
    }

    private Response status(String consumeId) {
        Status status = Status.OTHER;
        StatusCode statusCode = StatusCode.OTHER;
        String message = "没有消费";
        Response response = new Response();
        // 检查消费ID是否为空
        if (StringUtils.isBlank(consumeId)) {
            response.setStatus(Status.DEFEAT.getValue());
            response.setStatusCode(StatusCode.DEFEAT.getValue());
            response.setErrorCode(ErrorCode.ERROR_000009.getValue());
            response.setMessage(ErrorCode.ERROR_000009.getName() + ":consumeId不能为空！");
            return response;
        }
        if (mcCurrentService.select(consumeId) != null) { // 正在消费
            status = Status.RUNNING;
            statusCode = StatusCode.RUNNING;
            message = "正在消费";
        } else { // 在写日志或已写完
            // 查询消费日志
            McConsumeLog mcConsumeLog = mcConsumeLogService.select(consumeId);
            if (mcConsumeLog != null) { // 消费完成
                status = Status.SUCCESS;
                statusCode = StatusCode.SUCCESS;
                if ("0".equals(mcConsumeLog.getStatus())) {
                    message = "消费成功";
                    // 输出消费成功的文件位置信息
                    if (StringUtils.isNotBlank(mcConsumeLog.getRequestContent())) {
                        response.setResponseContent(mcConsumeLog.getResponseContent());
                    }
                } else {
                    message = "消费失败";
                    //输出具体失败消息
                    if (StringUtils.isNotBlank(mcConsumeLog.getMessage())) {
                        response.setResponseContent("消费id为‘" + consumeId + "’的异步任务失败原因：" + mcConsumeLog.getMessage());
                    }
                }
            } else { // 正在消费
                status = Status.RUNNING;
                statusCode = StatusCode.RUNNING;
                message = "正在消费";
            }
        }
        response.setStatus(status.getValue());
        response.setStatusCode(statusCode.getValue());
        response.setMessage(message);
        return response;
    }

    public Current getCurrent(Request request, int maxSyncNum, int maxAsyncNum) {
        if (ConsumerType.SYNC.getValue().equalsIgnoreCase(request.getType())) {
            return getMcCurrent(request, maxSyncNum);
        } else {
            return getMcCurrent(request, maxAsyncNum);
        }
    }

    private static Current getMcCurrent(Request request, int maxCurrentNum) {
        Current mcCurrent = new Current();
        String requestContent = JSONUtil.parseObj2JSON(request);
        String consumeId = UUIDUtil.consumeId(requestContent);
        mcCurrent.setStartTime(format.format(new Date()));
        mcCurrent.setServiceName(request.getServiceName());
        mcCurrent.setUserName(request.getUdspUser());
        mcCurrent.setAppType(request.getAppType());
        mcCurrent.setAppName(request.getAppName());
        mcCurrent.setRequestType(request.getRequestType());
        mcCurrent.setPid(consumeId);
        mcCurrent.setAppId(request.getAppId());
        mcCurrent.setSyncType(request.getType().toUpperCase());
        mcCurrent.setPkId(consumeId);
        mcCurrent.setMaxCurrentNum(maxCurrentNum);
        mcCurrent.setRequestContent(requestContent);
        return mcCurrent;
    }

    /**
     * 检检查等待队列是否已满
     *
     * @param mcCurrent
     * @param rcUserService
     * @return
     */
    private QueueIsFullResult checkWaitQueueIsFull(Current mcCurrent, RcUserService rcUserService) {
        String type = mcCurrent.getSyncType();
        int maxSyncWaitNum = rcUserService.getMaxSyncWaitNum();
        int maxAsyncWaitNum = rcUserService.getMaxAsyncWaitNum();
        // 等待队列长度为0，则未开启等待队列
        if ((maxSyncWaitNum == 0 && ConsumerType.SYNC.getValue().equalsIgnoreCase(type))
                || (maxAsyncWaitNum == 0 && ConsumerType.ASYNC.getValue().equalsIgnoreCase(type))) {
            return null;
        }
        // 开启等待队列
        if (ConsumerType.SYNC.getValue().equalsIgnoreCase(type)) {
            return mcWaitQueueService.checkWaitQueueIsFull(mcCurrent, maxSyncWaitNum);
        } else {
            return mcWaitQueueService.checkWaitQueueIsFull(mcCurrent, maxAsyncWaitNum);
        }
    }
}
