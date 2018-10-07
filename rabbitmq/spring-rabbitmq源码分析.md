# spring-rabbitmq源码分析

## 环境
1. 客户端版本
 ```
 		<dependency>
            <groupId>org.springframework.amqp</groupId>
            <artifactId>spring-rabbit</artifactId>
            <version>1.5.6.RELEASE</version>
        </dependency>
 ```

## 分析入口
1. RabbitTemplate

```
1. 入口
public void convertAndSend(Object object) throws AmqpException {
		convertAndSend(this.exchange, this.routingKey, object, (CorrelationData) null);
	}

2. 第一步
public void send(final String exchange, final String routingKey,
			final Message message, final CorrelationData correlationData)
			throws AmqpException {
		execute(new ChannelCallback<Object>() {

		//在下面的doExecute方法中执行此回调
			@Override
			public Object doInRabbit(Channel channel) throws Exception {
			//doSend看下面的分析
				doSend(channel, exchange, routingKey, message,
						returnCallback != null
								&& mandatoryExpression.getValue(evaluationContext, message, Boolean.class),
						correlationData);
				return null;
			}
		}, obtainTargetConnectionFactoryIfNecessary(this.sendConnectionFactorySelectorExpression, message));
	}
3. 第二步真正的执行入口
private <T> T execute(final ChannelCallback<T> action, final ConnectionFactory connectionFactory) {
		if (this.retryTemplate != null) {
			try {
				return this.retryTemplate.execute(new RetryCallback<T, Exception>() {

					@Override
					public T doWithRetry(RetryContext context) throws Exception {
						return RabbitTemplate.this.doExecute(action, connectionFactory);
					}

				}, (RecoveryCallback<T>) this.recoveryCallback);
			}
			catch (Exception e) {
				if (e instanceof RuntimeException) {
					throw (RuntimeException) e;
				}
				throw RabbitExceptionTranslator.convertRabbitAccessException(e);
			}
		}
		else {
			return doExecute(action, connectionFactory);
		}
	}
	
	
	private <T> T doExecute(ChannelCallback<T> action, ConnectionFactory connectionFactory) {
		Assert.notNull(action, "Callback object must not be null");
		RabbitResourceHolder resourceHolder = ConnectionFactoryUtils.getTransactionalResourceHolder(
				(connectionFactory != null ? connectionFactory : getConnectionFactory()), isChannelTransacted());
		//获取通道	
		Channel channel = resourceHolder.getChannel();
		
		if (this.confirmsOrReturnsCapable == null) {
		//判断当前的连接工厂是否是PublisherCallbackChannelConnectionFactory,如果是，是不是发布确认模式
			if (getConnectionFactory() instanceof PublisherCallbackChannelConnectionFactory) {
				PublisherCallbackChannelConnectionFactory pcccf =
						(PublisherCallbackChannelConnectionFactory) getConnectionFactory();
				this.confirmsOrReturnsCapable = pcccf.isPublisherConfirms() || pcccf.isPublisherReturns();
			}
			else {
				this.confirmsOrReturnsCapable = Boolean.FALSE;
			}
		}
		if (this.confirmsOrReturnsCapable) {
		   //如果是发布确认模式，添加异步回调监听器
			addListener(channel);
		}
		try {
			if (logger.isDebugEnabled()) {
				logger.debug("Executing callback on RabbitMQ Channel: " + channel);
			}
			return action.doInRabbit(channel);
		}
		catch (Exception ex) {
			if (isChannelLocallyTransacted(channel)) {
				resourceHolder.rollbackAll();
			}
			throw convertRabbitAccessException(ex);
		}
		finally {
			ConnectionFactoryUtils.releaseResources(resourceHolder);
		}
	}
4. 发送
 protected void doSend(Channel channel, String exchange, String routingKey, Message message,
			boolean mandatory, CorrelationData correlationData) throws Exception {
		if (logger.isDebugEnabled()) {
			logger.debug("Publishing message on exchange [" + exchange + "], routingKey = [" + routingKey + "]");
		}

		if (exchange == null) {
			// try to send to configured exchange
			exchange = this.exchange;
		}

		if (routingKey == null) {
			// try to send to configured routing key
			routingKey = this.routingKey;
		}
		//设置发送确认(确认回调和channel必须是PublisherCallbackChannel才设置)
		setupConfirm(channel, correlationData);
		Message messageToUse = message;
		MessageProperties messageProperties = messageToUse.getMessageProperties();
		
		if (mandatory) { //设置头部标志位
		mandatory标志位
		当mandatory标志位设置为true时，如果exchange根据自身类型和消息routeKey无法找到一个符合条件的queue，那么会调用		basic.return方法将消息返还给生产者；当mandatory设为false时，出现上述情形broker会直接将消息扔掉。
			messageProperties.getHeaders().put(PublisherCallbackChannel.RETURN_CORRELATION_KEY, this.uuid);
		}
		if (this.beforePublishPostProcessors != null) {
			for (MessagePostProcessor processor : this.beforePublishPostProcessors) {
				messageToUse = processor.postProcessMessage(messageToUse);
			}
		}
		//将消息属性转换为具体消息头
		BasicProperties convertedMessageProperties = this.messagePropertiesConverter
				.fromMessageProperties(messageProperties, encoding);
		//		
		channel.basicPublish(exchange, routingKey, mandatory, convertedMessageProperties, messageToUse.getBody());
		// Check if commit needed
		if (isChannelLocallyTransacted(channel)) {
			// Transacted channel created by this template -> commit.
			RabbitUtils.commitIfNecessary(channel);
		}
	}	
	
```

