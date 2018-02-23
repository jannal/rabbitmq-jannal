[TOC]

# Command
1. `Command`接口是AMQP方法-参数的容器接口，带有可选的内容头(content header)和内容体(content body)
2. 源码
   
    ```java
    /**
     * Interface to a container for an AMQP method-and-arguments, with optional content header and body.
     * AMQP方法和参数的容器接口，带有可选的content header and body
     */
    public interface Command {
        /**
         * Retrieves the {@link Method} held within this Command. Downcast to
         * concrete (implementation-specific!) subclasses as necessary.
         *
         * @return the command's method.
         */
        Method getMethod();
    
        /**
         * Retrieves the ContentHeader subclass instance held as part of this Command, if any.
         *
         * Downcast to one of the inner classes of AMQP,
         * for instance {@link AMQP.BasicProperties}, as appropriate.
         *
         * @return the Command's {@link ContentHeader}, or null if none
         */
        ContentHeader getContentHeader();
    
        /**
         * Retrieves the body byte array that travelled as part of this
         * Command, if any.
         *
         * @return the Command's content body, or null if none
         */
        byte[] getContentBody();
    }

    ```
    
    
# AMQCommand
1. [AMQCommand](../rabbitmq-java-client/src/main/java/com/rabbitmq/client/impl/AMQCommand.java)详细中文注释。
2. 核心方法源码,将`Command`转换为多个`Frame`并且发送

```java
 public void transmit(AMQChannel channel) throws IOException {
        int channelNumber = channel.getChannelNumber();
        AMQConnection connection = channel.getConnection();

        synchronized (assembler) {
            Method m = this.assembler.getMethod();
            connection.writeFrame(m.toFrame(channelNumber));
            if (m.hasContent()) {
                byte[] body = this.assembler.getContentBody();

                connection.writeFrame(this.assembler.getContentHeader()
                        .toFrame(channelNumber, body.length));
                //如果body长度超过client与server协商出的最大帧长度，将分多个Frame发送
                int frameMax = connection.getFrameMax();
                int bodyPayloadMax = (frameMax == 0) ? body.length : frameMax
                        - EMPTY_FRAME_SIZE;

                for (int offset = 0; offset < body.length; offset += bodyPayloadMax) {
                    int remaining = body.length - offset;

                    int fragmentLength = (remaining < bodyPayloadMax) ? remaining
                            : bodyPayloadMax;
                    Frame frame = Frame.fromBodyFragment(channelNumber, body,
                            offset, fragmentLength);
                    connection.writeFrame(frame);
                }
            }
        }

        connection.flush();
    }
```




# CommandAssembler
1. [CommandAssembler](../rabbitmq-java-client/src/main/java/com/rabbitmq/client/impl/CommandAssembler.java)是Command汇编,线程安全的类


