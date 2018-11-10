[TOC]

<!--20181028-->

# RabbitMQ-java-client版本

1. `com.rabbitmq:amqp-client:4.0.3`
2. `RabbitMQ`版本声明: 3.6.15


# NioLoopContext

1. 主要用于NIO事件循环的管理，根据配置参数初始化读写Buffer以及启动事件循环。初始化分析参见[RabbitMQ客户端源码分析(二)之Frame与FrameHandler](https://blog.csdn.net/usagoole/article/details/83048009#SocketChannelFrameHandlerFactory_282)
2. 构造方法:根据`NioParams`配置的`readByteBufferSize`和`writeByteBufferSize`生成读写创建读写`Buffer`，默认大小`32768`。
    
    ```java
        public class NioLoopContext {
        
            private static final Logger LOGGER = LoggerFactory.getLogger(NioLoopContext.class);
        
            private final SocketChannelFrameHandlerFactory socketChannelFrameHandlerFactory;
        
            private final ExecutorService executorService;
        
            private final ThreadFactory threadFactory;
        
            final ByteBuffer readBuffer, writeBuffer;
            
            SelectorHolder readSelectorState;
            SelectorHolder writeSelectorState;
        
            public NioLoopContext(SocketChannelFrameHandlerFactory socketChannelFrameHandlerFactory,
                NioParams nioParams) {
                this.socketChannelFrameHandlerFactory = socketChannelFrameHandlerFactory;
                this.executorService = nioParams.getNioExecutor();
                this.threadFactory = nioParams.getThreadFactory();
                this.readBuffer = ByteBuffer.allocate(nioParams.getReadByteBufferSize());
                this.writeBuffer = ByteBuffer.allocate(nioParams.getWriteByteBufferSize());
            }
        
         
          }
    
    
    ```

3. 初始化方法`initStateIfNecessary()`:创建读写`Selector`,启动NIO事件循环
    ```java
       void initStateIfNecessary() throws IOException {
            if (this.readSelectorState == null) {
                //Selector.open() 创建一个Selector
                this.readSelectorState = new SelectorHolder(Selector.open());
                this.writeSelectorState = new SelectorHolder(Selector.open());
    
                startIoLoops();
            }
        }
    
        private void startIoLoops() {
            if (executorService == null) {
                Thread nioThread = Environment.newThread(
                    threadFactory,
                    new NioLoop(socketChannelFrameHandlerFactory.nioParams, this),
                    "rabbitmq-nio"
                );
                nioThread.start();
            } else {
                this.executorService.submit(new NioLoop(socketChannelFrameHandlerFactory.nioParams, this));
            }
        }
    
    
    ```

# NioLoop

1. `NioLoop`的代码写的相对比较晦涩，而且整体代码的逻辑不够清晰。主要就是对读事件和写事件的处理
    ```java
        public class NioLoop implements Runnable {
        
            private static final Logger LOGGER = LoggerFactory.getLogger(NioLoop.class);
        
            private final NioLoopContext context;
        
            private final NioParams nioParams;
        
            public NioLoop(NioParams nioParams, NioLoopContext loopContext) {
                this.nioParams = nioParams;
                this.context = loopContext;
            }
        
            @Override
            public void run() {
                final SelectorHolder selectorState = context.readSelectorState;
                final Selector selector = selectorState.selector;
                final Set<SocketChannelRegistration> registrations = selectorState.registrations;
        
                final ByteBuffer buffer = context.readBuffer;
        
                final SelectorHolder writeSelectorState = context.writeSelectorState;
                final Selector writeSelector = writeSelectorState.selector;
                final Set<SocketChannelRegistration> writeRegistrations = writeSelectorState.registrations;
        
                // whether there have been write registrations in the previous loop
                // registrations are done after Selector.select(), to work on clean keys
                // thus, any write operation is performed in the next loop
                // we don't want to wait in the read Selector.select() if there are
                // pending writes
                boolean writeRegistered = false;
        
                try {
                    while (true && !Thread.currentThread().isInterrupted()) {
        
        
                        /**
                         * selector.selectedKeys();表示获取就绪的SelectionKey(只有当有就绪的Channel才应该调用此方法)
                         * selector.keys() 表示所有的SelectionKey集
                         */
                        for (SelectionKey selectionKey : selector.keys()) {
                            //获取SelectionKey上的附加对象
                            SocketChannelFrameHandlerState state = (SocketChannelFrameHandlerState) selectionKey.attachment();
                            if (state.getConnection() != null && state.getConnection().getHeartbeat() > 0) {
                                long now = System.currentTimeMillis();
                                if ((now - state.getLastActivity()) > state.getConnection().getHeartbeat() * 1000 * 2) {
                                    try {
                                        state.getConnection().handleHeartbeatFailure();
                                    } catch (Exception e) {
                                        LOGGER.warn("Error after heartbeat failure of connection {}", state.getConnection());
                                    } catch (AssertionError e) {
                                        // see https://github.com/rabbitmq/rabbitmq-java-client/issues/237
                                        LOGGER.warn("Assertion error after heartbeat failure of connection {}", state.getConnection());
                                    } finally {
                                        selectionKey.cancel();
                                    }
                                }
                            }
                        }
        
                        int select;
                        if (!writeRegistered && registrations.isEmpty() && writeRegistrations.isEmpty()) {
                            // we can block, registrations will call Selector.wakeup()
                            /**
                             *  当注册的事件到达时，方法返回；返回值表示有多少个channel就绪
                             */
        
                            select = selector.select(1000);
                            if (selector.keys().size() == 0) {
                                // we haven't been doing anything for a while, shutdown state
                                boolean clean = context.cleanUp();
                                if (clean) {
                                    // we stop this thread
                                    return;
                                }
                                // there may be incoming connections, keep going
                            }
                        } else {
                            // we don't have to block, we need to select and clean cancelled keys before registration
                            //此方法不会阻塞，会立即返回
                            select = selector.selectNow();
                        }
        
                        writeRegistered = false;
        
                        // registrations should be done after select,
                        // once the cancelled keys have been actually removed
                        //这里遍历所有就绪的SelectionKey,因为在sendWriteRequet()和startReading()时都注册了关联，或者说后面的wakeUp其实是无效的调用
                        SocketChannelRegistration registration;
                        Iterator<SocketChannelRegistration> registrationIterator = registrations.iterator();
                        while (registrationIterator.hasNext()) {
                            registration = registrationIterator.next();
                            registrationIterator.remove();
                            int operations = registration.operations;
                            //在通道上注册Selector，这有些疑惑？既然在sendWriteRequet()和startReading()唤醒，那之前应该注册了，这又重新注册一遍？？
                            registration.state.getChannel().register(selector, operations, registration.state);
                        }
        
                        if (select > 0) {
                            Set<SelectionKey> readyKeys = selector.selectedKeys();
                            Iterator<SelectionKey> iterator = readyKeys.iterator();
                            while (iterator.hasNext()) {
                                SelectionKey key = iterator.next();
                                // 删除已选的key,以防重复处理
                                iterator.remove();
        
                                if (!key.isValid()) {
                                    continue;
                                }
        
                                //可读事件
                                if (key.isReadable()) {
                                    final SocketChannelFrameHandlerState state = (SocketChannelFrameHandlerState) key.attachment();
        
                                    try {
                                        //如果通道已经关闭，此时取消关联(Channel与Selector的注册关系),取消关联不是立即生效
                                        if (!state.getChannel().isOpen()) {
                                            key.cancel();
                                            continue;
                                        }
                                        if(state.getConnection() == null) {
                                            // we're in AMQConnection#start, between the header sending and the FrameHandler#initialize
                                            // let's wait a bit more
                                            continue;
                                        }
        
                                        DataInputStream inputStream = state.inputStream;
        
                                        //Channel数据读入到Buffer中，这里只读一次，不会做hasRemaining()的判断
                                        state.prepareForReadSequence();
        
                                        while (state.continueReading()) {
                                            Frame frame = Frame.readFrom(inputStream);
        
                                            try {
                                                boolean noProblem = state.getConnection().handleReadFrame(frame);
                                                if (noProblem && (!state.getConnection().isRunning() || state.getConnection().hasBrokerInitiatedShutdown())) {
                                                    // looks like the frame was Close-Ok or Close
                                                    dispatchShutdownToConnection(state);
                                                    key.cancel();
                                                    break;
                                                }
                                            } catch (Throwable ex) {
                                                // problem during frame processing, tell connection, and
                                                // we can stop for this channel
                                                handleIoError(state, ex);
                                                key.cancel();
                                                break;
                                            }
                                        }
        
                                        state.setLastActivity(System.currentTimeMillis());
                                    } catch (final Exception e) {
                                        LOGGER.warn("Error during reading frames", e);
                                        handleIoError(state, e);
                                        key.cancel();
                                    } finally {
                                        buffer.clear();
                                    }
                                }
                            }
                        }
        
                        // write loop
        
                        select = writeSelector.selectNow();
        
                        // registrations should be done after select,
                        // once the cancelled keys have been actually removed
                        SocketChannelRegistration writeRegistration;
                        Iterator<SocketChannelRegistration> writeRegistrationIterator = writeRegistrations.iterator();
                        while (writeRegistrationIterator.hasNext()) {
                            writeRegistration = writeRegistrationIterator.next();
                            writeRegistrationIterator.remove();
                            int operations = writeRegistration.operations;
                            try {
                                if (writeRegistration.state.getChannel().isOpen()) {
                                    writeRegistration.state.getChannel().register(writeSelector, operations, writeRegistration.state);
                                    writeRegistered = true;
                                }
                            } catch (Exception e) {
                                // can happen if the channel has been closed since the operation has been enqueued
                                LOGGER.info("Error while registering socket channel for write: {}", e.getMessage());
                            }
                        }
        
                        if (select > 0) {
                            Set<SelectionKey> readyKeys = writeSelector.selectedKeys();
                            Iterator<SelectionKey> iterator = readyKeys.iterator();
                            while (iterator.hasNext()) {
                                SelectionKey key = iterator.next();
                                iterator.remove();
                                SocketChannelFrameHandlerState state = (SocketChannelFrameHandlerState) key.attachment();
        
                                if (!key.isValid()) {
                                    continue;
                                }
        
                                if (key.isWritable()) {
                                    boolean cancelKey = true;
                                    try {
                                        if (!state.getChannel().isOpen()) {
                                            key.cancel();
                                            continue;
                                        }
        
                                        state.prepareForWriteSequence();
        
                                        int toBeWritten = state.getWriteQueue().size();
                                        int written = 0;
        
                                        DataOutputStream outputStream = state.outputStream;
        
                                        WriteRequest request;
                                        while (written <= toBeWritten && (request = state.getWriteQueue().poll()) != null) {
                                            request.handle(outputStream);
                                            written++;
                                        }
                                        outputStream.flush();
                                        //如果有新的写请求入队，此时取消注册
                                        if (!state.getWriteQueue().isEmpty()) {
                                            cancelKey = true;
                                        }
                                    } catch (Exception e) {
                                        handleIoError(state, e);
                                    } finally {
                                        //清空写buffer
                                        state.endWriteSequence();
                                        if (cancelKey) {
                                            key.cancel();
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error in NIO loop", e);
                }
            }
        ... 省略
        }
    ```

# SelectorHolder

1. 此类在`SocketChannelFrameHandlerState`中调用，主要用于唤醒读写事件,核心方法如下，`operations`表示` SelectionKey.OP_WRITE`或者`SelectionKey.OP_READ`
    ```java
         public void registerFrameHandlerState(SocketChannelFrameHandlerState state, int operations) {
                registrations.add(new SocketChannelRegistration(state, operations));
                //唤醒阻塞在selector.select()或者selector.select(timeout)上的线程
                selector.wakeup();
            }
    
    ```

# SocketChannelFrameHandlerState

1. 主要负责注册读事件和写事件，写操作写入队列，一旦入队列成功就唤醒写事件，`NioLoop`事件循环从队列中获取数据并写入到写缓冲区中
    
    ```java
    
        private void sendWriteRequest(WriteRequest writeRequest) throws IOException {
            try {
                //将写操作入队列，offer当队列满了，返回false不会阻塞
                boolean offered = this.writeQueue.offer(writeRequest, writeEnqueuingTimeoutInMs, TimeUnit.MILLISECONDS);
                if(offered) {
                    this.writeSelectorState.registerFrameHandlerState(this, SelectionKey.OP_WRITE);
                    //注册了写事件，让写事件优先级变高，此时通过wakeup()唤醒
                    this.readSelectorState.selector.wakeup();
                } else {
                    throw new IOException("Frame enqueuing failed");
                }
            } catch (InterruptedException e) {
                LOGGER.warn("Thread interrupted during enqueuing frame in write queue");
            }
        }
    
        public void startReading() {
            this.readSelectorState.registerFrameHandlerState(this, SelectionKey.OP_READ);
        }
    ```