package com.jannal.mq.publish;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.concurrent.CountDownLatch;

/**
 * @author jannal
 * @create 2017-12-15
 **/
public class MessagePublish {
    public static void main(String[] args) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AbstractApplicationContext ctx =
                new ClassPathXmlApplicationContext("rabbitmq-producer.xml");
        RabbitTemplate template = ctx.getBean(RabbitTemplate.class);
        for(int i=0;i<1;i++){
            template.convertAndSend("Hello, world "+i);
        }




        //throw new RuntimeException("11111");


        //发送json格式数据
        //String json = "{\"foo\" : \"value\" }";
        //Message jsonMessage = MessageBuilder.withBody(json.getBytes())
        //        .andProperties(MessagePropertiesBuilder.newInstance().setContentType("application/json")
        //                .build()).build();
        //template.convertAndSend(jsonMessage);
        //
        ////template.convertAndSend("Hello, world2!");
        ////template.convertAndSend("Hello, world3!");
        //countDownLatch.countDown();
       // ctx.destroy();

    }
}
