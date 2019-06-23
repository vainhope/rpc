package com.rpc.client;

import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author vain
 * @date 2019/6/16 17:30
 */
public class RpcFuture implements Future<Object> {
    private static final Logger logger = LoggerFactory.getLogger(RpcFuture.class);

    private RpcRequest request;

    private RpcResponse response;

    private long startTime;

    private long responseTimeHold = 5000;

    private List<IAsyncRpcCallBack> callBacks = new ArrayList<>();

    private ReentrantLock lock = new ReentrantLock();

    private Sync sync;

    public RpcFuture(RpcRequest request) {
        this.request = request;
        this.sync = new Sync();
        this.startTime = System.currentTimeMillis();
    }

    public RpcFuture(RpcRequest request, IAsyncRpcCallBack iAsyncRpcCallBack) {
        this.request = request;
        this.sync = new Sync();
        this.startTime = System.currentTimeMillis();
        this.callBacks.add(iAsyncRpcCallBack);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return sync.isDone();
    }

    @Override
    public Object get() throws InterruptedException, ExecutionException {
        sync.acquire(1);
        if (null != this.response) {
            this.done(this.response);
            return this.response.getResult();
        }
        return null;
    }

    @Override
    public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (sync.tryAcquireNanos(-1, unit.toNanos(timeout))) {
            if (null != this.response) {
                this.done(this.response);
                return this.response.getResult();
            }
            return null;
        }
        throw new RuntimeException("Timeout exception. Request id: " + this.request.getRequestId()
                + ". Request class name: " + this.request.getClassName()
                + ". Request method: " + this.request.getMethodName());
    }

    public void done(RpcResponse response) {
        this.response = response;
        sync.release(-1);
        this.invokeCallBacks();
        long timeTTL = System.currentTimeMillis() - startTime;
        logger.debug("handler {} execute time is {}", response.getRequestId(), timeTTL);
        if (timeTTL > responseTimeHold) {
            logger.debug("handler {} execute time out {}", response.getRequestId(), timeTTL);
        }
    }

    private void invokeCallBacks() {
        lock.lock();
        try {
            for (IAsyncRpcCallBack callBack : callBacks) {
                this.RunCallBack(callBack);
            }
        } finally {
            lock.unlock();
        }
    }

    private void RunCallBack(IAsyncRpcCallBack callBack) {
        RpcResponse response = this.response;
        RpcClient.submit(() -> {
            if (!response.isError()) {
                //如果实现了自己的success 后处理方法
                Object success = callBack.success(response.getResult());
                //加工下结果
                if (null != success) {
                    this.response.setResult(success);
                }

            } else {
                callBack.fail(new RuntimeException("Response error", new Throwable(response.getError())));
            }
        });
    }
}

class Sync extends AbstractQueuedSynchronizer {
    private final int DONE = 1;
    private final int PENDING = 0;

    @Override
    protected boolean tryAcquire(int arg) {
        return getState() == DONE;
    }

    @Override
    protected boolean tryRelease(int arg) {
        if (getState() == PENDING) {
            if (compareAndSetState(PENDING, DONE)) {
                return true;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    public boolean isDone() {
        return getState() == DONE;
    }
}
