[TOC]

<!--20181014-->

# 版本声明

1. `com.rabbitmq:amqp-client:4.3.0`
2. `RabbitMQ`版本声明: 3.6.15

# Frame(帧)分析


## AMQP帧(Frame)的格式

1. tcp/ip是一个流协议，amqp没有采用在流中添加帧定界符来区分帧数据（原因官方认为简单，但是很慢），而是采用在`header`中写入帧大小来区分不同的帧（官方认为简单而且很快）。
2. `Frame(帧)`就是传输协议的具体实现,即传输的具体数据包
	* 帧类型 通道编号 帧大小 内容 结束标记
	![](https://gitee.com/jannal/images/raw/master/RabbitMQ/15185928873671.jpg)
3. 从上面可以看到读取一个`Frame`，大概要实现的逻辑
    * 第一步:读取`header`，检查Frame的类型(1,2,3,8四种类型,[AMQP.java](https://gitee.com/jannal/rabbitmq/blob/master/rabbitmq-java-client/src/main/java/com/rabbitmq/client/AMQP.java)类中定义)和`Channel`编号
    * 第二步:根据帧的类型(`type`)和`payload`的长度(size)来读取指定大小的`payload`进行处理
    * 第三步:读取最后一个字节(结束帧)。
    * 从上面的三步骤来看其实就是在做自定义协议解析的工作，不只是针对AMQP，其实自己定义的协议很多时候也是这么做的。
4.  AMQP协议定义了五种类型的帧:`协议头帧`、`method frame(方法帧)`、`content header frame(内容头帧)`、`content body(消息体帧)`、`HEARTBEAT(心跳帧)`
    
5. `方法帧`:一个`Method Frame(方法帧)`携带一个命令，方法帧payload有下面的格式 
![](https://gitee.com/jannal/images/raw/master/RabbitMQ/15191777236647.jpg)
6. `Content Header`的payload格式如下:
![](https://gitee.com/jannal/images/raw/master/RabbitMQ/15395016972182.jpg)

3. `Content Body Frame`:内容是我们通常AMQP服务器在客户端与客户端之间传送和应用数据,内容头帧包含一条消息的大小和属性
![](https://gitee.com/jannal/images/raw/master/RabbitMQ/15395008271652.jpg)


## Frame源码分析

0. 详细的中文注释请访问[Frame.java](https://gitee.com/jannal/rabbitmq/blob/master/rabbitmq-java-client/src/main/java/com/rabbitmq/client/impl/Frame.java)
1. Frame的属性
    
    ```java
    public class Frame {
        /** Frame type code */
        public final int type;
    
        /** Frame channel number, 0-65535 通道编号，一个TCPIP连接，多个通道，每个通道都有自己的编号*/
        public final int channel;
    
        /** Frame payload bytes (for inbound frames) 有效载荷,就是除了协议头之外的内容*/
        private final byte[] payload;
    
        /** Frame payload (for outbound frames) 字节流的累加器，用于*/
        private final ByteArrayOutputStream accumulator;
    
        /**
         * 自定义协议都是 【协议头】+【协议体】,NON_BODY_SIZE指的是没有协议体内容的情况下最小的传输
         * 长度。从这里可以看出【协议头】=type(1B)+channel(2B)+payloadSize(4B)+1(1B),总共占用8B(8个字节)
         */
        private static final int NON_BODY_SIZE = 1 /* type */ + 2 /* channel */ + 4 /* payload size */ + 1 /* end character */;
            
    }

    ```
2. 读取Frame
    
    ```java
    public static Frame readFrom(DataInputStream is) throws IOException {
        int type;
        int channel;

        try {
            //读取1B
            type = is.readUnsignedByte();
        } catch (SocketTimeoutException ste) {
            // System.err.println("Timed out waiting for a frame.");
            return null; // failed
        }

        if (type == 'A') {
            /*
             * Probably an AMQP.... header indicating a version
             * mismatch.
             */
            /*
             * Otherwise meaningless, so try to read the version,
             * and throw an exception, whether we read the version
             * okay or not.检查协议版本
             */
            protocolVersionMismatch(is);
        }
        //读取2B
        channel = is.readUnsignedShort();
        //读取4B
        int payloadSize = is.readInt();
        //构造存放内容的字节数组
        byte[] payload = new byte[payloadSize];
        /**
         *  readFully数据缓冲区的空间还有剩余时会阻塞等待读取，直到装满。
         *  此处不能使用is.read(payload),
         *  read(byte[] b)一直阻塞等待读取字节，直到字节流中的数据已经全部读完。
         *  而readFully(byte[] b)是当数据缓冲区的空间还有剩余时会阻塞等待读取，直到装满。
         */

        is.readFully(payload);

        int frameEndMarker = is.readUnsignedByte();
        //如果读取完body之后最后一个字节不是结束帧，就代表数据格式不正确，以此来判断Frame的传输的正确性
        if (frameEndMarker != AMQP.FRAME_END) {
            throw new MalformedFrameException("Bad frame end marker: " + frameEndMarker);
        }

        return new Frame(type, channel, payload);
    }
    ```

## FrameHandler

1. UML图
![](https://gitee.com/jannal/images/raw/master/RabbitMQ/15186004500844.jpg)
2. rabbitmq-java-client(4.0.3)版本提供了两种处理模式，`NIO`和`BIO`

### SocketFrameHandler

1. BIO模式,代码详细分析参考[SocketFrameHandler](https://gitee.com/jannal/rabbitmq/blob/master/rabbitmq-java-client/src/main/java/com/rabbitmq/client/impl/SocketFrameHandler.java)

### SocketChannelFrameHandler

1. NIO模式，[SocketChannelFrameHandler](https://gitee.com/jannal/rabbitmq/blob/master/rabbitmq-java-client/src/main/java/com/rabbitmq/client/impl/nio/SocketChannelFrameHandler.java)
2. `SocketChannelFrameHandler`委托`SocketChannelFrameHandlerState`进行读写以及Socket选项设置
3. [`SocketChannelFrameHandlerState`](https://gitee.com/jannal/rabbitmq/blob/master/rabbitmq-java-client/src/main/java/com/rabbitmq/client/impl/nio/SocketChannelFrameHandlerState.java),解析
    ```java
         public SocketChannelFrameHandlerState(SocketChannel channel, NioLoopContext nioLoopsState, NioParams nioParams, SSLEngine sslEngine) {
                this.channel = channel;
                //读Selector
                this.readSelectorState = nioLoopsState.readSelectorState;
                //写Selector
                this.writeSelectorState = nioLoopsState.writeSelectorState;
                //写操作存入阻塞队列，数组形式保存数据
                this.writeQueue = new ArrayBlockingQueue<WriteRequest>(nioParams.getWriteQueueCapacity(), true);
                this.writeEnqueuingTimeoutInMs = nioParams.getWriteEnqueuingTimeoutInMs();
                this.sslEngine = sslEngine;
                if(this.sslEngine == null) {
                    this.ssl = false;
                    this.plainOut = nioLoopsState.writeBuffer;
                    this.cipherOut = null;
                    this.plainIn = nioLoopsState.readBuffer;
                    this.cipherIn = null;
        
                    this.outputStream = new DataOutputStream(
                        new ByteBufferOutputStream(channel, plainOut)
                    );
                    this.inputStream = new DataInputStream(
                        new ByteBufferInputStream(channel, plainIn)
                    );
        
                } else {
                    this.ssl = true;
                    this.plainOut = ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize());
                    this.cipherOut = ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize());
                    this.plainIn = ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize());
                    this.cipherIn = ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize());
        
                    this.outputStream = new DataOutputStream(
                        new SslEngineByteBufferOutputStream(sslEngine, plainOut, cipherOut, channel)
                    );
                    this.inputStream = new DataInputStream(
                        new SslEngineByteBufferInputStream(sslEngine, plainIn, cipherIn, channel)
                    );
                }
        
            }
    
    ```

## FramehandlerFactory

1. uml图
![](https://gitee.com/jannal/images/raw/master/RabbitMQ/15191950844687.jpg)

2. 接口方法
   
    ```java
    public interface FrameHandlerFactory {
    
        FrameHandler create(Address addr) throws IOException;
    
    }
    ```

### AbstractFrameHandlerFactory

1. [AbstractFrameHandlerFactory](https://gitee.com/jannal/rabbitmq/blob/master/rabbitmq-java-client/src/main/java/com/rabbitmq/client/impl/AbstractFrameHandlerFactory.java)源码
    
    ```java
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
   ```
2. [DefaultSocketConfigurator](https://gitee.com/jannal/rabbitmq/blob/master/rabbitmq-java-client/src/main/java/com/rabbitmq/client/DefaultSocketConfigurator.java)源码
    
    ```java
        public class DefaultSocketConfigurator implements SocketConfigurator {
        @Override
        public void configure(Socket socket) throws IOException {
            // disable Nagle's algorithm, for more consistently low latency
            /**
             * 禁用Nagle算法，默认这里只是设置了一个socket选项，我们完全可以自己定义自己想要设置的socket选项
             * 传输给ConnectionFactory。比如我们可以设置SO_LINGER选项来进行延迟关闭连接.事实上在SocketFrameHandler类
             * 中close方法就设置了setSoLinger();
             */
    
    
            socket.setTcpNoDelay(true);
        }
    }

    ```
    
### SocketFrameHandlerFactory

1.  `SocketFrameHandlerFactory`用于创建`SocketFrameHandler`,[SocketFrameHandlerFactory](https://gitee.com/jannal/rabbitmq/blob/master/rabbitmq-java-client/src/main/java/com/rabbitmq/client/impl/SocketFrameHandlerFactory.java)源码
    
    ```java
        public class SocketFrameHandlerFactory extends AbstractFrameHandlerFactory {
    
        private final SocketFactory factory;
        //用于关闭socket的线程池
        private final ExecutorService shutdownExecutor;
    
        public SocketFrameHandlerFactory(int connectionTimeout, SocketFactory factory, SocketConfigurator configurator, boolean ssl) {
            this(connectionTimeout, factory, configurator, ssl, null);
        }
    
        public SocketFrameHandlerFactory(int connectionTimeout, SocketFactory factory, SocketConfigurator configurator, boolean ssl, ExecutorService shutdownExecutor) {
            super(connectionTimeout, configurator, ssl);
            this.factory = factory;
            this.shutdownExecutor = shutdownExecutor;
        }
    
        public FrameHandler create(Address addr) throws IOException {
            String hostName = addr.getHost();
            int portNumber = ConnectionFactory.portOrDefault(addr.getPort(), ssl);
            Socket socket = null;
            try {
                //通过socketFactory创建Socket
                socket = factory.createSocket();
                //设置socket选项
                configurator.configure(socket);
                //开启连接
                socket.connect(new InetSocketAddress(hostName, portNumber),
                        connectionTimeout);
                return create(socket);
            } catch (IOException ioe) {
                quietTrySocketClose(socket);
                throw ioe;
            }
        }
    
        public FrameHandler create(Socket sock) throws IOException
        {
            return new SocketFrameHandler(sock, this.shutdownExecutor);
        }
    
        private static void quietTrySocketClose(Socket socket) {
            if (socket != null)
                try { socket.close(); } catch (Exception _e) {/*ignore exceptions*/}
        }
    }

    ```   
    
### SocketChannelFrameHandlerFactory

1. `SocketChannelFrameHandlerFactory`用于创建`SocketChannelFrameHandler` 并且分负责分配连接到指定的事件循环中,[SocketChannelFrameHandlerFactory](https://gitee.com/jannal/rabbitmq/blob/master/rabbitmq-java-client/src/main/java/com/rabbitmq/client/impl/SocketChannelFrameHandlerFactory.java)源码解析
    
    ```java
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
    
    ```
    
