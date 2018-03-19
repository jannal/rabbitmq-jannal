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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * Bridge between the byte buffer and stream worlds.
 */
public class ByteBufferInputStream extends InputStream {

    private final ReadableByteChannel channel;

    private final ByteBuffer buffer;

    public ByteBufferInputStream(ReadableByteChannel channel, ByteBuffer buffer) {
        this.channel = channel;
        this.buffer = buffer;
    }

    @Override
    public int read() throws IOException {
        readFromNetworkIfNecessary(channel, buffer);
        return readFromBuffer(buffer);
    }

    private int readFromBuffer(ByteBuffer buffer) {
        return buffer.get() & 0xff;
    }

    /**
     * 1. 当socketChannel为阻塞方式时（默认就是阻塞方式）read函数，不会返回0，阻塞方式的socketChannel，若没有数据可读，
     * 或者缓冲区满了，就会阻塞，直到满足读的条件
     * 2. 非阻塞方式
     *      channel.read()返回-1
     *         * 客户端的数据发送完毕，并且主动close socket
     *      channel.read()返回0 三种情况
     *         * 某一个时刻socketChannel中当前没有数据刻度
     *         * ByteBuffer的position等于limit，即ByteBuffer的remaing等于0
     *         * 客户端数据返送完毕，这个时候客户端想获取服务端的反馈调用了recv函数，若服务端继续read
     *
     * 3. 如果客户端发送数据后不关闭channel，同时服务端收到数据后再次发给客户端，此时客户端read方法永远返回0
     * @param channel
     * @param buffer
     * @throws IOException
     */
    private static void readFromNetworkIfNecessary(ReadableByteChannel channel, ByteBuffer buffer) throws IOException {

        if(!buffer.hasRemaining()) {
            buffer.clear();//一旦读完所有数据就需要清空缓冲区，让缓冲区可以被再次写入
            //读取Channel中的数据到buffer(缓冲区)
            int read = NioHelper.read(channel, buffer);
            if(read <= 0) {
                NioHelper.retryRead(channel, buffer);
            }
            buffer.flip();
        }
    }
}
