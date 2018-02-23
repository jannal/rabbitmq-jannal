package com.jannal.rabbitmq.client;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class BasicMessageSend {
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
            channel.queueBind("jannal.queue", "jannal.exchange", "*.#");
            byte[] messageBodyBytes = "Hello, world!".getBytes();
            channel.basicPublish("jannal.exchange", "*.#", null, messageBodyBytes);

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
