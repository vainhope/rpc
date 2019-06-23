package com.rpc.client.proxy;

import com.rpc.client.ProxyManager;
import com.rpc.client.RpcClientHandler;
import com.rpc.client.RpcFuture;
import com.rpc.protocol.RpcRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * @author vain
 * @date 2019/6/16 17:26
 */
public class ObjectProxy<T> implements InvocationHandler, IAsyncObjectProxy {

    private static final Logger logger = LoggerFactory.getLogger(ObjectProxy.class);

    private Class<T> clazz;

    public ObjectProxy(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (Object.class == method.getDeclaringClass()) {
            String name = method.getName();
            if ("equals".equals(name)) {
                return proxy == args[0];
            } else if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            } else if ("toString".equals(name)) {
                return proxy.getClass().getName() + "@" +
                        Integer.toHexString(System.identityHashCode(proxy)) +
                        ", with InvocationHandler " + this;
            } else {
                throw new IllegalStateException(String.valueOf(method));
            }
        }
        for (int i = 0; i < method.getParameterTypes().length; ++i) {
            logger.debug(method.getParameterTypes()[i].getName());
        }
        RpcRequest request = this.getRequest(method.getDeclaringClass().getName(), method.getName(), args);

        RpcClientHandler handler = ProxyManager.getInstance().getHandler();
        return handler.sendRequest(request).get();
    }

    private RpcRequest getRequest(String className, String method, Object[] args) {
        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setClassName(className);
        request.setMethodName(method);
        Class[] parameterTypes = new Class[args.length];
        request.setParameters(args);
        logger.debug(" init request class {} method {}", className, method);
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = getClassType(args[i]);
            logger.debug(args[i].toString());
        }
        request.setParameterTypes(parameterTypes);
        return request;
    }

    @Override
    public RpcFuture call(String funcName, Object... args) {
        RpcClientHandler handler = ProxyManager.getInstance().getHandler();
        return handler.sendRequest(this.getRequest(this.clazz.getName(), funcName, args));
    }

    private Class<?> getClassType(Object obj) {
        Class<?> classType = obj.getClass();
        String typeName = classType.getName();
        switch (typeName) {
            case "java.lang.Integer":
                return Integer.TYPE;
            case "java.lang.Long":
                return Long.TYPE;
            case "java.lang.Float":
                return Float.TYPE;
            case "java.lang.Double":
                return Double.TYPE;
            case "java.lang.Character":
                return Character.TYPE;
            case "java.lang.Boolean":
                return Boolean.TYPE;
            case "java.lang.Short":
                return Short.TYPE;
            case "java.lang.Byte":
                return Byte.TYPE;
            default:
                return classType;
        }
    }

}
