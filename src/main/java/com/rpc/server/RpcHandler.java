package com.rpc.server;

import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.sf.cglib.reflect.FastClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @author vain
 * @date 2019/6/16 15:03
 */
public class RpcHandler extends SimpleChannelInboundHandler<RpcRequest> {

    private static final Logger logger = LoggerFactory.getLogger(RpcHandler.class);

    private Map<String, Object> map;

    public RpcHandler(Map<String, Object> map) {
        this.map = map;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("server caught exception", cause);
        ctx.close();
    }


    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, RpcRequest rpcRequest) throws Exception {
        RpcServer.submit(() -> {
            logger.debug("Receive request " + rpcRequest.getRequestId());
            RpcResponse response = new RpcResponse();
            response.setRequestId(rpcRequest.getRequestId());
            try {
                response.setResult(this.handler(rpcRequest));
            } catch (Exception e) {
                e.printStackTrace();
                response.setError(e.getMessage());
            }
            channelHandlerContext.writeAndFlush(response).addListener((channelFuture) -> {
                logger.debug("Send response for request " + rpcRequest.getRequestId());
            });
        });
    }

    private Object handler(RpcRequest rpcRequest) throws Exception {
        String className = rpcRequest.getClassName();
        Object service = map.get(className);
        if (null == service) {
            throw new RuntimeException(" rpc invoke service not exist");
        }
        Class<?> serviceClass = service.getClass();
        String methodName = rpcRequest.getMethodName();
        Class<?>[] parameterTypes = rpcRequest.getParameterTypes();
        Object[] parameters = rpcRequest.getParameters();

        logger.debug(serviceClass.getName());
        logger.debug(methodName);
        for (int i = 0; i < parameterTypes.length; ++i) {
            logger.debug(parameterTypes[i].getName());
        }
        for (int i = 0; i < parameters.length; ++i) {
            logger.debug(parameters[i].toString());
        }

        FastClass proxyClass = FastClass.create(serviceClass);
        int index = proxyClass.getIndex(methodName, parameterTypes);
        return proxyClass.invoke(index, service, parameters);
    }
}
