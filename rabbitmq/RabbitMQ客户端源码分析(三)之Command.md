[TOC]

<!--20181020-->

# RabbitMQ-java-client版本

1. `com.rabbitmq:amqp-client:4.3.0`
2. `RabbitMQ`版本声明: 3.6.15


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

1. [MQCommand](https://gitee.com/jannal/rabbitmq/blob/master/rabbitmq-java-client/src/main/java/com/rabbitmq/client/impl/AMQCommand.java)委托多个`CommandAssembler`类进行字节流与协议的转换，`CommandAssembler`是线程安全的
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

1. 源码分析，`CommandAssembler`是线程安全的
    
    ```java
        final class CommandAssembler {
            private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
        
            /** Current state, used to decide how to handle each incoming frame.
             * 当前状态，用于决定如何处理每个传入的帧，不过这个名字取名CAState真的好吗????(哈哈)
             * */
            private enum CAState {
                EXPECTING_METHOD, EXPECTING_CONTENT_HEADER, EXPECTING_CONTENT_BODY, COMPLETE
            }
            //
            private CAState state;
        
            /** The method for this command */
            private Method method;
            
            /** The content header for this command */
            private AMQContentHeader contentHeader;
        
            /** The fragments of this command's content body - a list of byte[] */
            private final List<byte[]> bodyN;
            /** sum of the lengths of all fragments 所有内容帧body数组加起来的长度*/
            private int bodyLength;
        
            /** No bytes of content body not yet accumulated */
            private long remainingBodyBytes;
        
            public CommandAssembler(Method method, AMQContentHeader contentHeader, byte[] body) {
                this.method = method;
                this.contentHeader = contentHeader;
                this.bodyN = new ArrayList<byte[]>(2);
                this.bodyLength = 0;
                this.remainingBodyBytes = 0;
                appendBodyFragment(body);
                if (method == null) {
                    this.state = CAState.EXPECTING_METHOD;
                } else if (contentHeader == null) {
                    this.state = method.hasContent() ? CAState.EXPECTING_CONTENT_HEADER : CAState.COMPLETE;
                } else {
                    this.remainingBodyBytes = contentHeader.getBodySize() - this.bodyLength;
                    updateContentBodyState();
                }
            }
        
            public synchronized Method getMethod() {
                return this.method;
            }
        
            public synchronized AMQContentHeader getContentHeader() {
                return this.contentHeader;
            }
        
            /** @return true if the command is complete */
            public synchronized boolean isComplete() {
                return (this.state == CAState.COMPLETE);
            }
        
            /** Decides whether more body frames are expected */
            private void updateContentBodyState() {
                this.state = (this.remainingBodyBytes > 0) ? CAState.EXPECTING_CONTENT_BODY : CAState.COMPLETE;
            }
        
            private void consumeMethodFrame(Frame f) throws IOException {
                if (f.type == AMQP.FRAME_METHOD) {
                    //如果是方法帧就读取方法帧payload中的流，这里的f.getInputStream()仅仅是payload，不包含帧头数据
                    this.method = AMQImpl.readMethodFrom(f.getInputStream());
                    this.state = this.method.hasContent() ? CAState.EXPECTING_CONTENT_HEADER : CAState.COMPLETE;
                } else {
                    throw new UnexpectedFrameError(f, AMQP.FRAME_METHOD);
                }
            }
        
            private void consumeHeaderFrame(Frame f) throws IOException {
                if (f.type == AMQP.FRAME_HEADER) {
                    //内容头帧
                    this.contentHeader = AMQImpl.readContentHeaderFrom(f.getInputStream());
                    this.remainingBodyBytes = this.contentHeader.getBodySize();
                    updateContentBodyState();
                } else {
                    throw new UnexpectedFrameError(f, AMQP.FRAME_HEADER);
                }
            }
        
            private void consumeBodyFrame(Frame f) {
                if (f.type == AMQP.FRAME_BODY) {
                    byte[] fragment = f.getPayload();
                    this.remainingBodyBytes -= fragment.length;
                    updateContentBodyState();
                    if (this.remainingBodyBytes < 0) {
                        throw new UnsupportedOperationException("%%%%%% FIXME unimplemented");
                    }
                    appendBodyFragment(fragment);
                } else {
                    throw new UnexpectedFrameError(f, AMQP.FRAME_BODY);
                }
            }
        
            /** Stitches together a fragmented content body into a single byte array
             * 合并内容帧body数组
             * */
            private byte[] coalesceContentBody() {
                if (this.bodyLength == 0) return EMPTY_BYTE_ARRAY;
                if (this.bodyN.size() == 1) return this.bodyN.get(0);
        
                byte[] body = new byte[bodyLength];
                int offset = 0;
                for (byte[] fragment : this.bodyN) {
                    System.arraycopy(fragment, 0, body, offset, fragment.length);
                    offset += fragment.length;
                }
                this.bodyN.clear();
                this.bodyN.add(body);
                return body;
            }
        
            public synchronized byte[] getContentBody() {
                return coalesceContentBody();
            }
        
            private void appendBodyFragment(byte[] fragment) {
                if (fragment == null || fragment.length == 0) return;
                bodyN.add(fragment);
                bodyLength += fragment.length;
            }
        
            /**
             * 处理Frame,根据当前的状态获取相关的Frame
             * @param f frame to be incorporated
             * @return true if command becomes complete
             * @throws IOException if error reading frame
             */
            public synchronized boolean handleFrame(Frame f) throws IOException
            {
                switch (this.state) {
                  case EXPECTING_METHOD:          consumeMethodFrame(f); break;
                  case EXPECTING_CONTENT_HEADER:  consumeHeaderFrame(f); break;
                  case EXPECTING_CONTENT_BODY:    consumeBodyFrame(f);   break;
        
                  default:
                      throw new AssertionError("Bad Command State " + this.state);
                }
                return isComplete();
            }
        }
        
    ```


