
[TOC]

## delivery-mode
1. `delivery-mode`设置为1(非持久化),设置为2(持久化)

## 消息确认
1. 如果在发送的时候，将`no-ack`设置为true，服务器就会在消息发送给客户端后自动将其出队。如果由于某些原因连接中断了，或者你的客户端应用程序发生故障了，那么该消息就永远丢失了。
2. 如果在订阅队列时将`no-ack`设置为true的话，那么你处理完消息之后就无需再发送确认消息回服务器(这样就能极大地加快消费者消费的速度)




## Prefetch

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

	










