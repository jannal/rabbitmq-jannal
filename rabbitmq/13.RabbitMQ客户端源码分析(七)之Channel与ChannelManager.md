[TOC]

<!--20181020-->

# RabbitMQ-java-client版本

1. `com.rabbitmq:amqp-client:4.0.3`
2. `RabbitMQ`版本声明: 3.6.15


# Channel

1. uml图
    
    ![](https://gitee.com/jannal/images/raw/master/RabbitMQ/15400145084705.jpg)
2. `transmit(AMQCommand c)`:传输方法，委托`AMQCommand`进行传输
   
    ```java
        public void transmit(AMQCommand c) throws IOException {
            synchronized (_channelMutex) {
                ensureIsOpen();
                quiescingTransmit(c);
            }
        }
        public void quiescingTransmit(AMQCommand c) throws IOException {
            synchronized (_channelMutex) {
                if (c.getMethod().hasContent()) {
                    while (_blockContent) {
                        try {
                            _channelMutex.wait();
                        } catch (InterruptedException ignored) {}
    
                        // This is to catch a situation when the thread wakes up during
                        // shutdown. Currently, no command that has content is allowed
                        // to send anything in a closing state.
                        ensureIsOpen();
                    }
                }
                //调用AMQCommand的传输方法
                c.transmit(this);
            }
        }
    
    
    ```

# ChannelManager

1.  [ChannelManager](https://gitee.com/jannal/rabbitmq/blob/master/rabbitmq-java-client/src/main/java/com/rabbitmq/client/impl/ChannelManager.java):负责Channel的管理，创建Channel、添加新的Channel、获取Channel、Channel关闭等
2. 构造方法
    ```java
        public ChannelManager(ConsumerWorkService workService, int channelMax, ThreadFactory threadFactory, MetricsCollector metricsCollector) {
            if (channelMax == 0) {
                // The framing encoding only allows for unsigned 16-bit integers
                // for the channel number
                channelMax = (1 << 16) - 1;
            }
            _channelMax = channelMax;
            channelNumberAllocator = new IntAllocator(1, channelMax);
    
            this.workService = workService;
            this.threadFactory = threadFactory;
            this.metricsCollector = metricsCollector;
        }
            
    ```
3. 创建Channel,通过`IntAllocator`分配一个Channel编号(channelNumber),使用Map维护ChannelNumber与Channel的映射关系。
    
    ```java
        //维护ChannelNumber与Channel的映射
        private final Map<Integer, ChannelN> _channelMap = new HashMap<Integer, ChannelN>();
        
        public ChannelN createChannel(AMQConnection connection) throws IOException {
            ChannelN ch;
            synchronized (this.monitor) {
                int channelNumber = channelNumberAllocator.allocate();
                if (channelNumber == -1) {
                    return null;
                } else {
                    ch = addNewChannel(connection, channelNumber);
                }
            }
            ch.open(); // now that it's been safely added
            return ch;
        }    
    
        private ChannelN addNewChannel(AMQConnection connection, int channelNumber) {
                if (_channelMap.containsKey(channelNumber)) {
                    // That number's already allocated! Can't do it
                    // This should never happen unless something has gone
                    // badly wrong with our implementation.
                    throw new IllegalStateException("We have attempted to "
                            + "create a channel with a number that is already in "
                            + "use. This should never happen. "
                            + "Please report this as a bug.");
                }
                ChannelN ch = instantiateChannel(connection, channelNumber, this.workService);
                _channelMap.put(ch.getChannelNumber(), ch);
                return ch;
            }
        protected ChannelN instantiateChannel(AMQConnection connection, int channelNumber, ConsumerWorkService workService) {
            return new ChannelN(connection, channelNumber, workService, this.metricsCollector);
        }
         
    
    ```
4. `handleSignal`:关闭所有被管理的Channel,在关闭Connection时需要关闭所有Channel，就是调用此方法。从源码可以看出，使用异步关闭，主要是为了避免`JDK socket wirte`死锁，BIO下socket write没有写超时。详细参考http://rabbitmq.1065348.n5.nabble.com/Long-timeout-if-server-host-becomes-unreachable-td30275.html
   
    ```java
        public void handleSignal(final ShutdownSignalException signal) {
            Set<ChannelN> channels;
            synchronized(this.monitor) {
                channels = new HashSet<ChannelN>(_channelMap.values());
            }
    
            for (final ChannelN channel : channels) {
                releaseChannelNumber(channel);
                // async shutdown if possible
                // see https://github.com/rabbitmq/rabbitmq-java-client/issues/194
                Runnable channelShutdownRunnable = new Runnable() {
                    @Override
                    public void run() {
                        channel.processShutdownSignal(signal, true, true);
                    }
                };
                if(this.shutdownExecutor == null) {
                    channelShutdownRunnable.run();
                } else {
                    Future<?> channelShutdownTask = this.shutdownExecutor.submit(channelShutdownRunnable);
                    try {
                        channelShutdownTask.get(channelShutdownTimeout, TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        LOGGER.warn("Couldn't properly close channel {} on shutdown after waiting for {} ms", channel.getChannelNumber(), channelShutdownTimeout);
                        channelShutdownTask.cancel(true);
                    }
                }
                shutdownSet.add(channel.getShutdownLatch());
                channel.notifyListeners();
            }
            scheduleShutdownProcessing();
        }
    
    
    ```