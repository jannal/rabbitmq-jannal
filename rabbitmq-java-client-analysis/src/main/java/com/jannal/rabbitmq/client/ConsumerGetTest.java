package com.jannal.rabbitmq.client;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * ${DESCRIPTION}
 *
 * @author jannal
 * @create 2018-08-11
 **/
public class ConsumerGetTest {
    public static void main(String[] args) {
        String userName = "jannal";
        String password = "jannal";
        String virtualHost = "jannal-vhost";
        String hostName = "jannal.mac.com";
        int portNumber = 5672;
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername(userName);
        factory.setPassword(password);
        factory.setVirtualHost(virtualHost);
        factory.setHost(hostName);
        factory.setPort(portNumber);
        factory.setAutomaticRecoveryEnabled(false);

        Connection conn = null;
        try {
            conn = factory.newConnection();


            final Channel channel = conn.createChannel();

            while (true) {
                /**
                 *  Basic.get是拉消息,性能比Basic.Consume低，尽量避免使用
                 *  autoAck 如果设置为false，则需要调用Channel.basicAck()来ack
                 */
                GetResponse getResponse = channel.basicGet("jannal.queue", false);

                if (getResponse != null) {
                    byte[] body = getResponse.getBody();
                    Envelope envelope = getResponse.getEnvelope();
                    int messageCount = getResponse.getMessageCount();
                    AMQP.BasicProperties props = getResponse.getProps();
                    System.out.println(new String(body, "utf-8"));
                    channel.basicAck(getResponse.getEnvelope().getDeliveryTag(),true);
                } else {
                    System.out.println("响应为空");
                }
            }





        } catch (IOException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
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
}
