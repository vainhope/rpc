package com.rpc.registry;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * @author vain
 * @date 2019/6/16 14:02
 */
public class ServiceRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ServiceRegistry.class);

    private CountDownLatch latch = new CountDownLatch(1);

    private String address;

    private int ZK_SESSION_TIMEOUT = 5000;

    private String REGISTRY_PATH = "/registry";

    private String DATA_PATH = REGISTRY_PATH + "/data";

    public ServiceRegistry(String address, int timeout) {
        this.address = address;
        this.ZK_SESSION_TIMEOUT = timeout;
    }

    public ServiceRegistry(String address) {
        this.address = address;
    }

    public void register(String data) {
        if (!StringUtils.isEmpty(data)) {
            ZooKeeper zooKeeper = this.connectRegister();
            if (null != zooKeeper) {
                this.nodeExist(zooKeeper, REGISTRY_PATH, true);
                this.createNode(zooKeeper, data, DATA_PATH);
            }
        }
    }


    private boolean nodeExist(ZooKeeper zooKeeper, String path, boolean neeCreate) {
        if (null == zooKeeper) {
            return false;
        }
        Stat exists = null;
        try {
            exists = zooKeeper.exists(path, false);
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
        if (null == exists) {
            logger.info("{} path is not exist", path);
            if (neeCreate) {
                try {
                    zooKeeper.create(REGISTRY_PATH, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                } catch (KeeperException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private void createNode(ZooKeeper zooKeeper, String data, String createPath) {
        if (null != zooKeeper && !StringUtils.isEmpty(data) && !StringUtils.isEmpty(createPath)) {
            //持久化初始文件内容
            try {
                String path = zooKeeper.create(createPath, data.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
                logger.debug("create path {} data {}", path, data);
            } catch (KeeperException | InterruptedException e) {
                e.printStackTrace();
                logger.error("", e);
            }
        }
    }

    private ZooKeeper connectRegister() {
        ZooKeeper zooKeeper = null;
        try {
            zooKeeper = new ZooKeeper(address, ZK_SESSION_TIMEOUT, (watchedEvent) -> {
                if (watchedEvent.getState() == Watcher.Event.KeeperState.SyncConnected) {
                    latch.countDown();
                }
            });
            latch.await();
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("", e);
        } catch (InterruptedException e) {
            e.printStackTrace();
            logger.error("connected server timeout", e);
        }
        return zooKeeper;
    }
}
