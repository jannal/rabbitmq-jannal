package com.jannal.mq.consumer;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.stereotype.Component;

/**
 * @author jannal
 * @create 2017-12-15
 **/
@Component
public class CommonMessageListener implements MessageListener{
    @Override
    public void onMessage(Message message) {
        System.out.println(message.getMessageProperties());
        String messageBody = new String(message.getBody());
        System.out.println(" start 接收 '" + messageBody + "'");
        if(messageBody.equals("Hello, world 0")){
            try {
                Thread.sleep(100000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println(" end 接收 '" + messageBody + "'");
    }
}
