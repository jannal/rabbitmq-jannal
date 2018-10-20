

[TOC]

<!--20181020-->

# RabbitMQ-java-client版本

1. `com.rabbitmq:amqp-client:4.3.0`
2. `RabbitMQ`版本声明: 3.6.15


# WorkPool

1. [WorkPool](https://gitee.com/jannal/rabbitmq/blob/master/rabbitmq-java-client/src/main/java/com/rabbitmq/client/impl/WorkPool.java)可以认为是一个任务池，保存`client`(在这里实际类型其实就是`Channel`)与具体处理任务的关系
2. 成员变量,`SetQueue`和`VariableLinkedBlockingQueue`在下面详细说明
    
    ```java
        //默认最大队列长度
        private static final int MAX_QUEUE_LENGTH = 1000;
    
        /**就绪的clients集合,SetQueue本质是一个LinkedList+Set*/
        private final SetQueue<K> ready = new SetQueue<K>();
        /** 正在处理的client集合*/
        private final Set<K> inProgress = new HashSet<K>();
        /** 保存注册的Channel与处理的任务队列 */
        private final Map<K, VariableLinkedBlockingQueue<W>> pool = new HashMap<K, VariableLinkedBlockingQueue<W>>();
        /** 保存限制被移除的key的集合，如果不为空，不限制队列大小 */
        private final Set<K> unlimited = new HashSet<K>();
    
    ```
3. `registerKey(K key)`: Channel所对应的任务队列长度取决于是否限制队列长度，如果限制队列长度最大`MAX_QUEUE_LENGTH(1000)`，不限制就是`Integer.MAX_VALUE`
    
    ```java
        public void registerKey(K key) {
            synchronized (this) {
                if (!this.pool.containsKey(key)) {
                    int initialCapacity = unlimited.isEmpty() ? MAX_QUEUE_LENGTH : Integer.MAX_VALUE;
                    this.pool.put(key, new VariableLinkedBlockingQueue<W>(initialCapacity));
                }
            }
        }
    
    ```
    
4. `nextWorkBlock(Collection<W> to, int size)`:返回下一个准备就绪的Channel,并从该Channel对应的任务队列里取出size个任务放在传入的参数Collection中。
    
    ```java
       public K nextWorkBlock(Collection<W> to, int size) {
            synchronized (this) {
                //从就绪队列中取出一个Channel
                K nextKey = readyToInProgress();
                if (nextKey != null) {
                    //获取Channel对应的任务队列
                    VariableLinkedBlockingQueue<W> queue = this.pool.get(nextKey);
                    //连续从queue取中size个元素，并将元素保存到to集合中
                    drainTo(queue, to, size);
                }
                return nextKey;
            }
        }
        private K readyToInProgress() {
            //从SetQueue的队列头获取一个Channel，并从就绪集合转移到处理集合
            K key = this.ready.poll();
            if (key != null) {
                this.inProgress.add(key);
            }
            return key;
        }
        
        private int drainTo(VariableLinkedBlockingQueue<W> deList, Collection<W> c, int maxElements) {
            int n = 0;
            while (n < maxElements) {
                W first = deList.poll();
                if (first == null)
                    break;
                c.add(first);
                ++n;
            }
            return n;
        }
    ```
5. `addWorkItem(K key, W item)`: 为特定的Channel添加新的任务，如果Channel处于休眠状态(不是就绪状态，不是处理状态，不是注册状态)，就将Channel标记为准备就绪状态
    
    ```java
        public boolean addWorkItem(K key, W item) {
            VariableLinkedBlockingQueue<W> queue;
            synchronized (this) {
                queue = this.pool.get(key);
            }
            // The put operation may block. We need to make sure we are not holding the lock while that happens.
            if (queue != null) {
                try {
                    queue.put(item);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
    
                synchronized (this) {
                    //如果Channel处于休眠状态，转换为准备就绪状态
                    if (isDormant(key)) {
                        dormantToReady(key);
                        return true;
                    }
                }
            }
            return false;
        }
    
    
        private boolean isInProgress(K key){ return this.inProgress.contains(key); }
        private boolean isReady(K key){ return this.ready.contains(key); }
        private boolean isRegistered(K key) { return this.pool.containsKey(key); }
        /**
         * 是否休眠状态
         * @param key
         * @return
         */
        private boolean isDormant(K key){ return !isInProgress(key) && !isReady(key) && isRegistered(key); }
    
        //状态流转方法，全部假设key已经注册过
        private void inProgressToReady(K key){ this.inProgress.remove(key); this.ready.addIfNotPresent(key); }
        private void inProgressToDormant(K key){ this.inProgress.remove(key); }
        private void dormantToReady(K key){ this.ready.addIfNotPresent(key); }
        
    ```
6. `finishWorkBlock(K key)`: 设置客户端不是处理状态(`inProgress`)。忽略未知客户端（并返回false）。
    
    ```java
        public boolean finishWorkBlock(K key) {
            synchronized (this) {
                if (!this.isRegistered(key))
                    return false;
                if (!this.inProgress.contains(key)) {
                    throw new IllegalStateException("Client " + key + " not in progress");
                }
    
                if (moreWorkItems(key)) {
                    inProgressToReady(key);
                    return true;
                } else {
                    inProgressToDormant(key);
                    return false;
                }
            }
        }
    
        private boolean moreWorkItems(K key) {
            VariableLinkedBlockingQueue<W> leList = this.pool.get(key);
            return leList != null && !leList.isEmpty();
        }
    
    ```

## VariableLinkedBlockingQueue

1. 这是一个`LinkedBlockingQueue`类的克隆，增加了一个`setCapacity(int)`方法，允许在使用的过程中更改容量

## SetQueue

1. [SetQueue](https://gitee.com/jannal/rabbitmq/blob/master/rabbitmq-java-client/src/main/java/com/rabbitmq/client/impl/SetQueue.java)是一个Set队列，即队列中的元素只能出现一次，本质上队列还是通过`LinkedList`实现，只不过同时通过HashSet判断是否已经有元素。如果有则不再添加元素到队列中。
2. `addIfNotPresent(T item)`: 如果不存在就添加到队列尾部
    
    ```java
        public boolean addIfNotPresent(T item) {
            if (this.members.contains(item)) {
                return false;
            }
            this.members.add(item);
            //添加到linkedList队列的尾部
            this.queue.offer(item);
            return true;
        }
    ```
3. `poll()`:获取并移除队列头部元素
    
     ```java
        public T poll() {
            T item =  this.queue.poll();
            if (item != null) {
                this.members.remove(item);
            }
            return item;
        }
     ```



# ConsumerWorkService

1. [ConsumerWorkService](https://gitee.com/jannal/rabbitmq/blob/master/rabbitmq-java-client/src/main/java/com/rabbitmq/client/impl/ConsumerWorkService.java)主要是用于处理Channel的任务，大部分方法都是委托`WorkPool`来进行处理
2. 成员变量与构造函数
   
    ```java
        private static final int MAX_RUNNABLE_BLOCK_SIZE = 16;
        //默认线程数 对于IO密集的程序来说2倍是不是有点少？？
        private static final int DEFAULT_NUM_THREADS = Runtime.getRuntime().availableProcessors() * 2;
        private final ExecutorService executor;
        private final boolean privateExecutor;
        //Channel与具体任务的关系
        private final WorkPool<Channel, Runnable> workPool;
        private final int shutdownTimeout;
    
        public ConsumerWorkService(ExecutorService executor, ThreadFactory threadFactory, int shutdownTimeout) {
            //如果没有自定义线程就使用默认的线程池
            this.privateExecutor = (executor == null);
            this.executor = (executor == null) ? Executors.newFixedThreadPool(DEFAULT_NUM_THREADS, threadFactory)
                                               : executor;
            this.workPool = new WorkPool<Channel, Runnable>();
            this.shutdownTimeout = shutdownTimeout;
        }
    
    
    
    ```
2. `addWork`:给特定的Channel添加任务
    
    ```java
        public void addWork(Channel channel, Runnable runnable) {
            if (this.workPool.addWorkItem(channel, runnable)) {
                this.executor.execute(new WorkPoolRunnable());
            }
        }
        private final class WorkPoolRunnable implements Runnable {

            @Override
            public void run() {
                //一个线程每次执行16个任务
                int size = MAX_RUNNABLE_BLOCK_SIZE;
                List<Runnable> block = new ArrayList<Runnable>(size);
                try {
                    Channel key = ConsumerWorkService.this.workPool.nextWorkBlock(block, size);
                    if (key == null) return; // nothing ready to run
                    try {
                        for (Runnable runnable : block) {
                            runnable.run();
                        }
                    } finally {
                        //任务执行完成后清理,如果还有任务在队列里，则继续使用线程池执行(递归)
                        if (ConsumerWorkService.this.workPool.finishWorkBlock(key)) {
                            ConsumerWorkService.this.executor.execute(new WorkPoolRunnable());
                        }
                    }
                } catch (RuntimeException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    ```


# ConsumerDispatcher

1. Consumer分发器，每个`Channel`都有一个分发器，分发通知事件发送给内部管理的`WorkPool`和`ConsumerWorkService`