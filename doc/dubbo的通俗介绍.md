通俗易懂描述dubbo工作原理
关于dubbo的描述就不再赘述，直接进入主题，那就是dubbo的工作原理。dubbo分为服务提供者和服务消费者，主要的工作内容有以下几点：提供者暴露服务、消费者引入服务、提供者和消费者和注册中心之间的通信、消费者消费服务、监控中心、其他扩展

一、provider暴露服务
1、首先provider可以在配置文件中配置自己可以提供那些服务，通过<dubbo:service>可以进行配置或者注解的方式，并且在配置的时候需要配置注册中心、应用名、节点地址、通信协议等一系列参数（以DemoService为例子，下同）

2、dubbo是基于spring的，dubbo提供的服务在spring看来就是一个bean，叫做ServiceBean，而ServiceBean实现了Spring的InitializingBean、ApplicationContextAware、ApplicationListener等接口，所以当spring启动完成之后，ServiceBean实际上就已经被加载到spring容器中了，（此时DemoService已经作为一个bean存在spring容器中，但是还没有注册到注册中心，也没有暴露给外部，只是作为一个最基本的bean的存在），所以ServiceBean还监听了applicationContext启动完成的事件，执行额外的操作。

3、当spring容器启动完成之后，ServiceBean就需要将自己暴露给外部并且注册到注册中心了，这一步是ServiceBean的export方法中执行，也可以叫做导出方法

4、首先加载provider端dubbo端配置，如应用名称、注册中心地址、通信协议、端口号等基本信息，并且对这些配置进行读取获取是设置默认值，并且根据不同配置进行不同处理，如是否延迟加载等

5、所有配置信息加载完毕，就将这些配置信息进行组装到一起，封装成了一个URL对象，一个URL对象就包含了一个服务所有的信息，包含了接口到方法名称、参数列表、dubbo的配置信息等

6、现在DemoService被封装成了一个URL，那么就可以进行暴露出去了，暴露出去的意思实际就是告诉别人需要以什么样的协议来调用，所以服务的暴露针对不同的协议暴露的方式也不同。

7、Protocol接口提供了暴露服务和引入服务两个方法，不同的协议实现就需要实现这个接口来进行暴露服务，默认是dubbo协议，所以就通过DubboProtocol实现类来进行服务的暴露

8、暴露的过程实际就是将服务存入一个全局的Map中，key就是以URL为基础创建的唯一key，value就是这个服务

9、下一步，既然是远程调用，那么消费者就需要连接提供者，提供者这边就需要开启一个端口等待消费者的连接

10、dubbo默认采用的是Netty框架，所以在暴露服务的时候就会根据服务配置的端口号启动Netty服务器，并且以host+port为key，NettyServer为value存入Map中（这样做的好处是不同服务可以通过不同的Netty服务器处理）

 

到这里服务的暴露过程基本上走完，实际上就是将服务封装成一个对象存入全局Map中，然后启动一个Netty服务器监听消费者的消费。

实现细节：

当服务被消费，那就需要被执行，如DemoService的一个方法被消费，那么这个方法最终是需要被执行的。那么如何被执行呢？两种思路：一种是服务暴露的时候定义一个执行器，可以执行DemoService的实现类的方法；一种是服务被消费的时候再来执行。很显然第一种方式跟靠谱，因为这样就可以在服务暴露的时候就提前知道了服务的方法该如何执行，具体执行的时候传入不同的参数就好了。dubbo也是这么干的，所以这里就涉及到了一个接口叫做Invoker。

Invoker是dubbo很核心的一个概念，首先不关心它是如何实现的，首先得了解它能干嘛。可以理解为Invoker就是一个执行体，一个Invoker对应一个接口，这个接口的方法就可以通过Invoker来执行，如DemoService的所有方法都可以通过这个Invoker来执行。

而Invoker就是在服务暴露的时候创建的，就是步骤5中的创建的URL对象来创建的，而步骤8中暴露服务的时候实际也就是将invoker对象作为参数进行暴露，暴露成功之后会再封装出一个Exporter对象。因为服务可以被暴露也可以取消暴露，Exporter对象中就包含了Invoker对象以及暴露的状态。所以第8步中最终存入全局Map的就是这个Exporter对象。

 

再捋一捋：服务暴露需要根据不同的协议去暴露，所以需要执行不同协议对象procotol实现类，每个procotol中有一个Map，key为服务的唯一标识，value为Exporter对象；Exporter对象可以调用getInvoker()得到这个服务的Invoker对象，得到了这个Invoker对象就可以执行具体服务的方法了。至于Invoker具体怎么执行方法下文和消费者的消费过程一同分析

 

二、consumer引入服务
1、消费者的启动过程和提供者大致差不多，只不过消费者的bean叫做ReferenceBean，也是在spring启动的时候进行加载初始化

2、当需要消费服务时，首先的从容器中获取bean也就是执行getObject()得到，此时就会在getObject方法中执行init()方法去引入服务

3、执行init方法时首先也是进行dubbo配置的读取和加载等，并且将一切配置信息整合到一个map中（提供者也是先放入map然后封装成URL对象）

4、然后根据map中的配置信息，执行createProxy方法来返回一个服务的实例，如DemoService的实现类实例 demoService，那么这个demoService就可以直接执行DemoService接口中的方法了。

5、前四步好理解，就是根据配置参数获取了接口的实现类对象，然后就可以去执行方法了。那么现在的重点就是这个实现类是如何生成的，也就是createProxy方法是如何实现的

6、回顾提供者暴露服务的过程可以知道一个invoker是一个执行体，暴露服务的时候通过Procotol接口的方法将Invoker暴露出去，那么消费者可以根据Procotol接口获取Invoker对象么？答案是肯定的。

7、消费者根据配置信息的map也创建了URL对象，然后通过Procotol的refer方法可以获取到一个Invoker对象，这个Invoker对象就是可以执行服务的方法的执行体

8、invoker对象的创建过程会和服务提供者进行连接，以netty为例子就是创建了Netty的客户端和提供者那边的Netty服务端进行连接，然后得到的连接对象和服务信息共同构造出了invoker对象

9、而消费者不能直接使用Invoker，因为不能使用DemoService service = invoker, 所以需要将invoker转化成接口的实现类对象。

10、这里就采用了代理模式，通过字节码生成技术，根据invoker对象动态生成了一个服务的实现类，那么这个实现类执行具体方法的时候实际就是通过invoker来执行了。

 

总结：服务引入的过程实际就是作为一个客户端，创建了和服务器的一个连接，得到了一个invoker对象，并通过invoker对象动态代理的方式得到服务的实现类，实现类的方法执行实际就是通过invoker来执行的。

 

三、consumer消费服务的执行过程


上面提供者和消费者两端都提到Invoker对象，所以实际的操作都是通过Invoker来执行的。

显然消费端的Invoker实际工作内容实际就是将执行的方法转化成字节流的形式通过网络通信发送给服务端，当然首先需要进行序列化，默认采用netty通信

服务端接收到字节流信息首先是进行解码，然后到的请求的具体参数，根据请求内容就可以得到是哪一个Exporter对象，然后就知道是哪一个Invoker对象，那么就可以通过执行这个invoker的方法进行方法的具体执行。

 

总结：

1、消费端执行DemoService service的具体方法，实际是执行代理实现类的对应方法，代理类的方法执行实际是通过invoker来执行，invoker执行的时候实际就是通过网络通信将请求发送给服务端

2、服务端接收消息进行解码，得到请求的信息，根据请求信息得到Exporter对象，根据Exporter对象可以获取Invoker对象，然后通过Invoker来执行具体的方法（这个Invoker和消费端的Invoker不是同一个实现）