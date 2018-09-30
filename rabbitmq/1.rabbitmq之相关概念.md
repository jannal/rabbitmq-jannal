[TOC]

<!-- v20180923 -->

# RabbitMQ版本

1. 版本声明: 3.6.15

# RabbitMQ相关概念
1. `RabbitMQ`是一个`Erlang`开发的`AMQP（Advanced Message Queuing Protocol,高级消息队列协议）`的开源实现，它最初起源于金融系统
2. 特点
	* 可靠性: `RabbitMQ`使用如持久化、传输确认、发布确认等机制来保证可靠性
	* 扩展性:多个`RabbitMQ`节点可以组成一个集群，可以动态扩展集群中的节点
	* 高可用性:队列可以在集群中的机器上设置镜像，即使部分节点出现问题队列仍然可用
	* 多种协议:`AMQP协议`、`STOMP`、`MOTT`等多种消息中间件协议
	* 多语言客户端:`java`、`Python`、`Ruby`、`PHP`、`C#`、`JavaScript`、`Go`、`Object-C`等
	* 管理界面:查看、监控、管理消息、集群中节点状态的用户界面
	* 第三方插件丰富
3. `AMQP`规范不仅定义了一种网络协议，同时也定义了服务端的服务与行为——`AMQP模型`，`AMQP模型`在逻辑上定义了三种抽象组件用于指定消息的路由行为
	* `Exchange(交换器)`:用于把消息路由到队列的组件
	* `Queue(队列)`:用于存储消息的数据结构(内存或者硬盘)
	* `Binding(绑定规则)`:一套规则，用于告诉`Exchange(交换器)`消息应该被存储到哪个`Queue(队列)`
4. 模型图
![](https://gitee.com/jannal/images/raw/master/RabbitMQ/15376193715642.jpg)



# Queue
1. `Queue（队列）`是`RabbitMQ`的内部对象，用于存储消息。`RabbitMQ中的消息都只能存储在Queue(队列)中`。`Producer(生产者)`发送的消息最终会投递到`Queue（队列）`中(从上图可以看出Producer并不直接与Queue直接交互)，`Consumer(消费者)`可以从`Queue（队列）`中获取消息并消费
2. 多个`Consumer(消费者)`可以订阅同一个`Queue（队列）`，此时队列中的消息会被平均分配(Round-Robin，轮询)给多个`Consumer(消费者)`处理，`RabbitMQ`不支持队列层面的广播消费。

# Exchange

`Producer(生产者)`会将消息发送给`Exchange(交换器)`，由`Exchange(交换器)`将消息路由到一个或者多个`Queue(队列)`(或者丢弃)。因为消息是存储在`Queue(队列)`中，所以`Exchange(交换器)`的使用并不真正的消耗服务器的性能，而`Queue(队列)`会。
![](https://gitee.com/jannal/images/raw/master/RabbitMQ/15376206636076.jpg)
2. `RoutingKey(路由键)`: `Producer`将消息发送给`Exchange(交换器)`时一般会指定一个`RoutingKey(路由键)`用来指定这个消息的路由规则，而这个`RoutingKey(路由键)`需要与交换器类型、`BindingKey(绑定键)`联合使用，最终才能到相应的`Queue(队列)`
3. `BindingKey(绑定键)`:`RabbitMQ Broker`通过`BindingKey(绑定键)`将`Exchange(交换器)`与`Queue(队列)`关联起来，这样`RabbitMQ Broker`就知道如何正确地将消息路由到那个`Queue(队列)`了
4.  当`RoutingKey(路由键)`与`BindingKey(绑定键)`相匹配时，消息会被路由到对应的队列中。在绑定多个队列到同一个`Exchange(交换器)`的时候，这些绑定允许使用相同的`BindingKey(绑定键)`，`BindingKey(绑定键)`是否有效取决于`Exchange(交换器)`的类型，比如`fanout`类型的`Exchange(交换器)`无论`BindingKey(绑定键)`是什么都会将消息路由到所有绑定到该`Exchange(交换器)`的`Queue(队列)`中。
5. `BindingKey(绑定键)`官方解释是绑定的时候使用的`RoutingKey(路由键)`，大多数时候都把`BindingKey(绑定键)`和`RoutingKey(路由键)`看做`RoutingKey(路由键)`,事实上`RabbitMQ java API`中只有`RoutingKey(路由键)`，并没有`BindingKey(绑定键)`，在API调用的时候需要自己辨别。
6. `发信人(Producer)`填写在包裹上的`地址(RoutingKey)`和`真实存在的地址(BindingKey)`相匹配时，这个包裹就会被正确的投递到`目的地(Queue)`,如果匹配不上，就不会投递到`目的地(Queue)`
	 ![](https://gitee.com/jannal/images/raw/master/RabbitMQ/15376216988866.jpg)

## Exchange常见类型



### fanout

1. `fanout类型`的交换器会把所有发送到该`Exchange`的消息路由到所有与它绑定的`Queue(队列)`中。下图中，生产者（P）发送到Exchange（X）的所有消息都会路由到图中的两个Queue，并最终被两个消费者（C1与C2）消费。
![](https://gitee.com/jannal/images/raw/master/RabbitMQ/15096961826960.jpg)

### direct

1. `direct类型`的`Exchange交换器`会把消息路由到那些`BindingKey(绑定键)`与 `RoutingKey(路由键)`完全匹配的`Queue(队列)`中。
 ![](https://gitee.com/jannal/images/raw/master/RabbitMQ/15096962359437.jpg)
2. 以上图的配置为例，我们以`RoutingKey="error"`发送消息到`Exchange`，则消息会路由到Queue1（amqp.gen-S9b…，这是由RabbitMQ自动生成的Queue名称）和Queue2（amqp.gen-Agl…）；如果我们以`RoutingKey="info"`或`RoutingKey="warning"`来发送消息，则消息只会路由到Queue2。如果我们以其他`RoutingKey`发送消息，则消息不会路由到这两个`Queue`中。

### topic
1. `topic类型`的`Exchange(交换器)`在匹配规则上进行了扩展，它与`direct类型`的`Exchange交换器`相似，也是将消息路由到`BindingKey(绑定键)`与 `RoutingKey(路由键)`相匹配的`Queue(队列)`中，但这里的匹配规则有些不同，它约定： `RoutingKey(路由键)`为一个`句点号"."`分隔的字符串（我们将被`句点号"."`分隔开的每一段独立的字符串称为一个单词），如`"stock.usd.nyse"`、`"nyse.vmw"`、`"quick.orange.rabbit"`。`BindingKey(绑定键)`与 `RoutingKey(路由键)`一样也是`句点号"."`分隔的字符串。
2. `BindingKey(绑定键)`中可以存在两种特殊字符`"*"`与`"#"`，用于做模糊匹配，其中`"*"`用于匹配一个单词，`"#"`用于匹配多个单词（可以是零个）。
3. 图示 
	* ![](https://gitee.com/jannal/images/raw/master/RabbitMQ/15096972976916.jpg)
	* 以上图中的配置为例，`routingKey="quick.orange.rabbit"`的消息会同时路由到Q1与Q2，`routingKey="lazy.orange.fox"`的消息会路由到Q1，`routingKey="lazy.brown.fox"`的消息会路由到Q2，`routingKey="lazy.pink.rabbit"`的消息会路由到Q2（只会投递给Q2一次，虽然这个`RoutingKey(路由键)`与Q2的两个`BindingKey(绑定键)`都匹配）.`routingKey="quick.brown.fox"`、`routingKey="orange"`、`routingKey="quick.orange.male.rabbit"`的消息将会被丢弃，因为它们没有匹配任何`BindingKey(绑定键)`。

### headers

1. `headers`类型的`Exchange(交换器)`不依赖于`RoutingKey(路由键)`的匹配规则来路由消息，而是根据发送的消息内容中中`headers`属性进行匹配。`Queue`与`Exchange`绑定时指定的`key-value`键值对，如果完全匹配消息发送到`Exchange`时消息内容中的`headers`属性(`key-value`键值对)，则消息会路由到该队列。`headers`类型的`Exchange(交换器)`性能很差，一般情况下不会使用

### 

# Virtual hosts

1. 每个`virtual host`本质上都是一个`RabbitMQ Server`，拥有它自己的`queue`，`exchagne`，和绑定规则等等。这保证了你可以在多个不同的`Application`中使用`RabbitMQ`。


	  
# docker安装

1. 安装
	
	```java
 	$ docker run -d  --name rabbitmq-3.6.15-management  -p 15671:15671 -p 15672:15672 -p 5671:5671 -p 5672:5672 rabbitmq:3.6.15-management
	```
2. 访问http://jannal.mac.com:15672/

