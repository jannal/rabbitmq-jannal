[TOC]

<!--20181103-->

# 概念

  1. `节点(Node)`: `Erlang`虚拟机的每个实例称为`节点(Node)`,`节点(Node)`之间可以进行本地通信(不管它们是否真的在同一台服务器上) ,因为`Erlang`天生就能让应用程序无须知道对方是否在同一台机器上即可互相通信。当启动`Erlang`节点时，可以指定一个节点名称，`name(长名称)`或者`sname(short name)`，短名称例如`rabbit@hostname`,长名称例如`rabbitmq@hostname.localhost`，短名称是`rabbtimq`启动的默认方式
  2. `Erlang Cookie`:`Erlang`节点(Node)通过交换作为密钥令牌的`Erlang Cookie`以获得认证，因为你一旦连接到远程节点后，就能执行命令，因此有必要确保节点是可信的。`Erlang`将令牌存储在名为`.erlang.cookie`的文件(如果使用解压方式安装通常在home目录下，如果使用rpm方式安装在/var/lib/rabbitmq/目录下下)，查看:
   
    ```bash
        # cat ~/.erlang.cookie 
        XDMPQXXASEVWJEXUDUYG
    ```
  3. `Mnesia`:`Mnesia`是Erlang的NoSQL数据库，RabbitMQ使用它存储Queue、Exchange、bind等信息，RabbitMQ启动时会启动`Mnesia数据库`,如果此数据库启动失败，RabbitMQ也启动失败。

# 单击安装

1. 查看RabbitMQ Erlang版本:[https://www.rabbitmq.com/which-erlang.html](https://www.rabbitmq.com/which-erlang.html)
2. 环境准备

    ```bash
        # lsb_release -a 
            LSB Version:    :core-4.1-amd64:core-4.1-noarch
            Distributor ID: CentOS
            Description:    CentOS Linux release 7.5.1804 (Core) 
            Release:        7.5.1804
            Codename:       Core
            
        # uname -r
            3.10.0-862.14.4.el7.x86_64
    ```
3. 安装Erlang
    
    ```bash
        # wget http://erlang.org/download/otp_src_20.3.tar.gz
        # tar -zxvf otp_src_20.3.tar.gz -C /usr/local
        # cd /usr/local/otp_src_20.3
        # export ERL_TOP=`pwd` 
        # ./configure 
        # make
        # make release_tests 
        # cd release/tests/test_server
        # $ERL_TOP/bin/erl -s ts install -s ts smoke_test batch -s init stop
        # $ERL_TOP/release/tests/test_server/index.html  打开此文件确定所有的测试用例是否都没有错误
        # make install
        
    
    ```
4. 安装Erlang遇到的问题
    
    ```bash
        1. # ./configure 出现以下错误
        configure: error: No curses library functions found
        configure: error: /bin/sh '/usr/local/otp_src_20.3/erts/configure' failed for erts
        解决方案:  yum -y install ncurses-devel或者把必须的都安装上
        yum -y install make gcc gcc-c++ kernel-devel m4 ncurses-devel openssl-devel
    
    ```
5. 配置Erlang环境
    
    ```bash
        # vim /etc/profile
        export ERLANG_HOME=/usr/local/otp_src_20.3/
        export PATH=$ERLANG_HOME/bin:$PATH 
        # source /etc/profile
        # erl -version
    ```
6. 安装RabbitMQ
   
    ```bash
        # wget https://www.rabbitmq.com/releases/rabbitmq-server/v3.6.15/rabbitmq-server-generic-unix-3.6.15.tar.xz
        # xz -d rabbitmq-server-generic-unix-3.6.15.tar.xz  
        # sudo tar -xvf rabbitmq-server-generic-unix-3.6.15.tar -C /usr/local/
      
    ```
7. 配置环境变量
    
    ```bash
        # vim /etc/profile
        export RABBITMQ_HOME=/usr/local/rabbitmq-server-3.6.15
        export PATH=$RABBITMQ_HOME/sbin:$PATH
        # source /etc/profile
    ```
8. 启动
    
    ```bash
        1. 开启插件(可以根据自己需要开启)
        # ./rabbitmq-plugins enable rabbitmq_management
        # ./rabbitmq-plugins enable --offline rabbitmq_stomp
        # ./rabbitmq-plugins enable --offline rabbitmq_web_stomp
        
        2. 前台启动
        # ./rabbitmq-server 
    
        3. 后台启动
        # ./rabbitmq-server -detached 
        Warning: PID file not written; -detached was passed.
        
        4. 查看状态
        # ./rabbitmqctl cluster_status
        Cluster status of node rabbit@CentOS7
        [{nodes,[{disc,[rabbit@CentOS7]}]},
         {running_nodes,[rabbit@CentOS7]},
         {cluster_name,<<"rabbit@CentOS7.localdomain">>},
         {partitions,[]},
         {alarms,[{rabbit@CentOS7,[]}]}]
         
        5. 停止，stop会将Erlang一同关闭，stop_app只会关闭RabbitMQ应用服务
        # ./rabbitmqctl stop 
        Stopping and halting node rabbit@CentOS7
         
        6. 停止具体的节点或者远程节点(Node)
        # ./rabbitmqctl stop -n rabbit@CentOS7
         Stopping and halting node rabbit@CentOS7
         
    ```
    
9. 查看已经安装的插件列表
    
    ```bash
        # ./rabbitmq-plugins list
    ```
10. 添加用户和权限
    
    ```bash
        添加用户(rabbitmqctl add_user {用户名} {密码})
        # ./rabbitmqctl add_user admin 123456
        指定允许访问的vhost以及write/read
        # ./rabbitmqctl set_permissions -p "/" admin ".*" ".*" ".*"
        赋予角色
        # ./rabbitmqctl set_user_tags admin administrator
        查看用户列表
        # ./rabbitmqctl list_users 
    ```
    
# 配置

1. `RabbitMQ`的环境变量都是以`RABBITMQ_`开头，可以在shell环境中设置，也可以在`rabbitmq-env.conf`这个RabbitMQ环境变量的定义文件中设置。如果在非shell环境下配置，需要将`RABBITMQ_`前缀去掉。优先级顺序
    * shell环境
    * rabbitmq-env.conf
    * 默认的配置(`/usr/local/rabbitmq_server-3.6.15/sbin/rabbitmq-defaults`)
2. 解压方式安装默认`$RABBITMQ_HOME/etc/rabbitmq/rabbitmq.config`不存在,可以手动创建,可以参考https://github.com/rabbitmq/rabbitmq-server/blob/v3.6.x/docs/rabbitmq.config.example查看具体配置规则。比如要配置tcp相关的参数,这里配置发送和接收缓冲区的大小为1M
    ```bash
        [
          {rabbit,[
        
            {tcp_listen_options, [{backlog,128000},
                             {nodelay,true},
                            {exit_on_close,false},
                            {linger,{true,0}},  
                            {sndbuf,1048576},  
                            {recbuf,1048576} 
                            ]}
        
         ]}
        ]
    
    ```





# 单击伪集群安装

1. 启动3个节点(要注意开启的插件端口冲突问题)
   
    ```
        # RABBITMQ_NODE_PORT=5672 RABBITMQ_SERVER_START_ARGS="-rabbitmq_management listener [{port,15672}]" RABBITMQ_NODENAME=rabbit_master ./rabbitmq-server -detached
        
        # RABBITMQ_NODE_PORT=5673 RABBITMQ_SERVER_START_ARGS="-rabbitmq_management listener [{port,15673}]" RABBITMQ_NODENAME=rabbit_slave1 ./rabbitmq-server -detached
        
        # RABBITMQ_NODE_PORT=5674 RABBITMQ_SERVER_START_ARGS="-rabbitmq_management listener [{port,15674}]" RABBITMQ_NODENAME=rabbit_slave2 ./rabbitmq-server -detached
    ```
2. 以`rabbitmq_master为主节点`，如果从节点要加入主节点并获取它的元数据，需要在从节点下执行以下操作
    
    ```bash
        0. 查看hostname
        # hostname
        CentOS7
        1. 停止Erlang节点上运行的应用程序
        # ./rabbitmqctl -n rabbit_slave1@CentOS7 stop_app 
        # ./rabbitmqctl -n rabbit_slave2@CentOS7 stop_app 
        2. 重设(清空)元数据
        # ./rabbitmqctl -n rabbit_slave1@CentOS7 reset
        # ./rabbitmqctl -n rabbit_slave2@CentOS7 reset 
        3. 加入集群
        # ./rabbitmqctl -n rabbit_slave1@CentOS7 join_cluster rabbit_master@CentOS7
        # ./rabbitmqctl -n rabbit_slave2@CentOS7 join_cluster rabbit_master@CentOS7
        4. 启动从节点
        # ./rabbitmqctl -n rabbit_slave1@CentOS7  start_app
        # ./rabbitmqctl -n rabbit_slave2@CentOS7  start_app
        
        5. 查看集群状态
        # ./rabbitmqctl -n rabbit_master@CentOS7  cluster_status
            Cluster status of node rabbit_master@CentOS7
            [{nodes,[{disc,[rabbit_master@CentOS7,rabbit_slave1@CentOS7,
                            rabbit_slave2@CentOS7]}]},
             {running_nodes,[rabbit_slave2@CentOS7,rabbit_slave1@CentOS7,
                             rabbit_master@CentOS7]},
             {cluster_name,<<"rabbit_master@CentOS7.localdomain">>},
             {partitions,[]},
             {alarms,[{rabbit_slave2@CentOS7,[]},
                      {rabbit_slave1@CentOS7,[]},
                      {rabbit_master@CentOS7,[]}]}]
                      
        # ./rabbitmqctl -n rabbit_slave1@CentOS7  cluster_status      
            Cluster status of node rabbit_slave1@CentOS7
            [{nodes,[{disc,[rabbit_master@CentOS7,rabbit_slave1@CentOS7,
                            rabbit_slave2@CentOS7]}]},
             {running_nodes,[rabbit_slave2@CentOS7,rabbit_master@CentOS7,
                             rabbit_slave1@CentOS7]},
             {cluster_name,<<"rabbit_master@CentOS7.localdomain">>},
             {partitions,[]},
             {alarms,[{rabbit_slave2@CentOS7,[]},
                      {rabbit_master@CentOS7,[]},
                      {rabbit_slave1@CentOS7,[]}]}]
                      
        # ./rabbitmqctl -n rabbit_slave2@CentOS7  cluster_status 
            Cluster status of node rabbit_slave2@CentOS7
            [{nodes,[{disc,[rabbit_master@CentOS7,rabbit_slave1@CentOS7,
                            rabbit_slave2@CentOS7]}]},
             {running_nodes,[rabbit_master@CentOS7,rabbit_slave1@CentOS7,
                             rabbit_slave2@CentOS7]},
             {cluster_name,<<"rabbit_master@CentOS7.localdomain">>},
             {partitions,[]},
             {alarms,[{rabbit_master@CentOS7,[]},
                      {rabbit_slave1@CentOS7,[]},
                      {rabbit_slave2@CentOS7,[]}]}]
        
        6. 添加用户并设置权限
        # ./rabbitmqctl -n rabbit_master@CentOS7 add_user admin 123456
        # ./rabbitmqctl -n rabbit_master@CentOS7 set_permissions -p "/" admin ".*" ".*" ".*"
        # ./rabbitmqctl -n rabbit_master@CentOS7 set_user_tags admin administrator
    ```
3. 查看
![](https://gitee.com/jannal/images/raw/master/RabbitMQ/15412313229533.jpg)


# 服务器日志

1. `RabbitMQ`的日志默认存放在`$RABBITMQ_HOME/var/log/rabbitmq`文件夹内。此文件夹内会创建两个日志文件`$RABBITMQ_NODENAME-sasl.log`和`$RABBITMQ_NODENAME.log`
2. SASL(System Application Support Libraries系统应用程序支持库)是库的集合，作为Erlang-OTP发行的一部分。当RabbitMQ记录Erlang相关信息时，它会将日志写入文件`$RABBITMQ_NODENAME-sasl.log`中，比如Erlang的崩溃报告
3. `$RABBITMQ_NODENAME.log`是RabbitMQ应用服务的日志

