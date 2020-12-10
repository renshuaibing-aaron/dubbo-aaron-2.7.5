package org.apache.dubbo.rpc;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

import java.util.Collections;
import java.util.List;

/**
 * Protocol. (API/SPI, Singleton, ThreadSafe)
 * @SPI：指定一个接口为SPI接口（可扩展接口）
 */
@SPI("dubbo")
public interface Protocol {

    /**
     * 获取缺省端口，当用户没有配置端口时使用。
     * Get default port when user doesn't config the port.
     *
     * @return default port 缺省端口
     */
    int getDefaultPort();



    /**
     *
     * todo
     *  暴露远程服务：<br>
     *  1. 协议在接收请求时，应记录请求来源方地址信息：RpcContext.getContext().setRemoteAddress();<br>
     *  2. export()必须是幂等的，也就是暴露同一个URL的Invoker两次，和暴露一次没有区别。<br>
     *  3. export()传入的Invoker由框架实现并传入，协议不需要关心。<br>
     * Export service for remote invocation: <br>
     * 1. Protocol should record request source address after receive a request:
     * RpcContext.getContext().setRemoteAddress();<br>
     * 2. export() must be idempotent, that is, there's no difference between invoking once and invoking twice when
     * export the same URL<br>
     * 3. Invoker instance is passed in by the framework, protocol needs not to care <br>
     *
     * @param <T>     Service type  服务的类型
     * @param invoker Service invoker  服务的执行体
     * @return exporter reference for exported service, useful for unexport the service later 暴露服务的引用，用于取消暴露
     * @throws RpcException thrown when error occurs during export the service, for example: port is occupied  当暴露服务出错时抛出，比如端口已占用
     *
     * 在export方法上有@Adaptive注解，这个注解写在方法上，有个作用，
     * 可以根据传入的URL来指定需要的协议，上面的URL中指名了是registry，所以他使用的应该是registryProtocol协议
     * @Adaptive：该注解可以注解在两个地方：
     *
     * 接口上：在 Dubbo 中，仅有 AdaptiveExtensionFactory 和 AdaptiveCompiler；
     * 接口的方法上：会动态生成相应的动态类（实际上是一个工厂类，工厂设计模式），例如 Protocol$Adapter
     */
    @Adaptive
    <T> Exporter<T> export(Invoker<T> invoker) throws RpcException;





    /**
     *
     * todo
     *  引用远程服务：<br>
     *   1. 当用户调用refer()所返回的Invoker对象的invoke()方法时，协议需相应执行同URL远端export()传入的Invoker对象的invoke()方法。<br>
     *   2. refer()返回的Invoker由协议实现，协议通常需要在此Invoker中发送远程请求。<br>
     *   3. 当url中有设置check=false时，连接失败不能抛出异常，并内部自动恢复。<br>
     * Refer a remote service: <br>
     * 1. When user calls `invoke()` method of `Invoker` object which's returned from `refer()` call, the protocol
     * needs to correspondingly execute `invoke()` method of `Invoker` object <br>
     * 2. It's protocol's responsibility to implement `Invoker` which's returned from `refer()`. Generally speaking,
     * protocol sends remote request in the `Invoker` implementation. <br>
     * 3. When there's check=false set in URL, the implementation must not throw exception but try to recover when
     * connection fails.
     *
     * @param <T>  Service type  服务的类型
     * @param type Service class  服务的类型
     * @param url  URL address for the remote service  远程服务的URL地址
     * @return invoker service's local proxy  服务的本地代理
     * @throws RpcException when there's any error while connecting to the service provider  当连接服务提供方失败时抛出
     */
    @Adaptive
    <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException;






    /**
     * todo
     *  释放协议：<br>
     *  1. 取消该协议所有已经暴露和引用的服务。<br>
     *  2. 释放协议所占用的所有资源，比如连接和端口。<br>
     *  3. 协议在释放后，依然能暴露和引用新的服务。<br>
     * Destroy protocol: <br>
     * 1. Cancel all services this protocol exports and refers <br>
     * 2. Release all occupied resources, for example: connection, port, etc. <br>
     * 3. Protocol can continue to export and refer new service even after it's destroyed.
     */
    void destroy();

    /**
     * Get all servers serving this protocol
     *
     * @return
     */
    default List<ProtocolServer> getServers() {
        return Collections.emptyList();
    }

}