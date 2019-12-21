/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.common.extension;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.context.Lifecycle;
import org.apache.dubbo.common.extension.support.ActivateComparator;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static org.apache.dubbo.common.constants.CommonConstants.*;

/**
 *
 * {@link org.apache.dubbo.rpc.model.ApplicationModel}, {@code DubboBootstrap} and this class are
 * at present designed to be singleton or static (by itself totally static or uses some static fields).
 * So the instances returned from them are of process or classloader scope. If you want to support
 * multiple dubbo servers in a single process, you may need to refactor these three classes.
 *
 * Load dubbo extensions
 * <ul>
 * <li>auto inject dependency extension </li>
 * <li>auto wrap extension in wrapper </li>
 * <li>default extension is an adaptive instance</li>
 * </ul>
 *
 * @see <a href="http://java.sun.com/j2se/1.5.0/docs/guide/jar/jar.html#Service%20Provider">Service Provider in Java 5</a>
 * @see org.apache.dubbo.common.extension.SPI
 * @see org.apache.dubbo.common.extension.Adaptive
 * @see org.apache.dubbo.common.extension.Activate
 * 可以类比为 JDK SPI 中的 ServiceLoader
 */
public class ExtensionLoader<T> {


    private static final Logger logger = LoggerFactory.getLogger(ExtensionLoader.class);

    /**
     * 类变量（全局变量），也可以做成一个 ExtensionLoaderFactory 来专门存储这些类变量
     */
    /** 存放SPI文件的三个目录 */
    /** 1. META-INF/services/ 是jdk的SPI文件的存放目录 */
    private static final String SERVICES_DIRECTORY = "META-INF/services/";

    /** 2. META-INF/dubbo/ 是第三方SPI文件的存放目录 */
    private static final String DUBBO_DIRECTORY = "META-INF/dubbo/";

    /** 3. META-INF/dubbo/internal/ 是Dubbo内部SPI文件的存放目录 */
    private static final String DUBBO_INTERNAL_DIRECTORY = DUBBO_DIRECTORY + "internal/";

    private static final Pattern NAME_SEPARATOR = Pattern.compile("\\s*[,]+\\s*");


    //缓存
    //每个可扩展接口的扩展实现类和实现实例的都管理通过是ExtensionLoader进行，每个接口维护一个单例的ExtensionLoader，
    // 所有可扩展接口的实现都维护在ExtensionLoader中
    /** key: SPI接口Class value: 该接口的ExtensionLoader, eg. "interface com.alibaba.dubbo.rpc.Protocol" -> "com.alibaba.dubbo.common.extension.ExtensionLoader[com.alibaba.dubbo.rpc.Protocol]" */
    //在 SOFARPC 中，是有一个 ExtensionLoaderFactory 类来存储 Map<Class, ExtensionLoader> 变量的
    private static final ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>();



    /** key: SPI接口Class value: SPI实现类的对象实例 */
    private static final ConcurrentMap<Class<?>, Object> EXTENSION_INSTANCES = new ConcurrentHashMap<>();


    /**
     * 以下都是实例变量
     */

    /** SPI接口Class，eg. Protocol */
    private final Class<?> type;
    /** 用于 Dubbo IOC 注入实例的创建 */
    private final ExtensionFactory objectFactory;

    /** key: ExtensionClass的Class value: SPI实现类的key */
    private final ConcurrentMap<Class<?>, String> cachedNames = new ConcurrentHashMap<>();

    /** 存储 type 的所有普通实现类的 spi key-valueClass: Map<String, Class<?>> getExtensionClasses() */
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<>();

    /** 如果类上有 @Activate 注解，则缓存到 Map<String, Activate> cachedActivates 中，key=spi key，value=注解 */
    private final Map<String, Object> cachedActivates = new ConcurrentHashMap<>();

    /** 缓存创建好的extensionClass实例 */
    private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();

    /** 缓存创建好的适配类实例 */
    private final Holder<Object> cachedAdaptiveInstance = new Holder<>();

    /** 存储类上带有 @Adaptive 注解的Class，如果没有，则自动生成一个适配类 */
    private volatile Class<?> cachedAdaptiveClass = null;

    /** 如果扩展接口（eg. Protocol）有 @SPI 注解，则获取其 @SPI.value 属性，如果存在，则缓存到 String cachedDefaultName 中 */
    private String cachedDefaultName;

    /** 存储在创建适配类实例这个过程中发生的错误，后续该 type 下再调用创建适配类的时候，就直接抛错 */
    private volatile Throwable createAdaptiveInstanceError;

    /** 存放具有一个type入参的构造器的实现类（wrapper类）的Class对象，用于实现 AOP */
    private Set<Class<?>> cachedWrapperClasses;

    /** key :实现类的全类名  value: exception, 如果加载当前行失败，则存储在这里，防止真正的异常被吞掉 */
    private Map<String, IllegalStateException> exceptions = new ConcurrentHashMap<>();


    //单例模式中，最典型的实现就是通过私有构造方法实现的：
    private ExtensionLoader(Class<?> type) {
        this.type = type;
       // type如果是ExtensionFactory类型，那么objectFactory是null,否则是ExtensionFactory类型的适配器类型
        if (type == ExtensionFactory.class) {
            objectFactory = null;
        } else {
            objectFactory = ExtensionLoader.getExtensionLoader(ExtensionFactory.class).getAdaptiveExtension();
        }
    }

    private static <T> boolean withExtensionAnnotation(Class<T> type) {
        return type.isAnnotationPresent(SPI.class);
    }

    @SuppressWarnings("unchecked")
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {

        /**
         * 1. 校验入参type：非空 + 接口 + 含有@SPI注解
         */
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type (" + type + ") is not an interface!");
        }
        //这个类型必须加上SPI注解，否则报错
        if (!withExtensionAnnotation(type)) {
            throw new IllegalArgumentException("Extension type (" + type +
                    ") is not an extension, because it is NOT annotated with @" + SPI.class.getSimpleName() + "!");
        }

         //2. 根据type接口从全局缓存EXTENSION_LOADERS中获取ExtensionLoader,如果有直接返回;如果没有,则先创建,之后放入缓存,最后返回
        ExtensionLoader<T> loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        if (loader == null) {
            //取不到创建一个放入EXTENSION_LOADERS中
            ExtensionLoader<T> tExtensionLoader = new ExtensionLoader<>(type);
            EXTENSION_LOADERS.putIfAbsent(type,tExtensionLoader );
            loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        }
        return loader;
    }

    // For testing purposes only
    public static void resetExtensionLoader(Class type) {
        ExtensionLoader loader = EXTENSION_LOADERS.get(type);
        if (loader != null) {
            // Remove all instances associated with this loader as well
            Map<String, Class<?>> classes = loader.getExtensionClasses();
            for (Map.Entry<String, Class<?>> entry : classes.entrySet()) {
                EXTENSION_INSTANCES.remove(entry.getValue());
            }
            classes.clear();
            EXTENSION_LOADERS.remove(type);
        }
    }

    public static void destroyAll() {
        EXTENSION_INSTANCES.forEach((_type, instance) -> {
            if (instance instanceof Lifecycle) {
                Lifecycle lifecycle = (Lifecycle) instance;
                try {
                    lifecycle.destroy();
                } catch (Exception e) {
                    logger.error("Error destroying extension " + lifecycle, e);
                }
            }
        });
    }

    private static ClassLoader findClassLoader() {
        return ClassUtils.getClassLoader(ExtensionLoader.class);
    }

    public String getExtensionName(T extensionInstance) {
        return getExtensionName(extensionInstance.getClass());
    }

    public String getExtensionName(Class<?> extensionClass) {
        getExtensionClasses();// load class
        return cachedNames.get(extensionClass);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, key, null)}
     *
     * @param url url
     * @param key url parameter key which used to get extension point names
     * @return extension list which are activated.
     * @see #getActivateExtension(org.apache.dubbo.common.URL, String, String)
     */
    public List<T> getActivateExtension(URL url, String key) {
        return getActivateExtension(url, key, null);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, values, null)}
     *
     * @param url    url
     * @param values extension point names
     * @return extension list which are activated
     * @see #getActivateExtension(org.apache.dubbo.common.URL, String[], String)
     */
    public List<T> getActivateExtension(URL url, String[] values) {
        return getActivateExtension(url, values, null);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, url.getParameter(key).split(","), null)}
     *
     * @param url   url
     * @param key   url parameter key which used to get extension point names
     * @param group group
     * @return extension list which are activated.
     * @see #getActivateExtension(org.apache.dubbo.common.URL, String[], String)
     */
    public List<T> getActivateExtension(URL url, String key, String group) {
        String value = url.getParameter(key);
        return getActivateExtension(url, StringUtils.isEmpty(value) ? null : COMMA_SPLIT_PATTERN.split(value), group);
    }

    /**
     * Get activate extensions.
     *
     * @param url    url
     * @param values extension point names
     * @param group  group
     * @return extension list which are activated
     * @see org.apache.dubbo.common.extension.Activate
     */
    public List<T> getActivateExtension(URL url, String[] values, String group) {
        List<T> exts = new ArrayList<>();
        List<String> names = values == null ? new ArrayList<>(0) : Arrays.asList(values);
        if (!names.contains(REMOVE_VALUE_PREFIX + DEFAULT_KEY)) {
            getExtensionClasses();
            for (Map.Entry<String, Object> entry : cachedActivates.entrySet()) {
                String name = entry.getKey();
                Object activate = entry.getValue();

                String[] activateGroup, activateValue;

                if (activate instanceof Activate) {
                    activateGroup = ((Activate) activate).group();
                    activateValue = ((Activate) activate).value();
                } else if (activate instanceof com.alibaba.dubbo.common.extension.Activate) {
                    activateGroup = ((com.alibaba.dubbo.common.extension.Activate) activate).group();
                    activateValue = ((com.alibaba.dubbo.common.extension.Activate) activate).value();
                } else {
                    continue;
                }
                if (isMatchGroup(group, activateGroup)
                        && !names.contains(name)
                        && !names.contains(REMOVE_VALUE_PREFIX + name)
                        && isActive(activateValue, url)) {
                    exts.add(getExtension(name));
                }
            }
            exts.sort(ActivateComparator.COMPARATOR);
        }
        List<T> usrs = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (!name.startsWith(REMOVE_VALUE_PREFIX)
                    && !names.contains(REMOVE_VALUE_PREFIX + name)) {
                if (DEFAULT_KEY.equals(name)) {
                    if (!usrs.isEmpty()) {
                        exts.addAll(0, usrs);
                        usrs.clear();
                    }
                } else {
                    usrs.add(getExtension(name));
                }
            }
        }
        if (!usrs.isEmpty()) {
            exts.addAll(usrs);
        }
        return exts;
    }

    private boolean isMatchGroup(String group, String[] groups) {
        if (StringUtils.isEmpty(group)) {
            return true;
        }
        if (groups != null && groups.length > 0) {
            for (String g : groups) {
                if (group.equals(g)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isActive(String[] keys, URL url) {
        if (keys.length == 0) {
            return true;
        }
        for (String key : keys) {
            // @Active(value="key1:value1, key2:value2")
            String keyValue = null;
            if (key.contains(":")) {
                String[] arr = key.split(":");
                key = arr[0];
                keyValue = arr[1];
            }

            for (Map.Entry<String, String> entry : url.getParameters().entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue();
                if ((k.equals(key) || k.endsWith("." + key))
                        && ((keyValue != null && keyValue.equals(v)) || (keyValue == null && ConfigUtils.isNotEmpty(v)))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get extension's instance. Return <code>null</code> if extension is not found or is not initialized. Pls. note
     * that this method will not trigger extension load.
     * <p>
     * In order to trigger extension load, call {@link #getExtension(String)} instead.
     *
     * @see #getExtension(String)
     */
    @SuppressWarnings("unchecked")
    public T getLoadedExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        Holder<Object> holder = getOrCreateHolder(name);
        return (T) holder.get();
    }

    private Holder<Object> getOrCreateHolder(String name) {
        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) {
            cachedInstances.putIfAbsent(name, new Holder<>());
            holder = cachedInstances.get(name);
        }
        return holder;
    }

    /**
     * Return the list of extensions which are already loaded.
     * <p>
     * Usually {@link #getSupportedExtensions()} should be called in order to get all extensions.
     *
     * @see #getSupportedExtensions()
     */
    public Set<String> getLoadedExtensions() {
        return Collections.unmodifiableSet(new TreeSet<>(cachedInstances.keySet()));
    }

    public List<T> getLoadedExtensionInstances() {
        List<T> instances = new ArrayList<>();
        cachedInstances.values().forEach(holder -> instances.add((T) holder.get()));
        return instances;
    }

    public Object getLoadedAdaptiveExtensionInstances() {
        return cachedAdaptiveInstance.get();
    }

//    public T getPrioritizedExtensionInstance() {
//        Set<String> supported = getSupportedExtensions();
//
//        Set<T> instances = new HashSet<>();
//        Set<T> prioritized = new HashSet<>();
//        for (String s : supported) {
//
//        }
//
//    }

    /**
     * Find the extension with the given name. If the specified name is not found, then {@link IllegalStateException}
     * will be thrown.
     */
    /**
     * 根据传入的spiKey（name，eg.dubbo），创建相应的SPI扩展实例（eg.DubboProtocol），
     * 并且将 <spiKey, 创建好的SPI扩展实例> 存储到 Map<String, Holder<Object>> cachedInstances 中，
     * eg. <"dubbo", DubboProtocol>
     */
    @SuppressWarnings("unchecked")
    public T getExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        // 创建 @SPI.value 的 extension 的实例
        if ("true".equals(name)) {
            return getDefaultExtension();
        }
        final Holder<Object> holder = getOrCreateHolder(name);
        Object instance = holder.get();
        // 首先从缓存 Map<String, Holder<Object>> cachedInstances 中获取，如果存在，则直接返回，如果不存在，则双重检测创建实例
        if (instance == null) {
            synchronized (holder) {
                instance = holder.get();
                if (instance == null) {
                    // 创建SPI实现类实例
                    instance = createExtension(name);
                    // 进行缓存
                    holder.set(instance);
                }
            }
        }
        return (T) instance;
    }

    /**
     * Get the extension by specified name if found, or {@link #getDefaultExtension() returns the default one}
     *
     * @param name the name of extension
     * @return non-null
     */
    public T getOrDefaultExtension(String name) {
        return containsExtension(name)  ? getExtension(name) : getDefaultExtension();
    }

    /**
     * Return default extension, return <code>null</code> if it's not configured.
     */
    public T getDefaultExtension() {
        getExtensionClasses();
        if (StringUtils.isBlank(cachedDefaultName) || "true".equals(cachedDefaultName)) {
            return null;
        }
        return getExtension(cachedDefaultName);
    }

    public boolean hasExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        Class<?> c = this.getExtensionClass(name);
        return c != null;
    }

    public Set<String> getSupportedExtensions() {
        Map<String, Class<?>> clazzes = getExtensionClasses();
        return Collections.unmodifiableSet(new TreeSet<>(clazzes.keySet()));
    }

    public Set<T> getSupportedExtensionInstances() {
        Set<T> instances = new HashSet<>();
        Set<String> supportedExtensions = getSupportedExtensions();
        if (CollectionUtils.isNotEmpty(supportedExtensions)) {
            for (String name : supportedExtensions) {
                instances.add(getExtension(name));
            }
        }
        return instances;
    }

    /**
     * Return default extension name, return <code>null</code> if not configured.
     */
    public String getDefaultExtensionName() {
        getExtensionClasses();
        return cachedDefaultName;
    }

    /**
     * Register new extension via API
     *
     * @param name  extension name
     * @param clazz extension class
     * @throws IllegalStateException when extension with the same name has already been registered.
     */
    public void addExtension(String name, Class<?> clazz) {
        getExtensionClasses(); // load classes

        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Input type " +
                    clazz + " doesn't implement the Extension " + type);
        }
        if (clazz.isInterface()) {
            throw new IllegalStateException("Input type " +
                    clazz + " can't be interface!");
        }

        if (!clazz.isAnnotationPresent(Adaptive.class)) {
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Extension name is blank (Extension " + type + ")!");
            }
            if (cachedClasses.get().containsKey(name)) {
                throw new IllegalStateException("Extension name " +
                        name + " already exists (Extension " + type + ")!");
            }

            cachedNames.put(clazz, name);
            cachedClasses.get().put(name, clazz);
        } else {
            if (cachedAdaptiveClass != null) {
                throw new IllegalStateException("Adaptive Extension already exists (Extension " + type + ")!");
            }

            cachedAdaptiveClass = clazz;
        }
    }

    /**
     * Replace the existing extension via API
     *
     * @param name  extension name
     * @param clazz extension class
     * @throws IllegalStateException when extension to be placed doesn't exist
     * @deprecated not recommended any longer, and use only when test
     */
    @Deprecated
    public void replaceExtension(String name, Class<?> clazz) {
        getExtensionClasses(); // load classes

        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Input type " +
                    clazz + " doesn't implement Extension " + type);
        }
        if (clazz.isInterface()) {
            throw new IllegalStateException("Input type " +
                    clazz + " can't be interface!");
        }

        if (!clazz.isAnnotationPresent(Adaptive.class)) {
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Extension name is blank (Extension " + type + ")!");
            }
            if (!cachedClasses.get().containsKey(name)) {
                throw new IllegalStateException("Extension name " +
                        name + " doesn't exist (Extension " + type + ")!");
            }

            cachedNames.put(clazz, name);
            cachedClasses.get().put(name, clazz);
            cachedInstances.remove(name);
        } else {
            if (cachedAdaptiveClass == null) {
                throw new IllegalStateException("Adaptive Extension doesn't exist (Extension " + type + ")!");
            }

            cachedAdaptiveClass = clazz;
            cachedAdaptiveInstance.set(null);
        }
    }

    @SuppressWarnings("unchecked")
    public T getAdaptiveExtension() {
        Object instance = cachedAdaptiveInstance.get();
        if (instance == null) {
            if (createAdaptiveInstanceError != null) {
                throw new IllegalStateException("Failed to create adaptive instance: " +
                        createAdaptiveInstanceError.toString(),
                        createAdaptiveInstanceError);
            }

            synchronized (cachedAdaptiveInstance) {
                instance = cachedAdaptiveInstance.get();
                if (instance == null) {
                    try {
                        //缓存中获取不到就创建
                        System.out.println("=========创建AdaptiveExtension==============");
                        instance = createAdaptiveExtension();
                        cachedAdaptiveInstance.set(instance);
                    } catch (Throwable t) {
                        createAdaptiveInstanceError = t;
                        throw new IllegalStateException("Failed to create adaptive instance: " + t.toString(), t);
                    }
                }
            }
        }

        return (T) instance;
    }

    private IllegalStateException findException(String name) {
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) {
            if (entry.getKey().toLowerCase().contains(name.toLowerCase())) {
                return entry.getValue();
            }
        }
        StringBuilder buf = new StringBuilder("No such extension " + type.getName() + " by name " + name);


        int i = 1;
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) {
            if (i == 1) {
                buf.append(", possible causes: ");
            }

            buf.append("\r\n(");
            buf.append(i++);
            buf.append(") ");
            buf.append(entry.getKey());
            buf.append(":\r\n");
            buf.append(StringUtils.toString(entry.getValue()));
        }
        return new IllegalStateException(buf.toString());
    }

    /**
     * 调用方已经加了同步锁，无需考虑线程同步问题
     */
    @SuppressWarnings("unchecked")
    private T createExtension(String name) {
        // 从普通SPI实现类map中获取传入的spiKey的实现类，例如 DubboProtocol.class
        Map<String, Class<?>> extensionClasses = getExtensionClasses();
        Class<?> clazz = extensionClasses.get(name);

        if (clazz == null) {
            // 如果没找到，抛出加载SPI实现类时出现的异常
            throw findException(name);
        }
        try {
            // 从缓存中获取SPI实现类实例，如果有，直接返回，如果没有，创建
            T instance = (T) EXTENSION_INSTANCES.get(clazz);
            if (instance == null) {
                Object o = clazz.newInstance();
                // 创建 DubboProtocol 实例（这里可以看出，要求SPI实现类必须具有无参构造器），放到缓存中 Map<Class<?>, Object> EXTENSION_INSTANCES
                EXTENSION_INSTANCES.putIfAbsent(clazz,o );
                instance = (T) EXTENSION_INSTANCES.get(clazz);
            }

            // Dubbo IOC
            injectExtension(instance);

            //指定的 SPI 接口的 wrapper 类（实现了 SPI 接口 + 入参仅有一个SPI接口参数）放到 Set<Class<?>> cachedWrapperClasses 中，
            // 之后遍历 cachedWrapperClasses 循环调用 wrapper 的单参构造器创建实例，形成一条 wrapper 链，
            // 链尾是进行了 IOC 操作的最底层的 SPI 实现类
            // Dubbo AOP    cachedWrapperClasses :ProtocolFilterWrapper  ProtocolListenerWrapper
            Set<Class<?>> wrapperClasses = cachedWrapperClasses;
            if (CollectionUtils.isNotEmpty(wrapperClasses)) {

                //只有当该class无adative注解，并且构造函数包含目标接口（type）类型，
                //例如protocol里面的spi就只有ProtocolFilterWrapper和ProtocolListenerWrapper能命中
                for (Class<?> wrapperClass : wrapperClasses) {
                    T t = (T) wrapperClass.getConstructor(type).newInstance(instance);
                    instance = injectExtension((T)t);
                }
            }
            initExtension(instance);
            return instance;
        } catch (Throwable t) {
            throw new IllegalStateException("Extension instance (name: " + name + ", class: " +
                    type + ") couldn't be instantiated: " + t.getMessage(), t);
        }
    }

    private boolean containsExtension(String name) {
        return getExtensionClasses().containsKey(name);
    }

    /**
     * ioc 有效的前提: objectFactory != null
     * 遍历当前的实现类（eg. DubboProtocol）中的每一个方法：
     * 1. 对于方法是 setXxx + public修饰符 + setXxx入参只有一个的方法，进行如下操作：
     * 1.1 如果该 setXxx 方法有 @DisableInject 注解，则当前方法不处理，行下一个方法的处理，否则
     * 1.2 获取 setXxx 入参类型 pt + 获取入参属性名 property，
     *     之后使用 AdaptiveExtensionFactory.getExtension(pt, property) 来获取被注入的实例
     * 1.3 调用 setXxx 将被注入的实例设置到当前的实现类中，实现 ioc
     *
     */
    private T injectExtension(T instance) {

        if (objectFactory == null) {
            return instance;
        }

        try {
            for (Method method : instance.getClass().getMethods()) {
                if (!isSetter(method)) {
                    continue;
                }
                /**
                 * Check {@link DisableInject} to see if we need auto injection for this property
                 * 如果该 setXxx 方法有 @DisableInject 注解，则当前方法不处理，行下一个方法的处理
                 */
                if (method.getAnnotation(DisableInject.class) != null) {
                    continue;
                }
                // 获取setXxx入参类型
                Class<?> pt = method.getParameterTypes()[0];
                if (ReflectUtils.isPrimitives(pt)) {
                    continue;
                }

                try {
                    // 获取入参属性名 setName => name
                    String property = getSetterProperty(method);

                    // 使用 AdaptiveExtensionFactory.getExtension(pt, property) 来获取被注入的实例
                    Object object = objectFactory.getExtension(pt, property);
                    if (object != null) {

                        // 调用 setXxx 将被注入的实例设置到当前的实现类中，实现 ioc
                        method.invoke(instance, object);
                    }
                } catch (Exception e) {
                    logger.error("Failed to inject via method " + method.getName()
                            + " of interface " + type.getName() + ": " + e.getMessage(), e);
                }

            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return instance;
    }

    private void initExtension(T instance) {
        if (instance instanceof Lifecycle) {
            Lifecycle lifecycle = (Lifecycle) instance;
            lifecycle.initialize();
        }
    }

    /**
     * get properties name for setter, for instance: setVersion, return "version"
     * <p>
     * return "", if setter name with length less than 3
     */
    private String getSetterProperty(Method method) {
        return method.getName().length() > 3 ? method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4) : "";
    }

    /**
     * return true if and only if:
     * <p>
     * 1, public
     * <p>
     * 2, name starts with "set"
     * <p>
     * 3, only has one parameter
     */
    private boolean isSetter(Method method) {
        return method.getName().startsWith("set")
                && method.getParameterTypes().length == 1
                && Modifier.isPublic(method.getModifiers());
    }

    private Class<?> getExtensionClass(String name) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        if (name == null) {
            throw new IllegalArgumentException("Extension name == null");
        }
        return getExtensionClasses().get(name);
    }

    /**
     * 加载三个 spi 目录下的 Class<?> type（eg. Protocol） 指定的 spi 接口的所有适配类、包装类和普通实现类，进而初始化各个缓存项
     * 1. Holder<Map<String, Class<?>>> cachedClasses，存储 type 的所有普通实现类的 <spiKey, valueClass>
     * 2. 如果扩展接口（eg. Protocol）有 @SPI 注解，则获取其 @SPI.value 属性，如果存在，则缓存到 String cachedDefaultName 中（例如, "dubbo"）
     * 3. 之后依次加载三个 spi 目录下的所有 type spi 文件，读取文件中的每一行：
     *  3.1 如果类上有 @Adaptive 注解，则缓存到 Class<?> cachedAdaptiveClass 中
     *  3.2 如果类是 wrapper 类，则缓存到 Set<Class<?>> cachedWrapperClasses 中
     *  3.3 如果类是普通实现类，
     *      检测实现类必须有无参构造器，（在 T createExtension(String name) 处会调用无参构造器实例化）
     *      如果类上有 @Activate 注解，则缓存到 Map<String, Activate> cachedActivates 中，key=spiKey，value=注解；
     *      存储 spiKey 到 Map<Class<?>, String> cachedNames；
     *      将当前的 spi 实现类的 <spiKey, value> 存储到 Holder<Map<String, Class<?>>> cachedClasses
     *  3.4 如果加载当前行失败，则存储在 Map<String, IllegalStateException> exceptions 中，key :实现类的全类名 value: exception，防止真正的异常被吞掉
     */
    private Map<String, Class<?>> getExtensionClasses() {
        Map<String, Class<?>> classes = cachedClasses.get();
        if (classes == null) {
            synchronized (cachedClasses) {
                classes = cachedClasses.get();
                if (classes == null) {
                    // 加载一个目录
                    classes = loadExtensionClasses();
                    //放入缓存
                    cachedClasses.set(classes);
                }
            }
        }
        return classes;
    }

    /**
     * synchronized in getExtensionClasses
     * */
    private Map<String, Class<?>> loadExtensionClasses() {

        //获取到类型的SPI注解，所以利用SPI扩展点的地方，需要加入SPI注解
        cacheDefaultExtensionName();


        /**
         * 从下面的地址中加在这个类型的数据的extensionClasses中，地址包括
         * META-INF/dubbo/internal/
         * META-INF/dubbo/
         * META-INF/services/
         */

        Map<String, Class<?>> extensionClasses = new HashMap<>();
        // internal extension load from ExtensionLoader's ClassLoader first
        loadDirectory(extensionClasses, DUBBO_INTERNAL_DIRECTORY, type.getName(), true);
        loadDirectory(extensionClasses, DUBBO_INTERNAL_DIRECTORY, type.getName().replace("org.apache", "com.alibaba"), true);

        loadDirectory(extensionClasses, DUBBO_DIRECTORY, type.getName());
        loadDirectory(extensionClasses, DUBBO_DIRECTORY, type.getName().replace("org.apache", "com.alibaba"));
        loadDirectory(extensionClasses, SERVICES_DIRECTORY, type.getName());
        loadDirectory(extensionClasses, SERVICES_DIRECTORY, type.getName().replace("org.apache", "com.alibaba"));
        return extensionClasses;
    }

    /**
     * extract and cache default extension name if exists
     */
    private void cacheDefaultExtensionName() {

        //获取到类型的SPI注解，所以利用SPI扩展点的地方，需要加入SPI注解
        final SPI defaultAnnotation = type.getAnnotation(SPI.class);
        if (defaultAnnotation == null) {
            return;
        }

        String value = defaultAnnotation.value();
        if ((value = value.trim()).length() > 0) {
            String[] names = NAME_SEPARATOR.split(value);
            if (names.length > 1) {
                throw new IllegalStateException("More than 1 default extension name on extension " + type.getName()
                        + ": " + Arrays.toString(names));
            }
            //如果注解中有value，说明有默认的实现，那么将value放到cachedDefaultName中
            if (names.length == 1) {
                cachedDefaultName = names[0];
            }
        }
    }

    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir, String type) {
        loadDirectory(extensionClasses, dir, type, false);
    }

    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir, String type, boolean extensionLoaderClassLoaderFirst) {
        String fileName = dir + type;
        try {
            Enumeration<java.net.URL> urls = null;
            ClassLoader classLoader = findClassLoader();
            
            // try to load from ExtensionLoader's ClassLoader first
            // 加载文件中的一个SPI实现类
            if (extensionLoaderClassLoaderFirst) {
                ClassLoader extensionLoaderClassLoader = ExtensionLoader.class.getClassLoader();
                if (ClassLoader.getSystemClassLoader() != extensionLoaderClassLoader) {
                    urls = extensionLoaderClassLoader.getResources(fileName);
                }
            }
            
            if(urls == null || !urls.hasMoreElements()) {
                if (classLoader != null) {
                    urls = classLoader.getResources(fileName);
                } else {
                    urls = ClassLoader.getSystemResources(fileName);
                }
            }

            if (urls != null) {
                while (urls.hasMoreElements()) {
                    java.net.URL resourceURL = urls.nextElement();
                    loadResource(extensionClasses, classLoader, resourceURL);
                }
            }
        } catch (Throwable t) {
            logger.error("Exception occurred when loading extension class (interface: " +
                    type + ", description file: " + fileName + ").", t);
        }
    }

    private void loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader, java.net.URL resourceURL) {
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceURL.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final int ci = line.indexOf('#');
                    if (ci >= 0) {
                        line = line.substring(0, ci);
                    }
                    line = line.trim();
                    if (line.length() > 0) {
                        try {
                            String name = null;
                            int i = line.indexOf('=');
                            if (i > 0) {
                                name = line.substring(0, i).trim();
                                line = line.substring(i + 1).trim();
                            }
                            if (line.length() > 0) {
                                loadClass(extensionClasses, resourceURL, Class.forName(line, true, classLoader), name);
                            }
                        } catch (Throwable t) {
                            IllegalStateException e = new IllegalStateException("Failed to load extension class (interface: " + type + ", class line: " + line + ") in " + resourceURL + ", cause: " + t.getMessage(), t);
                            exceptions.put(line, e);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logger.error("Exception occurred when loading extension class (interface: " +
                    type + ", class file: " + resourceURL + ") in " + resourceURL, t);
        }
    }

    /**
     * 加载一个类
     * 1.adaptive 类、wrapper 类、普通 SPI 类都必须实现 SPI 接口
     * 2.
     * @param extensionClasses
     * @param resourceURL
     * @param clazz
     * @param name
     * @throws NoSuchMethodException
     */
    private void loadClass(Map<String, Class<?>> extensionClasses, java.net.URL resourceURL, Class<?> clazz, String name) throws NoSuchMethodException {
        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Error occurred when loading extension class (interface: " +
                    type + ", class line: " + clazz.getName() + "), class "
                    + clazz.getName() + " is not subtype of interface.");
        }
        //如果类上有 @Adaptive 注解，则缓存到 Class<?> cachedAdaptiveClass 中（如果有多个 @Adaptive 类，则直接抛出异常）
        if (clazz.isAnnotationPresent(Adaptive.class)) {
            cacheAdaptiveClass(clazz);

            // 如果类是 wrapper 类，则缓存到 Set<Class<?>> cachedWrapperClasses 中
        } else if (isWrapperClass(clazz)) {
            cacheWrapperClass(clazz);

            // 正常类
        } else {
            // 检测必须有无参构造器
            clazz.getConstructor();
            if (StringUtils.isEmpty(name)) {

                name = findAnnotationName(clazz);
                if (name.length() == 0) {
                    throw new IllegalStateException("No such extension name for the class " + clazz.getName() + " in the config " + resourceURL);
                }
            }

            String[] names = NAME_SEPARATOR.split(name);
            if (ArrayUtils.isNotEmpty(names)) {
                // 如果类上有 @Activate 注解，则缓存到 Map<String, Activate> cachedActivates 中，key=spiKey，value=注解
                cacheActivateClass(clazz, names[0]);
                for (String n : names) {
                    // 存储 spiKey 到 Map<Class<?>, String> cachedNames
                    cacheName(clazz, n);

                    // 将 spi 的 key-value 存储到 Map<String, Class<?>> extensionClasses 中，
                    // 实际上该 extensionClasses 会在最外层 getExtensionClasses() 处存储到 Holder<Map<String, Class<?>>> cachedClasses 中
                    saveInExtensionClass(extensionClasses, clazz, n);
                }
            }
        }
    }

    /**
     * cache name
     */
    private void cacheName(Class<?> clazz, String name) {
        if (!cachedNames.containsKey(clazz)) {
            cachedNames.put(clazz, name);
        }
    }

    /**
     * put clazz in extensionClasses
     */
    private void saveInExtensionClass(Map<String, Class<?>> extensionClasses, Class<?> clazz, String name) {
        Class<?> c = extensionClasses.get(name);
        if (c == null) {
            extensionClasses.put(name, clazz);
        } else if (c != clazz) {
            String duplicateMsg = "Duplicate extension " + type.getName() + " name " + name + " on " + c.getName() + " and " + clazz.getName();
            logger.error(duplicateMsg);
            throw new IllegalStateException(duplicateMsg);
        }
    }

    /**
     * cache Activate class which is annotated with <code>Activate</code>
     * <p>
     * for compatibility, also cache class with old alibaba Activate annotation
     */
    private void cacheActivateClass(Class<?> clazz, String name) {
        Activate activate = clazz.getAnnotation(Activate.class);
        if (activate != null) {
            cachedActivates.put(name, activate);
        } else {
            // support com.alibaba.dubbo.common.extension.Activate
            com.alibaba.dubbo.common.extension.Activate oldActivate = clazz.getAnnotation(com.alibaba.dubbo.common.extension.Activate.class);
            if (oldActivate != null) {
                cachedActivates.put(name, oldActivate);
            }
        }
    }

    /**
     * cache Adaptive class which is annotated with <code>Adaptive</code>
     */
    private void cacheAdaptiveClass(Class<?> clazz) {
        if (cachedAdaptiveClass == null) {
            cachedAdaptiveClass = clazz;
        } else if (!cachedAdaptiveClass.equals(clazz)) {
            throw new IllegalStateException("More than 1 adaptive class found: "
                    + cachedAdaptiveClass.getName()
                    + ", " + clazz.getName());
        }
    }

    /**
     * cache wrapper class
     * <p>
     * like: ProtocolFilterWrapper, ProtocolListenerWrapper
     */
    private void cacheWrapperClass(Class<?> clazz) {
        if (cachedWrapperClasses == null) {
            cachedWrapperClasses = new ConcurrentHashSet<>();
        }
        cachedWrapperClasses.add(clazz);
    }

    /**
     * test if clazz is a wrapper class
     * <p>
     * which has Constructor with given class type as its only argument
     */
    private boolean isWrapperClass(Class<?> clazz) {
        try {
            clazz.getConstructor(type);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private String findAnnotationName(Class<?> clazz) {
        org.apache.dubbo.common.Extension extension = clazz.getAnnotation(org.apache.dubbo.common.Extension.class);
        if (extension != null) {
            return extension.value();
        }

        String name = clazz.getSimpleName();
        if (name.endsWith(type.getSimpleName())) {
            name = name.substring(0, name.length() - type.getSimpleName().length());
        }
        return name.toLowerCase();
    }

    @SuppressWarnings("unchecked")
    private T createAdaptiveExtension() {
        try {

            //这里把代码进行了拆分
            //injectExtension进入IOC的反转控制模式，实现了动态入注
            Class<?> adaptiveExtensionClass = getAdaptiveExtensionClass();

            Object o = adaptiveExtensionClass.newInstance();

            return injectExtension((T) o);
        } catch (Exception e) {
            throw new IllegalStateException("Can't create adaptive extension " + type + ", cause: " + e.getMessage(), e);
        }
    }

    /**
     * 生成了动态类
     * @return
     */
    private Class<?> getAdaptiveExtensionClass() {
        /**
         * 触发SPI流程的扫描
         */
        getExtensionClasses();

        System.out.println("=======cachedAdaptiveClass============"+cachedAdaptiveClass);

        /**
         * 如果通过上面的步骤可以获取到cachedAdaptiveClass直接返回，如果不行的话，就得考虑自己进行利用动态代理创建一个了
         */
        if (cachedAdaptiveClass != null) {
            return cachedAdaptiveClass;
        }
        /**
         * 利用动态代理创建一个扩展类
         */
        return cachedAdaptiveClass = createAdaptiveExtensionClass();
    }

    private Class<?> createAdaptiveExtensionClass() {

        System.out.println(cachedDefaultName);

        String code = new AdaptiveClassCodeGenerator(type, cachedDefaultName).generate();
        System.out.println("/r/n");
        System.out.println("/r/n");
        System.out.println(code);

        System.out.println("/r/n");
        System.out.println("/r/n");
        ClassLoader classLoader = findClassLoader();
        org.apache.dubbo.common.compiler.Compiler compiler = ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.compiler.Compiler.class).getAdaptiveExtension();
        return compiler.compile(code, classLoader);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "[" + type.getName() + "]";
    }

}
