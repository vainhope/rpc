package com.rpc.client;

/**
 * @author vain
 * @date 2019/6/16 17:32
 */
public interface IAsyncRpcCallBack {
    Object success(Object result);

    void fail(Exception e);
}
