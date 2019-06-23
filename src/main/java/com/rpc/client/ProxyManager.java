package com.rpc.client;

import com.rpc.protocol.RpcDecoder;
import com.rpc.protocol.RpcEncoder;
import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * @author vain
 * @date 2019/6/16 20:04
 */
public class ProxyManager {
    private static final Logger logger = LoggerFactory.getLogger(ProxyManager.class);

    private volatile static ProxyManager proxyManager = null;

    private EventLoopGroup group = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors());

    @SuppressWarnings("all")
    private static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(16, 16,
            600L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(65536));

    /**
     * 复用netty channel
     */
    private CopyOnWriteArrayList<RpcClientHandler> handlers = new CopyOnWriteArrayList<>();

    private Map<InetSocketAddress, RpcClientHandler> serverNodes = new ConcurrentHashMap<>();

    private ReentrantLock lock = new ReentrantLock();

    private Condition connected = lock.newCondition();

    private long timeoutMillis = 6000;

    private volatile boolean isRunning = true;

    private AtomicInteger roundRobin = new AtomicInteger(0);

    private ProxyManager() {
    }

    public static ProxyManager getInstance() {
        if (null == proxyManager) {
            synchronized (ProxyManager.class) {
                if (null == proxyManager) {
                    proxyManager = new ProxyManager();
                }
            }
        }
        return proxyManager;
    }

    public void connectServer(List<String> addServerAddress) {
        if (!CollectionUtils.isEmpty(addServerAddress)) {
            List<InetSocketAddress> socketAddresses = addServerAddress.stream().map(address -> {
                String[] split = address.split(":");
                if (split.length > 1) {
                    String host = split[0];
                    int port = Integer.valueOf(split[1]);
                    return new InetSocketAddress(host, port);
                }
                return null;
            }).collect(Collectors.toList());

            for (InetSocketAddress socketAddress : socketAddresses) {
                this.initSocketConnect(socketAddress);
            }

            //更新服务地址中 移除之前失效的
            for (RpcClientHandler handler : handlers) {
                if (!socketAddresses.contains(handler.getRemote())) {
                    handler.close();
                    serverNodes.remove(handler.getRemote());
                }
            }
        } else {
            for (RpcClientHandler handler : handlers) {
                handler.close();
                serverNodes.remove(handler.getRemote());
            }
        }
    }

    private void initSocketConnect(InetSocketAddress socketAddress) {
        threadPoolExecutor.execute(() -> {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            socketChannel.pipeline().addLast(new RpcEncoder(RpcRequest.class));
                            socketChannel.pipeline().addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 0));
                            socketChannel.pipeline().addLast(new RpcDecoder(RpcResponse.class));
                            socketChannel.pipeline().addLast(new RpcClientHandler());
                        }
                    });

            ChannelFuture channelFuture = bootstrap.connect(socketAddress);
            channelFuture.addListener((future) -> {
                if (future.isSuccess()) {
                    logger.debug("Successfully connect to remote server. remote peer = " + socketAddress.getHostString());
                    this.addHandler(channelFuture.channel().pipeline().get(RpcClientHandler.class));
                }
            });

        });
    }

    private void addHandler(RpcClientHandler rpcClientHandler) {
        handlers.add(rpcClientHandler);
        InetSocketAddress remoteAddress = (InetSocketAddress) rpcClientHandler.getChannel().remoteAddress();
        serverNodes.put(remoteAddress, rpcClientHandler);
        this.signalAvailableHandler();
    }

    public RpcClientHandler getHandler() {
        int size = handlers.size();
        while (isRunning && size <= 0) {
            lock.lock();
            try {
                if (connected.await(this.timeoutMillis, TimeUnit.MILLISECONDS)) {
                    size = handlers.size();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
        int index = (roundRobin.getAndAdd(1) + size) % size;
        return handlers.get(index);
    }

    private void signalAvailableHandler() {
        lock.lock();
        try {
            connected.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        isRunning = false;
        for (RpcClientHandler handler : handlers) {
            handler.close();
        }
        this.signalAvailableHandler();
        threadPoolExecutor.shutdown();
        group.shutdownGracefully();
    }

}
