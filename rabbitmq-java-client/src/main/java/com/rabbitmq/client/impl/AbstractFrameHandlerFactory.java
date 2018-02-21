package com.rabbitmq.client.impl;

import com.rabbitmq.client.SocketConfigurator;

/**
 *
 */
public abstract class AbstractFrameHandlerFactory implements FrameHandlerFactory {

    //连接超时时间
    protected final int connectionTimeout;
    //socket选项，此SocketConfigurator接口目前只有一个实现类DefaultSocketConfigurator
    protected final SocketConfigurator configurator;
    //是否ssl
    protected final boolean ssl;

    protected AbstractFrameHandlerFactory(int connectionTimeout, SocketConfigurator configurator, boolean ssl) {
        this.connectionTimeout = connectionTimeout;
        this.configurator = configurator;
        this.ssl = ssl;
    }
}
