package com.hex.bigdata.udsp.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import com.hex.bigdata.udsp.common.api.model.BaseRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.Charset;

public class SdkHttpUtil {

    /**
     * 日志记录
     */
    private static Logger logger = LogManager.getLogger(SdkHttpUtil.class);

    /**
     * 调用UDSP
     *
     * @param request
     * @param url
     */
    public static <T> T requestUdsp(BaseRequest request, String url, Class<T> clazz) {
        return requestUdsp(request, url, clazz, Charset.forName("UTF-8"));
    }

    /**
     * 调用UDSP
     *
     * @param request
     * @param url
     */
    public static <T> T requestUdsp(BaseRequest request, String url, Class<T> clazz, Charset charset) {
        JSONObject jsonObject = (JSONObject) JSON.toJSON(request);
        String json = jsonObject.toJSONString();
        return HttpUtils.requestPost(url, json, null, clazz, charset, null, null);
    }
}
