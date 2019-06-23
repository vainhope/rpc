package com.rpc.server;

import com.rpc.protocol.RpcDecoder;
import com.rpc.protocol.RpcEncoder;
import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;
import com.rpc.registry.ServiceRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author vain
 * @date 2019/6/16 14:36
 */
public class RpcServer implements ApplicationContextAware, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);

    private String address;

    private ServiceRegistry serviceRegistry;

    private Map<String, Object> serviceMap = new HashMap<>();

    private EventLoopGroup parentGroup = null;

    private EventLoopGroup childGroup = null;

    private static final int DEFAULT_PORT = 9999;

    private static final String DEFAULT_HOST = "127.0.0.1";

    private static ThreadPoolExecutor threadPoolExecutor;

    public RpcServer(String address) {
        this.address = address;
    }

    public RpcServer(String address, ServiceRegistry serviceRegistry) {
        this.address = address;
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.initGroup();
        this.start();
    }

    private void initGroup() {
        if (null == parentGroup) {
            parentGroup = new NioEventLoopGroup();
        }

        if (null == childGroup) {
            childGroup = new NioEventLoopGroup();
        }

    }

    private void start() {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(parentGroup, childGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        //netty 默认拆包方式
                        socketChannel.pipeline()
                                .addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 0))
                                .addLast(new RpcDecoder(RpcRequest.class))
                                .addLast(new RpcEncoder(RpcResponse.class))
                                .addLast(new RpcHandler(serviceMap));
                    }
                }).option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        String[] split = address.split(":");
        String host = split[0];
        int port = DEFAULT_PORT;
        if (StringUtils.isEmpty(host)) {
            host = DEFAULT_HOST;
        }
        if (split.length > 1 && !StringUtils.isEmpty(split[1])) {
            port = Integer.valueOf(split[1]);
        }
        ChannelFuture sync = null;
        try {
            logger.debug("start server on {} {}", host, port);
            sync = serverBootstrap.bind(host, port).sync();
            if (null != serviceRegistry) {
                serviceRegistry.register(address);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            logger.error("", e);
        } finally {
            if (null != sync) {
                try {
                    sync.channel().closeFuture().sync();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Map<String, Object> beansWithAnnotation = applicationContext.getBeansWithAnnotation(RpcService.class);
        if (!CollectionUtils.isEmpty(beansWithAnnotation)) {
            logger.debug("start server bean register {} ", beansWithAnnotation.keySet());
            beansWithAnnotation.values()
                    .forEach(o -> {
                                //父类的接口
                                Class<?>[] interfaces = o.getClass().getInterfaces();
                                for (Class<?> parentInterface : interfaces) {
                                    serviceMap.put(parentInterface.getName(), o);
                                }
                            }
                    );
        }
    }

    @SuppressWarnings("all")
    public static void submit(Runnable task) {
        if (threadPoolExecutor == null) {
            synchronized (RpcServer.class) {
                if (threadPoolExecutor == null) {
                    threadPoolExecutor = new ThreadPoolExecutor(16, 16, 600L,
                            TimeUnit.SECONDS, new ArrayBlockingQueue<>(65536));
                }
            }
        }
        threadPoolExecutor.submit(task);
    }
}
