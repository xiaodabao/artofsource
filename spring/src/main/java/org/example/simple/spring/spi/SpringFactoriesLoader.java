package org.example.simple.spring.spi;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.*;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public final class SpringFactoriesLoader {
    /**
     * The location to look for factories.
     * <p>Can be present in multiple JAR files.
     */
    public static final String FACTORIES_RESOURCE_LOCATION = "META-INF/spring.factories";


    private static final Log logger = LogFactory.getLog(SpringFactoriesLoader.class);

    static final Map<ClassLoader, Map<String, List<String>>> cache = new ConcurrentReferenceHashMap<>();


    private SpringFactoriesLoader() {
    }


    /**
     * Load and instantiate the factory implementations of the given type from
     * {@value #FACTORIES_RESOURCE_LOCATION}, using the given class loader.
     * <p>The returned factories are sorted through {@link AnnotationAwareOrderComparator}.
     * <p>If a custom instantiation strategy is required, use {@link #loadFactoryNames}
     * to obtain all registered factory names.
     * <p>As of Spring Framework 5.3, if duplicate implementation class names are
     * discovered for a given factory type, only one instance of the duplicated
     * implementation type will be instantiated.
     * @param factoryType the interface or abstract class representing the factory
     * @param classLoader the ClassLoader to use for loading (can be {@code null} to use the default)
     * @throws IllegalArgumentException if any factory implementation class cannot
     * be loaded or if an error occurs while instantiating any factory
     * @see #loadFactoryNames
     */
    public static <T> List<T> loadFactories(Class<T> factoryType, @Nullable ClassLoader classLoader) {
        Assert.notNull(factoryType, "'factoryType' must not be null");
        ClassLoader classLoaderToUse = classLoader;
        if (classLoaderToUse == null) {
            classLoaderToUse = SpringFactoriesLoader.class.getClassLoader();
        }
        // 获取实现类名称集合
        List<String> factoryImplementationNames = loadFactoryNames(factoryType, classLoaderToUse);
        if (logger.isTraceEnabled()) {
            logger.trace("Loaded [" + factoryType.getName() + "] names: " + factoryImplementationNames);
        }
        List<T> result = new ArrayList<>(factoryImplementationNames.size());
        // 实例化所有的实现类
        for (String factoryImplementationName : factoryImplementationNames) {
            result.add(instantiateFactory(factoryImplementationName, factoryType, classLoaderToUse));
        }
        // 按照PriorityOrder、Order排序
        AnnotationAwareOrderComparator.sort(result);
        return result;
    }

    /**
     * Load the fully qualified class names of factory implementations of the
     * given type from {@value #FACTORIES_RESOURCE_LOCATION}, using the given
     * class loader.
     * <p>As of Spring Framework 5.3, if a particular implementation class name
     * is discovered more than once for the given factory type, duplicates will
     * be ignored.
     * @param factoryType the interface or abstract class representing the factory
     * @param classLoader the ClassLoader to use for loading resources; can be
     * {@code null} to use the default
     * @throws IllegalArgumentException if an error occurs while loading factory names
     * @see #loadFactories
     */
    public static List<String> loadFactoryNames(Class<?> factoryType, @Nullable ClassLoader classLoader) {
        ClassLoader classLoaderToUse = classLoader;
        if (classLoaderToUse == null) {
            classLoaderToUse = SpringFactoriesLoader.class.getClassLoader();
        }
        String factoryTypeName = factoryType.getName();
        return loadSpringFactories(classLoaderToUse).getOrDefault(factoryTypeName, Collections.emptyList());
    }

    /**
     * 根据ClassLoader加载所有的数据  key: interfaceName --> value: implementationNames
     * @param classLoader
     * @return
     */
    private static Map<String, List<String>> loadSpringFactories(ClassLoader classLoader) {
        // 首先从缓存中获取
        Map<String, List<String>> result = cache.get(classLoader);
        if (result != null) {
            return result;
        }

        result = new HashMap<>();
        try {
            // 在所有的jar包中搜索classpath:META-INF/spring.factories
            Enumeration<URL> urls = classLoader.getResources(FACTORIES_RESOURCE_LOCATION);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                UrlResource resource = new UrlResource(url);
                // 将url封装为Resource, 并加载到Properties中
                Properties properties = PropertiesLoaderUtils.loadProperties(resource);
                for (Map.Entry<?, ?> entry : properties.entrySet()) {
                    // key 为 抽象类型 interface、abstractType
                    String factoryTypeName = ((String) entry.getKey()).trim();
                    // 将实现类使用, 分割为实现类数组
                    String[] factoryImplementationNames =
                            StringUtils.commaDelimitedListToStringArray((String) entry.getValue());
                    for (String factoryImplementationName : factoryImplementationNames) {
                        // 处理实现类, 如果多个jar包中存在多个相同的接口key, 在这里也会进行合并
                        result.computeIfAbsent(factoryTypeName, key -> new ArrayList<>())
                                .add(factoryImplementationName.trim());
                    }
                }
            }

            // 实现类去重, 并将其设置为不可变List
            result.replaceAll((factoryType, implementations) -> implementations.stream().distinct()
                    .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)));
            // 加入缓存
            cache.put(classLoader, result);
        }
        catch (IOException ex) {
            throw new IllegalArgumentException("Unable to load factories from location [" +
                    FACTORIES_RESOURCE_LOCATION + "]", ex);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T instantiateFactory(String factoryImplementationName, Class<T> factoryType, ClassLoader classLoader) {
        try {
            // 获取实现类的Class对象, 因为只配置了名称
            Class<?> factoryImplementationClass = ClassUtils.forName(factoryImplementationName, classLoader);
            // 必须是factoryType的实现类
            if (!factoryType.isAssignableFrom(factoryImplementationClass)) {
                throw new IllegalArgumentException(
                        "Class [" + factoryImplementationName + "] is not assignable to factory type [" + factoryType.getName() + "]");
            }
            // 使用newInstance 反射的方式创建对象, 必须存在无参构造函数
            return (T) ReflectionUtils.accessibleConstructor(factoryImplementationClass).newInstance();
        }
        catch (Throwable ex) {
            throw new IllegalArgumentException(
                    "Unable to instantiate factory class [" + factoryImplementationName + "] for factory type [" + factoryType.getName() + "]",
                    ex);
        }
    }
}
