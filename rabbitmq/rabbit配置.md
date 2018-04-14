# 参数

## delivery-mode
1. `delivery-mode`设置为1(非持久化),设置为2(持久化)

## 消息确认
1. 如果在发送的时候，将`no-ack`设置为true，服务器就会在消息发送给客户端后自动将其出队。如果由于某些原因连接中断了，或者你的客户端应用程序发生故障了，那么该消息就永远丢失了。
2. 如果在订阅队列时将`no-ack`设置为true的话，那么你处理完消息之后就无需再发送确认消息回服务器(这样就能极大地加快消费者消费的速度)

## 投递消息
1. 如果发布的消息中`mandatory`和`immediate`标记设置为false的话，那么这个过程会以异步的方式执行，并且从客户端的角度来看，服务器会变得更快。




## Prefetch
1. 实际使用RabbitMQ过程中，如果完全不配置QoS，这样Rabbit会尽可能快速地发送队列中的所有消息到client端。因为consumer在本地缓存所有的message，从而极有可能导致OOM或者导致服务器内存不足影响其它进程的正常运行。所以我们需要通过设置Qos的`prefetch count`来控制consumer的流量。同时设置得当也会提高consumer的吞吐量。
2. prefetch允许为每个consumer指定最大的`unacked messages`数目。简单来说就是用来指定一个consumer一次可以从Rabbit中获取多少条message并缓存在client中(RabbitMQ提供的各种语言的client library)。一旦缓冲区满了，Rabbit将会停止投递新的message到该consumer中直到它发出ack
3. 假设prefetch值设为10，共有两个consumer。意味着每个consumer每次会从queue中预抓取 10 条消息到本地缓存着等待消费。同时该channel的unacked数变为20。而Rabbit投递的顺序是，先为consumer1投递满10个message，再往consumer2投递10个message。如果这时有新message需要投递，先判断channel的unacked数是否等于20，如果是则不会将消息投递到consumer中，message继续呆在queue中。之后其中consumer对一条消息进行ack，unacked此时等于19，Rabbit就判断哪个consumer的unacked少于10，就投递到哪个consumer中。
4. 总的来说，consumer负责不断处理消息，不断ack，然后只要unacked数少于prefetch * consumer数目，broker就不断将消息投递过去。
5. spring-amqp配置

```java
  spring-amqp中的prefetch默认值是1。
  <rabbit:listener-container connection-factory="connectionFactory" acknowledge="auto"
                               max-concurrency="1000" concurrency="100"
                               message-converter="messageConverter" prefetch="1"    >
        <rabbit:listener ref="commonMessageListener"  queue-names="jannal.queue"   />

    </rabbit:listener-container>

```
6. prefetch的设置与以下几点有关：
	* 客户端服务端之间网络传输时间
	* consumer消耗一条消息所执行的业务逻辑的耗时
	* 网络状况
	* 参考官方说明 https://www.rabbitmq.com/blog/2012/05/11/some-queuing-theory-throughput-latency-and-bandwidth/


