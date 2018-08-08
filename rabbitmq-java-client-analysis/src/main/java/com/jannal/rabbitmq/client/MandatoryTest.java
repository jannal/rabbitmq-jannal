package com.jannal.rabbitmq.client;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * 1. 创建一个vhost:jannal-vhost
 * 2. 创建一个exchange:jannal.exchange
 * 3. 不创建队列，交换器不绑定任何队列
 *
 * @author jannal
 * @create 2018-05-03
 **/
public class MandatoryTest {

    public static void main(String[] args) {

        String userName = "jannal";
        String password="jannal";
        String virtualHost ="jannal-vhost";
        String hostName = "jannal.mac.com";
        int portNumber = 5672;
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername(userName);
        factory.setPassword(password);
        factory.setVirtualHost(virtualHost);
        factory.setHost(hostName);
        factory.setPort(portNumber);
        factory.setAutomaticRecoveryEnabled(false);

        Connection conn =null;
        try {
            conn = factory.newConnection();
            Channel channel = conn.createChannel();
            //channel.exchangeDeclare("jannal.exchange", "topic", true);
            //String queueName = channel.queueDeclare().getQueue();
            //channel.queueBind("jannal.queue", "jannal.exchange", "*.#");
            byte[] messageBodyBytes = "Hello, world!".getBytes();
            channel.basicPublish("jannal.exchange", "*.#", true,null, messageBodyBytes);

            //生产者没有成功的将消息路由到队列，此时rabbitmq会响应Basic.Return命令，客户端通过ReturnListener监听
            channel.addReturnListener(new ReturnListener() {
                @Override
                public void handleReturn(int replyCode, String replyText, String exchange, String routingKey, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    String result = new String(replyText);
                    //返回的信息:NO_ROUTE
                    System.out.println("返回的信息:"+result);
                    String bodyStr = new String(body);
                    //发送的信息:Hello, world!
                    System.out.println("发送的信息:"+bodyStr);
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }finally {
            if(conn!=null){
                try {
                    conn.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
