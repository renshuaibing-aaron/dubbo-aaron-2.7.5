package org.apache.dubbo.config;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.bytecode.Wrapper;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.*;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.config.event.ReferenceConfigDestroyedEvent;
import org.apache.dubbo.config.event.ReferenceConfigInitializedEvent;
import org.apache.dubbo.config.utils.ConfigValidationUtils;
import org.apache.dubbo.event.Event;
import org.apache.dubbo.event.EventDispatcher;
import org.apache.dubbo.metadata.WritableMetadataService;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.directory.StaticDirectory;
import org.apache.dubbo.rpc.cluster.support.ClusterUtils;
import org.apache.dubbo.rpc.cluster.support.registry.ZoneAwareCluster;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ConsumerModel;
import org.apache.dubbo.rpc.model.ServiceDescriptor;
import org.apache.dubbo.rpc.model.ServiceRepository;
import org.apache.dubbo.rpc.protocol.injvm.InjvmProtocol;
import org.apache.dubbo.rpc.service.GenericService;
import org.apache.dubbo.rpc.support.ProtocolUtils;

import java.util.*;

import static org.apache.dubbo.common.constants.CommonConstants.*;
import static org.apache.dubbo.common.utils.NetUtils.isInvalidLocalHost;
import static org.apache.dubbo.config.Constants.DUBBO_IP_TO_REGISTRY;
import static org.apache.dubbo.registry.Constants.CONSUMER_PROTOCOL;
import static org.apache.dubbo.registry.Constants.REGISTER_IP_KEY;
import static org.apache.dubbo.rpc.Constants.LOCAL_PROTOCOL;
import static org.apache.dubbo.rpc.cluster.Constants.REFER_KEY;

/**
 * Please avoid using this class for any new application,
 * use {@link ReferenceConfigBase} instead.
 */
public class ReferenceConfig<T> extends ReferenceConfigBase<T> {

    public static final Logger logger = LoggerFactory.getLogger(ReferenceConfig.class);

    /**
     * The {@link Protocol} implementation with adaptive functionality,it will be different in different scenarios.
     * A particular {@link Protocol} implementation is determined by the protocol attribute in the {@link URL}.
     * For example:
     *
     * <li>when the url is registry://224.5.6.7:1234/org.apache.dubbo.registry.RegistryService?application=dubbo-sample,
     * then the protocol is <b>RegistryProtocol</b></li>
     *
     * <li>when the url is dubbo://224.5.6.7:1234/org.apache.dubbo.config.api.DemoService?application=dubbo-sample, then
     * the protocol is <b>DubboProtocol</b></li>
     * <p>
     * Actually，when the {@link ExtensionLoader} init the {@link Protocol} instants,it will automatically wraps two
     * layers, and eventually will get a <b>ProtocolFilterWrapper</b> or <b>ProtocolListenerWrapper</b>
     */
    private static final Protocol REF_PROTOCOL = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

    /**
     * The {@link Cluster}'s implementation with adaptive functionality, and actually it will get a {@link Cluster}'s
     * specific implementation who is wrapped with <b>MockClusterInvoker</b>
     */
    private static final Cluster CLUSTER = ExtensionLoader.getExtensionLoader(Cluster.class).getAdaptiveExtension();

    /**
     * A {@link ProxyFactory} implementation that will generate a reference service's proxy,the JavassistProxyFactory is
     * its default implementation
     */
    private static final ProxyFactory PROXY_FACTORY = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

    /**
     * The interface proxy reference
     */
    private transient volatile T ref;

    /**
     * The invoker of the reference service
     */
    private transient volatile Invoker<?> invoker;

    /**
     * The flag whether the ReferenceConfig has been initialized
     */
    private transient volatile boolean initialized;

    /**
     * whether this ReferenceConfig has been destroyed
     */
    private transient volatile boolean destroyed;

    private DubboBootstrap bootstrap;

    public ReferenceConfig() {
    }

    public ReferenceConfig(Reference reference) {
        super(reference);
    }

    @Override
    public synchronized T get() {

        // 如果被销毁，则抛出异常
        if (destroyed) {
            throw new IllegalStateException("The invoker of ReferenceConfig(" + url + ") has already destroyed!");
        }

        // 检测 代理对象ref 是否为空，为空则通过 init 方法创建
        if (ref == null) {
            // 用于处理配置，以及调用 createProxy 生成代理类
            init();
        }
          return ref;
    }

    @Override
    public synchronized void destroy() {

        System.out.println("=====destroydestroydestroydestroy===============");
        if (ref == null) {
            return;
        }
        if (destroyed) {
            return;
        }
        destroyed = true;
        try {
            invoker.destroy();
        } catch (Throwable t) {
            logger.warn("Unexpected error occured when destroy invoker of ReferenceConfig(" + url + ").", t);
        }
        invoker = null;
        ref = null;

        // dispatch a ReferenceConfigDestroyedEvent since 2.7.4
        dispatch(new ReferenceConfigDestroyedEvent(this));
    }

    /**
     * 配置加载
     * 1）、收集各种配置信息，并将配置存储到 map 中
     * 2）、主要用于解析服务消费者 ip
     * 3）、创建接口代理类
     * 4）、根据服务名构建 ConsumerModel，并将 ConsumerModel 存入到 ApplicationModel 的消费者集合map中
     */
    public synchronized void init() {

        System.out.println("************初始化******************");
        // 如果已经初始化过，则结束
        if (initialized) {
            return;
        }

        if (bootstrap == null) {
            bootstrap = DubboBootstrap.getInstance();
            bootstrap.init();
        }
        // 检查本地存根配置合法性，主要检查本地存根类是否实现了接口类，以及检查带有参数who类型的本地存根类构造函数是否是接口类类型
        checkAndUpdateSubConfigs();

        //init serivceMetadata
        serviceMetadata.setVersion(version);
        serviceMetadata.setGroup(group);
        serviceMetadata.setDefaultGroup(group);
        serviceMetadata.setServiceType(getActualInterface());
        serviceMetadata.setServiceInterfaceName(interfaceName);
        // TODO, uncomment this line once service key is unified
        serviceMetadata.setServiceKey(URL.buildKey(interfaceName, group, version));

        // 本地存根合法性校验
        checkStubAndLocal(interfaceClass);
        // mock合法性校验
        ConfigValidationUtils.checkMock(interfaceClass, this);

        // 用来存放配置 添加side、dubbo version、pid等参数到map中
        Map<String, String> map = new HashMap();
        // 存放这是消费者侧
        map.put(SIDE_KEY, CONSUMER_SIDE);
        //添加 协议版本、发布版本，时间戳 等信息到 map 中
        ReferenceConfigBase.appendRuntimeParameters(map);

        //如果是泛化调用
        if (!ProtocolUtils.isGeneric(generic)) {
            // 获得版本号
            String revision = Version.getVersion(interfaceClass, version);
            if (revision != null && revision.length() > 0) {
                // 设置版本号
                map.put(REVISION_KEY, revision);
            }
            // 获得所有方法
            String[] methods = Wrapper.getWrapper(interfaceClass).getMethodNames();
            if (methods.length == 0) {
                logger.warn("No method found in service interface " + interfaceClass.getName());
                map.put(METHODS_KEY, ANY_VALUE);
            } else {
                // 把所有方法签名拼接起来放入map
                map.put(METHODS_KEY, StringUtils.join(new HashSet<String>(Arrays.asList(methods)), COMMA_SEPARATOR));
            }
        }
        // 加入服务接口名称
        map.put(INTERFACE_KEY, interfaceName);

        // 添加metrics、application、module、consumer、protocol的所有信息到map
        AbstractConfig.appendParameters(map, metrics);
        AbstractConfig.appendParameters(map, application);
        AbstractConfig.appendParameters(map, module);
        // remove 'default.' prefix for configs from ConsumerConfig
        // appendParameters(map, consumer, Constants.DEFAULT_KEY);
        AbstractConfig.appendParameters(map, consumer);
        AbstractConfig.appendParameters(map, this);
        Map<String, Object> attributes = null;


        if (CollectionUtils.isNotEmpty(getMethods())) {
            attributes = new HashMap<>();

            // 遍历方法配置
            for (MethodConfig methodConfig : getMethods()) {

                // 把方法配置加入map
                AbstractConfig.appendParameters(map, methodConfig, methodConfig.getName());

                // 生成重试的配置key
                String retryKey = methodConfig.getName() + ".retry";
                // 如果map中已经有该配置，则移除该配置
                if (map.containsKey(retryKey)) {
                    String retryValue = map.remove(retryKey);
                    // 如果配置为false，也就是不重试，则设置重试次数为0次
                    if ("false".equals(retryValue)) {
                        map.put(methodConfig.getName() + ".retries", "0");
                    }
                }
                ConsumerModel.AsyncMethodInfo asyncMethodInfo = AbstractConfig.convertMethodConfig2AsyncInfo(methodConfig);
                if (asyncMethodInfo != null) {
                    //                    consumerModel.getMethodModel(methodConfig.getName()).addAttribute(ASYNC_KEY, asyncMethodInfo);
                    // 设置异步配置
                    attributes.put(methodConfig.getName(), asyncMethodInfo);
                }
            }
        }

        // 获取服务消费者 ip 地址
        String hostToRegistry = ConfigUtils.getSystemProperty(DUBBO_IP_TO_REGISTRY);
        // 如果为空，则获取本地ip
        if (StringUtils.isEmpty(hostToRegistry)) {
            hostToRegistry = NetUtils.getLocalHost();
        } else if (isInvalidLocalHost(hostToRegistry)) {
            throw new IllegalArgumentException("Specified invalid registry ip from property:" + DUBBO_IP_TO_REGISTRY + ", value:" + hostToRegistry);
        }
        // 设置消费者ip // 添加服务消费者ip参数到map
        map.put(REGISTER_IP_KEY, hostToRegistry);

        serviceMetadata.getAttachments().putAll(map);

        ServiceRepository repository = ApplicationModel.getServiceRepository();
        ServiceDescriptor serviceDescriptor = repository.registerService(interfaceClass);
        repository.registerConsumer(
                serviceMetadata.getServiceKey(),
                attributes,
                serviceDescriptor,
                this,
                null,
                serviceMetadata);

        // 创建接口代理类
        //{side=consumer, register.ip=172.19.5.49, release=, methods=sayHello,sayHelloAsync,
        // lazy=false, sticky=false, dubbo=2.0.2, pid=17284,
        // interface=org.apache.dubbo.demo.DemoService, timestamp=1575769816204}
        ref = createProxy(map);


        // 获取消费者服务名,例如：org.apache.dubbo.demo.DemoService
        serviceMetadata.setTarget(ref);
        serviceMetadata.addAttribute(PROXY_CLASS_REF, ref);
        String serviceKey = serviceMetadata.getServiceKey();
        // 根据服务名，ReferenceConfig，代理类构建 ConsumerModel，并将 ConsumerModel 存入到 ApplicationModel 的消费者集合map中
        repository.lookupReferredService(serviceKey).setProxyObject(ref);


        // 设置初始化标志为true
        initialized = true;

        // dispatch a ReferenceConfigInitializedEvent since 2.7.4
        dispatch(new ReferenceConfigInitializedEvent(this, invoker));
    }

    /**
     * 引用服务
     *1.首先 ReferenceConfig 类的 init 方法调用 Protocol#refer 方法生成 Invoker 实例，这是服务消费的关键。
     * 2.然后使用 JavassistProxyFactory#getProxy 生成接口（DemoService）的代理对象 ref
     *1）、若是本地服务调用，则调用 InjvmProtocol 的 refer 方法生成 InjvmInvoker 实例
     * 2）、配置了url参数，则将所有 url 做设置 path 或者 设置 refer 或者合并处理，最后添加到 urls 列表中
     * 3）、没有配置url参数时，从 registries 参数中加载注册中心链接 url，url 做设置 refer 处理后添加到 urls 列表中
     * 4）、urls 元素为1个时，直接调用 RegistryProtocol 的 refer 构建 Invoker 实例
     * 5）、urls 元素大于1时，根据 url 协议头加载指定的 Protocol 实例，并调用实例的 refer 方法构建 Invoker 实例，最后通过 Cluster 合并多个 Invoker
     * 6）、保存消费者的Metadata数据
     * 7）、 调用 ProxyFactory 的 getproxy 方法创建服务代理类
     * @param map
     * @return
     */
    @SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
    private T createProxy(Map<String, String> map) {
        //{side=consumer, register.ip=172.19.5.49, release=, methods=sayHello,sayHelloAsync,
        // lazy=false, sticky=false, dubbo=2.0.2, pid=17284,
        // interface=org.apache.dubbo.demo.DemoService, timestamp=1575769816204}


        // 根据配置检查是否为本地调用
        if (shouldJvmRefer(map)) {
            // 生成本地引用 URL，协议为 injvm，ip 为 127.0.0.1，port 为 0
            URL url = new URL(LOCAL_PROTOCOL, LOCALHOST_VALUE, 0, interfaceClass.getName()).addParameters(map);

            // 先调用 AbstractProtocol 的 refer 方法，然后调用 InjvmProtocol 的 protocolBindingRefer 方法构建 InjvmInvoker 实例
            invoker = REF_PROTOCOL.refer(interfaceClass, url);
            if (logger.isInfoEnabled()) {
                logger.info("Using injvm service " + interfaceClass.getName());
            }
        } else {
            // 远程服务引用
            urls.clear();
            if (url != null && url.length() > 0) {
                // 如果url不为空，则用户可能想进行直连来调用
                // user specified URL, could be peer-to-peer address, or register center's address.
                // 当需要配置多个 url 时，可用分号进行分割，这里会进行切分
                String[] us = SEMICOLON_SPLIT_PATTERN.split(url);

                // 遍历所有的url
                if (us != null && us.length > 0) {
                    for (String u : us) {
                        URL url = URL.valueOf(u);
                        if (StringUtils.isEmpty(url.getPath())) {
                            // 设置接口全限定名为 url 路径
                            // 设置接口全限定名为 url 路径，例如：org.apache.dubbo.demo.DemoService
                            url = url.setPath(interfaceName);
                        }
                        // 检测 url 协议是否为 registry，若是，表明用户想使用指定的注册中心
                        if (UrlUtils.isRegistry(url)) {
                            // 将 map 转换为查询字符串，并作为 refer 参数的值添加到 url 中
                            urls.add(url.addParameterAndEncoded(REFER_KEY, StringUtils.toQueryString(map)));
                        } else {
                            // 合并 url，移除服务提供者的一些配置（这些配置来源于用户配置的 url 属性），
                            // 比如线程池相关配置。并保留服务提供者的部分配置，比如版本，group，时间戳等
                            // 最后将合并后的配置设置为 url 查询字符串中。
                            urls.add(ClusterUtils.mergeUrl(url, map));
                        }
                    }
                }
            } else { // assemble URL from register center's configuration
                // if protocols not injvm checkRegistry
                // protocols 非 injvm时，即
                if (!LOCAL_PROTOCOL.equalsIgnoreCase(getProtocol())) {
                    // 校验注册中心
                    // 检查 RegistryConfig 配置是否存在，并且将 registries 和 registryIds 转换为 RegistryConfig 对象
                    checkRegistry();

                    // 加载注册中心的url
                    List<URL> us = ConfigValidationUtils.loadRegistries(this, false);
                    // us里面只有一个 registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?
                    // application=dubbo-demo-api-consumer&dubbo=2.0.2&pid=20412&registry=zookeeper&timestamp=1575770099463
                    if (CollectionUtils.isNotEmpty(us)) {

                        // 遍历所有的注册中心
                        for (URL u : us) {
                            // 生成监控url
                            URL monitorUrl = ConfigValidationUtils.loadMonitor(this, u);
                            if (monitorUrl != null) {
                                // 加入监控中心url的配置
                                map.put(MONITOR_KEY, URL.encode(monitorUrl.toFullString()));
                            }
                            // 添加 refer 参数到 url 中，并将 url 添加到 urls 中
                            urls.add(u.addParameterAndEncoded(REFER_KEY, StringUtils.toQueryString(map)));
                        }
                    }
                    // 如果urls为空，则抛出异常
                    if (urls.isEmpty()) {
                        throw new IllegalStateException("No such any registry to reference " + interfaceName + " on the consumer " + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion() + ", please config <dubbo:registry address=\"...\" /> to your spring config.");
                    }
                }
            }

            // 如果只有一个注册中心，则直接调用refer方法
            // 单个注册中心或服务提供者->服务直连
            if (urls.size() == 1) {
                // 调用 RegistryProtocol 的 refer 构建 Invoker 实例
                Protocol refProtocol = REF_PROTOCOL;
                //服务引用第一步，有注册中心的情况下（最常用）会调用  RegistryProtocol#refer(Class<T> type, URL url) ，
                // RegistryProtocol 实际上是其他具体 Protocol（eg. DubboProtocol）的 AOP 类

                //registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?
                // application=dubbo-demo-api-consumer&dubbo=2.0.2&pid=22144&refer=dubbo%3D2.0.2%26interface%3Dorg.apache.dubbo.demo.DemoService%26lazy%3Dfalse%26methods%3DsayHello%2CsayHelloAsync%26pid%3D22144%26register.ip%3D172.19.5.49%26side%3Dconsumer%26sticky%3Dfalse%26timestamp%3D1575721905133&registry=zookeeper&timestamp=1575721905204

                //extName="registry"。
                //我们debug调试看调用栈可以看出整个调用过程，createProxy（ReferenceConfig） -> ref（ProtocolListenerWrapper）->
                // ref（ProtocolFilterWrapper）-> ref（RegistryProtocol），最终调用到了 RegistryProtocol 的 ref 方法
                invoker = refProtocol.refer(interfaceClass, urls.get(0));
            } else {
                // 存在多个注册中心或者服务提供者
                List<Invoker<?>> invokers = new ArrayList<Invoker<?>>();
                URL registryURL = null;
                // 遍历所有的注册中心url
                // 根据url获取所有的invoke
                for (URL url : urls) {

                    // 通过 refprotocol 调用 refer 构建 Invoker，
                    // refprotocol 会在运行时根据 url 协议头加载指定的 Protocol 实例，并调用实例的 refer 方法
                    // 把生成的Invoker加入到集合中
                    invokers.add(REF_PROTOCOL.refer(interfaceClass, url));

                    // 如果是注册中心的协议
                    if (UrlUtils.isRegistry(url)) {
                        // 则设置registryURL
                        registryURL = url; // use last registry url
                    }
                }
                // 优先用注册中心的url
                if (registryURL != null) { // registry url is available
                    // for multi-subscription scenario, use 'zone-aware' policy by default
                    // 只有当注册中心当链接可用当时候，采用RegistryAwareCluster
                    URL u = registryURL.addParameterIfAbsent(CLUSTER_KEY, ZoneAwareCluster.NAME);
                    // The invoker wrap relation would be like: ZoneAwareClusterInvoker(StaticDirectory) -> FailoverClusterInvoker(RegistryDirectory, routing happens here) -> Invoker

                    // 由集群进行多个invoker合并
                    invoker = CLUSTER.join(new StaticDirectory(u, invokers));
                } else { // not a registry url, must be direct invoke.

                    // 直接进行合并
                    invoker = CLUSTER.join(new StaticDirectory(invokers));
                }
            }
        }
        // 检查invoker是否可用，即提供者服务是否可用
        if (shouldCheck() && !invoker.isAvailable()) {
            throw new IllegalStateException("Failed to check the status of the service "
                    + interfaceName
                    + ". No provider available for the service "
                    + (group == null ? "" : group + "/")
                    + interfaceName +
                    (version == null ? "" : ":" + version)
                    + " from the url "
                    + invoker.getUrl()
                    + " to the consumer "
                    + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion());
        }
        if (logger.isInfoEnabled()) {
            logger.info("Refer dubbo service " + interfaceClass.getName() + " from url " + invoker.getUrl());
        }
        /**
         * @since 2.7.0
         * ServiceData Store
         */
        // 元数据中心服务
        String metadata = map.get(METADATA_KEY);
        WritableMetadataService metadataService = WritableMetadataService.getExtension(metadata == null ? DEFAULT_METADATA_STORAGE_TYPE : metadata);
        // 加载元数据服务，如果成功
        if (metadataService != null) {
            // 生成url
            URL consumerURL = new URL(CONSUMER_PROTOCOL, map.remove(REGISTER_IP_KEY), 0, map.get(INTERFACE_KEY), map);
            // 把消费者配置加入到元数据中心中
            metadataService.publishServiceDefinition(consumerURL);
        }


        // create service proxy
        // 创建服务代理  ProxyFactory$Adaptive
        //getProxy内部最终得到是一个被StubProxyFactoryWrapper包装后的JavassistProxyFactory
        //最终得到的demoService是一个proxy0实例
        return (T) PROXY_FACTORY.getProxy(invoker);
    }

    /**
     * This method should be called right after the creation of this class's instance, before any property in other config modules is used.
     * Check each config modules are created properly and override their properties if necessary.
     */
    public void checkAndUpdateSubConfigs() {

        // 检测接口名合法性
        if (StringUtils.isEmpty(interfaceName)) {
            throw new IllegalStateException("<dubbo:reference interface=\"\" /> interface not allow null!");
        }
        // 完成application、module、registries、monitor配置信息填充
        completeCompoundConfigs();
        // get consumer's global configuration
        checkDefault();
        this.refresh();
        if (getGeneric() == null && getConsumer() != null) {
            setGeneric(getConsumer().getGeneric());
        }
        if (ProtocolUtils.isGeneric(generic)) {
            interfaceClass = GenericService.class;
        } else {
            try {
                interfaceClass = Class.forName(interfaceName, true, Thread.currentThread()
                        .getContextClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            checkInterfaceAndMethods(interfaceClass, getMethods());
        }
        // 解析文件，获取接口名对应的配置信息赋值给 url 属性
        resolveFile();
        ConfigValidationUtils.validateReferenceConfig(this);
        // 检查 metadataReport配置信息
        appendParameters();
    }


    /**
     * Figure out should refer the service in the same JVM from configurations. The default behavior is true
     * 1. if injvm is specified, then use it
     * 2. then if a url is specified, then assume it's a remote call
     * 3. otherwise, check scope parameter
     * 4. if scope is not specified but the target service is provided in the same JVM, then prefer to make the local
     * call, which is the default behavior
     */
    protected boolean shouldJvmRefer(Map<String, String> map) {
        URL tmpUrl = new URL("temp", "localhost", 0, map);
        boolean isJvmRefer;
        if (isInjvm() == null) {
            // if a url is specified, don't do local reference
            if (url != null && url.length() > 0) {
                isJvmRefer = false;
            } else {
                // by default, reference local service if there is
                isJvmRefer = InjvmProtocol.getInjvmProtocol().isInjvmRefer(tmpUrl);
            }
        } else {
            isJvmRefer = isInjvm();
        }
        return isJvmRefer;
    }

    /**
     * Dispatch an {@link Event event}
     *
     * @param event an {@link Event event}
     * @since 2.7.5
     */
    protected void dispatch(Event event) {
        EventDispatcher.getDefaultExtension().dispatch(event);
    }

    public DubboBootstrap getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(DubboBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @SuppressWarnings("unused")
    private final Object finalizerGuardian = new Object() {
        @Override
        protected void finalize() throws Throwable {
            super.finalize();

            if (!ReferenceConfig.this.destroyed) {
                logger.warn("ReferenceConfig(" + url + ") is not DESTROYED when FINALIZE");

                /* don't destroy for now
                try {
                    ReferenceConfig.this.destroy();
                } catch (Throwable t) {
                        logger.warn("Unexpected err when destroy invoker of ReferenceConfig(" + url + ") in finalize method!", t);
                }
                */
            }
        }
    };

    public void appendParameters() {
        URL appendParametersUrl = URL.valueOf("appendParameters://");
        List<AppendParametersComponent> appendParametersComponents = ExtensionLoader.getExtensionLoader(AppendParametersComponent.class).getActivateExtension(appendParametersUrl, (String[]) null);
        appendParametersComponents.forEach(component -> component.appendReferParameters(this));
    }

    // just for test
    Invoker<?> getInvoker() {
        return invoker;
    }
}
