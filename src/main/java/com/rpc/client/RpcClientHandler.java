package com.rpc.client;

import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;

import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * @author vain
 * @date 2019/6/16 20:07
 */
public class RpcClientHandler extends SimpleChannelInboundHandler<RpcResponse> {

    private volatile Channel channel;

    private SocketAddress socketAddress;

    private ConcurrentHashMap<String, RpcFuture> pendingRpc = new ConcurrentHashMap<>();

    public Channel getChannel() {
        return channel;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, RpcResponse response) throws Exception {
        String requestId = response.getRequestId();
        RpcFuture rpcFuture = pendingRpc.get(requestId);
        if (null != rpcFuture) {
            pendingRpc.remove(requestId);
            rpcFuture.done(response);
        }
    }

    public void close() {
        channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        this.channel = ctx.channel();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.socketAddress = this.channel.remoteAddress();
    }

    public SocketAddress getRemote() {
        return this.socketAddress;
    }

    public RpcFuture sendRequest(RpcRequest request) {
        final CountDownLatch latch = new CountDownLatch(1);
        RpcFuture rpcFuture = new RpcFuture(request, new AsyncRpcCallBackImpl());
        pendingRpc.put(request.getRequestId(), rpcFuture);
        channel.writeAndFlush(request).addListener((future -> latch.countDown()));
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return rpcFuture;
    }
}
