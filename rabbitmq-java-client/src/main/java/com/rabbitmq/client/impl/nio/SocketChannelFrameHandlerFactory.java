// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.client.impl.nio;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.AbstractFrameHandlerFactory;
import com.rabbitmq.client.impl.FrameHandler;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
public class SocketChannelFrameHandlerFactory extends AbstractFrameHandlerFactory {

    final NioParams nioParams;

    private final SSLContext sslContext;

    private final Lock stateLock = new ReentrantLock();


    private final AtomicLong globalConnectionCount = new AtomicLong();

    //nio 事件循环上下文
    private final List<NioLoopContext> nioLoopContexts;

    public SocketChannelFrameHandlerFactory(int connectionTimeout, NioParams nioParams, boolean ssl, SSLContext sslContext)
        throws IOException {
        super(connectionTimeout, null, ssl);
        this.nioParams = new NioParams(nioParams);
        this.sslContext = sslContext;
        this.nioLoopContexts = new ArrayList<NioLoopContext>(this.nioParams.getNbIoThreads());
        for (int i = 0; i < this.nioParams.getNbIoThreads(); i++) {
            this.nioLoopContexts.add(new NioLoopContext(this, this.nioParams));
        }
    }

    @Override
    public FrameHandler create(Address addr) throws IOException {
        int portNumber = ConnectionFactory.portOrDefault(addr.getPort(), ssl);

        SSLEngine sslEngine = null;
        SocketChannel channel = null;

        try {
            if (ssl) {
                sslEngine = sslContext.createSSLEngine(addr.getHost(), portNumber);
                sslEngine.setUseClientMode(true);
            }

            SocketAddress address = new InetSocketAddress(addr.getHost(), portNumber);
            channel = SocketChannel.open();
            //设置为阻塞模式
            channel.configureBlocking(true);
            if(nioParams.getSocketChannelConfigurator() != null) {
                nioParams.getSocketChannelConfigurator().configure(channel);
            }

            channel.connect(address);

            if (ssl) {
                sslEngine.beginHandshake();
                boolean handshake = SslEngineHelper.doHandshake(channel, sslEngine);
                if (!handshake) {
                    throw new SSLException("TLS handshake failed");
                }
            }
            //设置通道为非阻塞模式
            channel.configureBlocking(false);

            // lock
            stateLock.lock();
            NioLoopContext nioLoopContext = null;
            try {
                //分配连接到指定的事件循环中
                long modulo = globalConnectionCount.getAndIncrement() % nioParams.getNbIoThreads();
                nioLoopContext = nioLoopContexts.get((int) modulo);
                //启动事件循环
                nioLoopContext.initStateIfNecessary();
                SocketChannelFrameHandlerState state = new SocketChannelFrameHandlerState(
                    channel,
                    nioLoopContext,
                    nioParams,
                    sslEngine
                );
                //注册读事件
                state.startReading();
                SocketChannelFrameHandler frameHandler = new SocketChannelFrameHandler(state);
                return frameHandler;
            } finally {
                stateLock.unlock();
            }


        } catch(IOException e) {
            try {
                if(sslEngine != null && channel != null) {
                    SslEngineHelper.close(channel, sslEngine);
                }
                channel.close();
            } catch(IOException closingException) {
                // ignore
            }
            throw e;
        }

    }

    void lock() {
        stateLock.lock();
    }

    void unlock() {
        stateLock.unlock();
    }
}
