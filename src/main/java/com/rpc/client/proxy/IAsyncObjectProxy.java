package com.rpc.client.proxy;

import com.rpc.client.RpcFuture;

/**
 * @author vain
 * @date 2019/6/16 17:26
 */
public interface IAsyncObjectProxy {
    RpcFuture call(String funcName, Object... args);
}
