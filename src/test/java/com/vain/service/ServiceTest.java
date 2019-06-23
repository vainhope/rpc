package com.vain.service;

import com.rpc.server.RpcService;
import org.apache.zookeeper.ZooKeeper;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @author vain
 * @date 2019/6/16 21:21
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:client-spring.xml")
public class ServiceTest {

    @Autowired
    private HelloService helloService;

    @Test
    public void helloTest1() {
        String result = helloService.hello("World");
        Assert.assertEquals("Hello! World", result);
    }

    @Test
    public void readData() {
        try {
            ZooKeeper zooKeeper = new ZooKeeper("127.0.0.1:2181", 50000, null);
            if (null != zooKeeper) {
                List<String> children = zooKeeper.getChildren("/registry", null);
                for (String child : children) {
                    System.out.println(child);
                    if (!StringUtils.isEmpty(child)) {
                        byte[] data = zooKeeper.getData("/registry/" + child, null, null
                        );
                        System.out.println(new String(data));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

