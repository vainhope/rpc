package com.rpc.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * @author vain
 * @date 2019/6/16 14:58
 */
public class RpcDecoder extends ByteToMessageDecoder {

    private Class<?> genericClass;

    public RpcDecoder(Class<?> genericClass) {
        this.genericClass = genericClass;
    }

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {
        if (byteBuf.readableBytes() >= 4) {
            byteBuf.markReaderIndex();
            int length = byteBuf.readInt();
            if (byteBuf.readableBytes() < length) {
                byteBuf.resetReaderIndex();
            } else {
                byte[] data = new byte[length];
                byteBuf.readBytes(data);
                list.add(SerializationUtils.deserialize(data, genericClass));
            }
        }
    }
}
