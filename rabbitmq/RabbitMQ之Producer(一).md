[TOC]

<!--v20180924-->

# RabbitMQ-java-client版本

1. `com.rabbitmq:amqp-client:4.3.0`
2. `RabbitMQ`版本声明: 3.6.15

# API介绍

1. `Connection Factory`、`Connection`、`Channel`都是`RabbitMQ`对外提供的API中最基本的对象。`Connection`是`RabbitMQ`的socket连接，它封装了socket协议相关部分逻辑。`Connection Factory`则是`Connection`的制造工厂,`Connection`是用来开启`Channel`的,AMQP协议层面的操作是通过`Channel`进行的。

2. 代码示例
	
	```java
	private Logger logger = LoggerFactory.getLogger("rabbitmq-producer");
	    @Test
	    public void testDirectExchange(){
	
	        ConnectionFactory factory = new ConnectionFactory();
	
	
	        String userName = "jannal";
	        String password = "jannal";
	        String virtualHost = "jannal-vhost";
	        String queueName = "jannal.direct.queue";
	        String exchange = "jannal.direct.exchange";
	        String routingKey = "SMS";
	        String bindingKey = "SMS";
	        String hostName = "jannal.mac.com";
	        int portNumber = 5672;
	
	
	        factory.setUsername(userName);
	        factory.setPassword(password);
	        factory.setVirtualHost(virtualHost);
	        factory.setHost(hostName);
	        factory.setPort(portNumber);
	        //以上等价下面
	        //factory.setUri("amqp://jannal:jannal@jannal.mac.com:5672/jannal-vhost");
	
	        factory.setAutomaticRecoveryEnabled(false);
	
	        Connection conn = null;
	        try {
	            //创建连接
	            conn = factory.newConnection();
	            //通过连接创建通道
	            Channel channel = conn.createChannel();
	            //持久化
	            boolean durable = true;
	            //独占
	            boolean exclusive = false;
	            //是否自动删除
	            boolean autoDelete = false;
	            //声明队列
	            channel.queueDeclare(queueName, durable, exclusive, autoDelete, null);
	            //声明交换器,direct交换器类型下，RoutingKey与BindingKey需要完全匹配
	            channel.exchangeDeclare(exchange, "direct", true);
	            //绑定
	            channel.queueBind(queueName, exchange, bindingKey);
	
	             //无法路由时，消息处理方式。true返回给Producer，false则直接丢弃消息
	            boolean mandatory = false;
	            //queue没有Consumer，消息返回给Producer，不加入queue。有Consumer，则加入queue投递给Consumer，RabbitMQ3.0后已经废弃，默认false
	            boolean immediate = false;
	            String msg = "Hello, world ";
            	//deliveryMode为2，表示消息会持久化到磁盘
            	channel.basicPublish(exchange, 
            		routingKey,
            		mandatory, 
            		immediate, 
            		MessageProperties.PERSISTENT_TEXT_PLAIN, 					msg.getBytes("UTF-8"));

	            //发送带headers的消息
	            HashMap<String, Object> headers = new HashMap<>();
	            headers.put("jannal", "jannal");
	            channel.basicPublish(exchange, 
	            	routingKey,
	            	mandatory, 
	            	immediate,
                   new AMQP.BasicProperties()
                    .builder()
                    .headers(headers)
                    .deliveryMode(2).build(),
                    msg.getBytes("UTF-8"));

            	//发送带有过期时间的消息
            	channel.basicPublish(exchange, 
            		routingKey, 
            		mandatory, 
            		immediate,
                    new AMQP.BasicProperties()
                    .builder()
                    .expiration("1000")
                    .deliveryMode(2).build(),
                    msg.getBytes("UTF-8"));	        } catch (IOException e) {
	            logger.error(e.getMessage(), e);
	        } catch (TimeoutException e) {
	            logger.error(e.getMessage(), e);
	        } finally {
	            if (conn != null) {
	                try {
	                    conn.close();
	                } catch (IOException e) {
	                    e.printStackTrace();
	                }
	            }
	        }
	    }
	
	```
3.  查看`Exchange`
![](https://gitee.com/jannal/images/raw/master/RabbitMQ/15377924713195.jpg)
4. 查看队列	
![](https://gitee.com/jannal/images/raw/master/RabbitMQ/15377925241744.jpg)
![](https://gitee.com/jannal/images/raw/master/RabbitMQ/15377925465837.jpg)


	
## `queueDeclare`	
	
1. 方法声明
	
	```java
	Queue.DeclareOk queueDeclare(
	String queue, //队列名称
	boolean durable, //是否持久化
	boolean exclusive,// 独占
	boolean autoDelete,//是否自动删除
  	Map<String, Object> arguments) //队列的其他参数
  	throws IOException

	```
2. 方法参数说明	
	* `durable`:是否持久化,true表示一个持久的`Queue(队列)`(持久化到磁盘)，在服务器重启之后不会丢失`Queue(队列)`的相关信息(**注意这里仅仅`Queue(队列)`的信息的持久化，而不是队列中消息的持久化**)
	* `exclusive`: 是否是独占(排他)队列
		- 基于`Connection`可见，通过一个`Connection`的不同`Channel`是可以同时访问的
		- 如果一个`Connection`已经建立一个`exclusive(独占)`的`Queue(队列)`，其他`Connection`是不允许建立同名的独占`Queue(队列)`的
		- 即使`Queue(队列)`是持久化的，一旦`Connection`关闭或者客户端退出，该独占`Queue(队列)`都会被自动删除。这种队列适用于一个客户端同时发送和读取消息的应用场景
	* `autoDelete`: 是否自动删除,true表示设置队列自动删除。自动删除的前提是:至少有一个`Consumer` 连接到这个队列，之后所有与这个队列连接的`Consumer`都断开时，才会自动删除
	* `arguments`:设置队列的其他参数,如`x-message-ttl`、`x-expires`、`x-max-length`等
	
## `exchangeDeclare`

1. 方法声明

	```java
	
	public Exchange.DeclareOk exchangeDeclare(
		String exchange, //交换器名称
		String type,//交换器类型fanout、direct、topic                                                     		boolean durable, //是否持久化                                            		boolean autoDelete,//是否自动删除                                              		boolean internal, //是否内置                                             		Map<String, Object> arguments){
		
		  return (Exchange.DeclareOk)
                exnWrappingRpc(new Exchange.Declare.Builder()
                                .exchange(exchange)
                                .type(type)
                                .durable(durable)
                                .autoDelete(autoDelete)
                                .internal(internal)
                                .arguments(arguments)
                               .build())
                .getMethod();
		
		}
	
	void exchangeDeclareNoWait(
		String exchange,
      	String type,
      	boolean durable,
      	boolean autoDelete,
      	boolean internal,
      	Map<String, Object> arguments) throws IOException{
      	
   		 transmit(new AMQCommand(new Exchange.Declare.Builder()
                                .exchange(exchange)
                                .type(type)
                                .durable(durable)
                                .autoDelete(autoDelete)
                                .internal(internal)
                                .arguments(arguments)
                                .passive(false)
                                .nowait(true)//多了一个nowait
                                .build()));
    }   	
   	}
	```
	
2. 方法参数详解
	* `type`:交换器类型，`fanout`、`topic`、`direct`
	* `durable`:设置是否持久化，true表示将`Exchange`持久化到磁盘，在服务器重启时不会丢失相关信息
	* `autoDelete`:是否自动删除，自动删除的前提是至少有一个`Queue(队列)`或者`Exchange(交换器)`与当前设定的`Exchange(交换器)`绑定，之后所有与当前设定的`Exchange(交换器)`绑定的`Queue(队列)`或者`Exchange(交换器)`都与此解绑
	* `internal`:是否内置。true表示是内置的`Exchange`,客户端程序无法直接发送消息到这个`Exchange`中，只能通过`Exchange`路由到另一个`Exchange`这种方式
	* `argument`: 结构化参数
3. 返回值
	* `Exchange.DeclareOk`:使用`exchangeDeclare`客户端声明一个`Exchange`之后，需要同步等待服务端返回.而使用`exchangeDeclareNoWait`不需要服务端同步返回结果


## `queueBind`

1. 方法
 
	 ```java
	    public Queue.BindOk queueBind(
	    	String queue, //队列名
	    	String exchange,//交换器名称
	      	String routingKey, //绑定Queue和Exchange的bindingKey
	      	Map<String, Object> arguments
	      	) throws IOException
	    {
	        validateQueueNameLength(queue);
	        return (Queue.BindOk)
	               exnWrappingRpc(new Queue.Bind.Builder()
	                               .queue(queue)
	                               .exchange(exchange)
	                               .routingKey(routingKey)
	                               .arguments(arguments)
	                              .build())
	               .getMethod();
	    }
	
	 ```
2. `BindingKey`其实也属于`RoutingKey`的一种，官方解析为在绑定的时候使用的路由键。为了避免混淆，可以通过如下方式区分
	* 在使用绑定的时候，其中需要的路由键是`BindingKey`.涉及的客户端方法一般有bind字样，如`channel.exchangeBind`、`channel.queueBind`
	* 在发送消息的时候，其中的路由键是`RoutingKey`,设计的客户端方法有`channel.basicPublish`	 

## `exchangeBind`

1. 方法
	
	```java
	public Exchange.BindOk exchangeBind(
		String destination, 
		String source,
	    String routingKey, 
	    Map<String, Object> arguments
	    )throws IOException
	```
2. 	不仅可以将`Exchange`与`Queue`绑定，也可以将`Exchange`与`Exchange`绑定。绑定后消息从`source Exchange`转发到`destination Exchange`，进而存储在 `destination Exchange`绑定的`Queue`中

## `basicPublish`

1. 方法
	
	```java
	public void basicPublish(String exchange, //交换器名称
							String routingKey,//路由键
	                       boolean mandatory,//
	                       boolean immediate,
	                       BasicProperties props,//消息的基本属性 
	                       byte[] body)
	
	```
2. 参数详解
	* `mandatory`:当`mandatory`设置为true，`Exchange`无法根据自身的类型和`RoutingKey`找到一个匹配的`Queue`，那么`RabbitMQ`会调用`Basic.Return`命令将消息返回给生产者。`Producer`可以通过`channel.addReturnListener`来获取返回的消息。如果`mandatory`设置为false，则`RabbitMQ`会直接丢弃消息.
		
	   ```java
	            channel.exchangeDeclare(exchange, "direct", true);
	            boolean mandatory = true;
	            boolean immediate = false;
	            String msg = "no match queue ";
	            channel.basicPublish(exchange, routingKey, mandatory, immediate, MessageProperties.PERSISTENT_TEXT_PLAIN, msg.getBytes("UTF-8"));
	            channel.addReturnListener(new ReturnListener() {
	                @Override
	                public void handleReturn(int replyCode, String replyText, String exchange, String routingKey, AMQP.BasicProperties properties, byte[] body) throws IOException {
	                    logger.info("Basic.Return 返回的消息:{}", new String(body, "utf-8"));
	                }
	            });
		
		``` 
	* `immediate`:如果设置为true，交换器在将消息路由到队列时发现队列上并不存在任何`Consumer(消费者)`，那么这条消息将不会存入队列中。当与`RoutingKey`匹配的所有`Queue`都没有`Consumer(消费者)`，该消息会通过`Basic.Return`返回给`Producer(生产者)`.从`RabbitMQ3.0`版本开始不支持`immediate`(所以客户端最好设为false，否则会抛出异常),因为`immediate`会影响镜像队列的性能，增加了代码的复杂性，建议采用`TTL`和`DLX(Dead-Letter-Exchange死信队列)`代替




# 持久化

1. `RabbitMQ`的持久化分为三个部分:	
	* `Exchange`的持久化,声明时将`durable`设置为true
	* `Queue`的持久化，声明时将`durable`设置为true，这里仅仅是`Queue`相关信息的持久化，并不代表`Queue`中`消息`的持久化。
	* `Queue`中`消息`的持久化，设置`BasicProperties`中的`deliveryMode`属性为2,`Queue`中`消息`持久化的前提是`Queue`本身要持久化
2. 将所有消息都设置为持久化，会严重影响`RabbitMQ`的性能(随机写入磁盘)，对于可靠性不是很高的消息可以不采用持久化以提高整体的吞吐量
3. 即使将`Exchange`、`Queue`、`消息`都设置了持久化后也不能保证数据不丢失
	* 在持久化的消息正确存入`RabbitMQ`之后，还有一段时间才能存入磁盘之后。`RabbitMQ`并不会为每条消息都进行同步存盘，可能保存在操作系统缓存之中而不是物理磁盘之中。如果再次期间`RabbitMQ`服务节点宕机、重启或者发生异常等，消息保存还没从缓存同步到磁盘中，那么这些消息将丢失
	* `publish confirm(发送方确认)`模式，一旦`Channel`进入`Confirm`模式，所有在该`Channel`上面发布的消息都会被指派一个唯一的ID，一旦消息被投递到所有匹配的队列之后，`RabbitMQ`就会发送一个`Basic.Ack(确认)`给`Producer`，如果消息和`Queue`是可持久化的，那么确认消息会在消息写入磁盘后发出。`RabbitMQ`回传给生产者的确认消息中的`deliveryTag`包含了确认消息的序号，此外`RabbitMQ`也可以设置`Channel.basicAck`方法中的`multiple`参数，表示到这个序号之前的所有消息都已经得到了处理(类似批量确认)。
		![](https://gitee.com/jannal/images/raw/master/RabbitMQ/15377670840712.jpg)



