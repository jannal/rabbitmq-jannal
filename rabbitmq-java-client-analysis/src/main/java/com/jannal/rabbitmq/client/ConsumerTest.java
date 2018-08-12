package com.jannal.rabbitmq.client;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class ConsumerTest {
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



            final Channel channel =conn.createChannel();
            //设置客户端最多接收未被ack的消息的个数
            channel.basicQos(27);

            //basicConsume是一个同步方法，这里设置不自动确认
            String consumerTag = channel.basicConsume("jannal.queue", false, "consumerTag", new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    super.handleDelivery(consumerTag, envelope, properties, body);
                    String routingKey = envelope.getRoutingKey();
                    String contentType = properties.getContentType();

                    long deliveryTag = envelope.getDeliveryTag();
                    // (process the message components here ...)
                    channel.basicAck(deliveryTag, true);
                    System.out.println(new String(body, "utf-8"));
                }
            });

            System.out.println("consumerTag:"+consumerTag);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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
