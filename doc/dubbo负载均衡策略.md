https://blog.csdn.net/wueryan/article/details/91129212

dubbo的负载均衡 其实是在客户端实现的 客户端服务器启动后 会进行服务引用 
其实获取的是一个代理类 proxy0

当然这个是有spring 生成的  
然后执行这个代理类 里面的invoke方法 在执行过程中 

会执行一系列的功能 比如过滤器 路由 还有就是负载均衡 

因为很多的invoker 会进行合成一个集群模式的invoker  对这个集群模式的invoker进行利用负载均衡算法 找出一个
合适的invoker  最后执行 远程调用
(负载均衡在客户端实现)
1.了解dubbo的四种负载均衡策略以及代码实现

基于加权的随机算法
基于最少活跃调用数的算法
基于hash一致性的算法
基于加权轮训的算法