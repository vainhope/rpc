package com.vain.service;

import com.rpc.server.RpcService;

/**
 * @author vain
 * @date 2019/6/16 16:16
 */
@RpcService
public interface HelloService {
    String hello(String name);
}
