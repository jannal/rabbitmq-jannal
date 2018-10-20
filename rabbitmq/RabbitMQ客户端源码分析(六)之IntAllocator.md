[TOC]

<!--20181019-->

# RabbitMQ-java-client版本

1. `com.rabbitmq:amqp-client:4.3.0`
2. `RabbitMQ`版本声明: 3.6.15

# IntAllocator

1. 用于分配给定范围的Integer。主要用于产生`channelNumber`。核心是通过`BitSet`来进行Integer的分配与释放。
2. 分配`channelNumber`是在`ChannelManager`构造方法中

    ```java
        channelMax = (1 << 16) - 1;
        channelNumberAllocator = new IntAllocator(1, channelMax);
    ```
3. `IntAllocator`的成员变量与构造函数。传入参数按照上面的(1,channelMax)传入，来分析具体的执行值
    
    ```java
        
        private final int loRange;     
        private final int hiRange; 
        private final int numberOfBits;     
        private int lastIndex = 0;   
        private final BitSet freeSet;
        //创建一个[bottom,top]返回的BitSet，从这个范围分配整数
        public IntAllocator(int bottom, int top) {
            this.loRange = bottom;//1
            this.hiRange = top + 1;//65535+1
            this.numberOfBits = hiRange - loRange;//65535
            this.freeSet = new BitSet(this.numberOfBits);
            //[0,this.numberOfBits)范围设置为true，表示可以分配
            this.freeSet.set(0, this.numberOfBits); //[0,65535)
        }
    ```
4. 分配一个整数
    
    ```java
        public int allocate() {
            //返回上次分配的索引
            int setIndex = this.freeSet.nextSetBit(this.lastIndex); //0
            if (setIndex<0) { // means none found in trailing part
                setIndex = this.freeSet.nextSetBit(0);
            }
            if (setIndex<0) return -1;
            //赋值设置上次分配的索引
            this.lastIndex = setIndex;
            //设置为false表示已经分配，此时（this.lastIndex,this.numberOfBits) 都是true，只有this.lastIndex是false
            this.freeSet.clear(setIndex);
            return setIndex + this.loRange;//0+1
        }
    
    ```
5. 释放`channelNumber`
    
    ```java
    
        channelNumberAllocator.free(channelNumber);
    
        //将BitSet的index设置为true表示已经可以继续利用
        public void free(int reservation) {
            this.freeSet.set(reservation - this.loRange);
        }
    ```