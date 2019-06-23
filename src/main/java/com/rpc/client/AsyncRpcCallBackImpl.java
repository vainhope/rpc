package com.rpc.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author vain
 * @date 2019/6/23 19:58
 */
public class AsyncRpcCallBackImpl implements IAsyncRpcCallBack {

    private static final Logger logger = LoggerFactory.getLogger(AsyncRpcCallBackImpl.class);

    @Override
    public Object success(Object result) {
        if (result instanceof String) {
            logger.info(result + "------------");
            return result + " change result";
        }
        return null;
    }

    @Override
    public void fail(Exception e) {

    }
}

