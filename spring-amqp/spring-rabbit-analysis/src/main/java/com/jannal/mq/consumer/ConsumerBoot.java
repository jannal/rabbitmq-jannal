package com.jannal.mq.consumer;

import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author jannal
 * @create 2017-12-15
 **/
public class ConsumerBoot {
    public static void main(String[] args) throws Exception{
        AbstractApplicationContext ctx =
                new ClassPathXmlApplicationContext("rabbitmq-consumer.xml");
        //ctx.destroy();
    }
}
