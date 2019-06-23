package com.vain.service;

import com.rpc.server.RpcService;

@RpcService
public class HelloServiceImpl implements HelloService {

    public HelloServiceImpl() {

    }

    @Override
    public String hello(String name) {
        return "Hello! " + name;
    }
}
