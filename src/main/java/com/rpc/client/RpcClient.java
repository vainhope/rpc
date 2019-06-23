package com.rpc.client;

import com.rpc.client.proxy.ObjectProxy;
import com.rpc.registry.ServiceDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author vain
 * @date 2019/6/16 17:22
 */
public class RpcClient implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(RpcClient.class);

    public ServiceDiscovery discovery;

    private List<String> proxyBean;

    public List<String> getProxyBean() {
        return proxyBean;
    }

    public void setProxyBean(List<String> proxyBean) {
        this.proxyBean = proxyBean;
    }

    @SuppressWarnings("all")
    private static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(16, 16,
            600L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(65536));

    public RpcClient(ServiceDiscovery discovery) {
        this.discovery = discovery;
    }

    public static <T> T create(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                new ObjectProxy<T>(interfaceClass)
        );
    }

    public static void submit(Runnable task) {
        threadPoolExecutor.execute(task);
    }

    public void stop() {
        threadPoolExecutor.shutdown();
        discovery.stop();
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        ApplicationContext applicationContext = contextRefreshedEvent.getApplicationContext();
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        for (String beanClassName : proxyBean) {
            //TODO 生成的bean 有依赖关系 需要解决
            logger.debug("start server bean register {} ", beanClassName);
            try {
                //需要代理的类 生成 放入spring中
                Class<?> beanClass = Class.forName(beanClassName);
                if (null != beanClass) {
                    Object proxyInterface = RpcClient.create(beanClass);
                    applicationContext.getAutowireCapableBeanFactory().applyBeanPostProcessorsAfterInitialization(
                            proxyInterface,
                            beanClass.getName());
                    //注册为单例模式
                    beanFactory.registerSingleton(beanClass.getName(), proxyInterface);
                }

            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

        }

    }
}


