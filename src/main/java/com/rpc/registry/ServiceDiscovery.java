package com.rpc.registry;

import com.rpc.client.ProxyManager;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

/**
 * @author vain
 * @date 2019/6/16 17:08
 */
public class ServiceDiscovery {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscovery.class);

    private CountDownLatch latch = new CountDownLatch(1);

    private volatile List<String> dataList = new ArrayList<>();

    private String discoveryAddress;

    private ZooKeeper zookeeper;

    private int ZK_SESSION_TIMEOUT = 5000;

    private String REGISTRY_PATH = "/registry";


    public ServiceDiscovery(String discoveryAddress) {
        this.discoveryAddress = discoveryAddress;
        zookeeper = this.connectDiscovery();
        if (null != zookeeper && latch.getCount() == 0) {
            this.watchNode(zookeeper);
        }
    }

    private void watchNode(ZooKeeper zookeeper) {
        try {
            List<String> children = zookeeper.getChildren(REGISTRY_PATH, (event) -> {
                if (Watcher.Event.EventType.NodeChildrenChanged.equals(event.getType())) {
                    watchNode(zookeeper);
                }
            });
            dataList = children.stream().map(node -> {
                try {
                    return new String(zookeeper.getData(REGISTRY_PATH + "/" + node, false, null));
                } catch (KeeperException | InterruptedException e) {
                    e.printStackTrace();
                    logger.error("", e);
                }
                return null;
            }).collect(Collectors.toList());
            logger.debug("discovery server node data has change {}", dataList);
            ProxyManager.getInstance().connectServer(this.dataList);
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
            logger.error("", e);
        }
    }


    public void stop() {
        if (null != zookeeper) {
            try {
                zookeeper.close();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private ZooKeeper connectDiscovery() {
        ZooKeeper zooKeeper = null;
        try {
            zooKeeper = new ZooKeeper(discoveryAddress, ZK_SESSION_TIMEOUT, (watchedEvent) -> {
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
            logger.error("connected discovery server timeout", e);
        }
        return zooKeeper;
    }

}
