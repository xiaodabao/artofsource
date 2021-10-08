package org.example.simple.spring.spi;

import org.springframework.context.ApplicationContextInitializer;

import java.util.List;

public class SpringFactoriesLoaderTest {

    public static void main(String[] args) {

        testApplicationContextInitializer();

        testApplicationContextInitializerInstance();

        testColor();
    }

    /**
     * 测试 loadFactoryNames方法, 输出 ApplicationContextInitializer的实现类名称
     */
    public static void testApplicationContextInitializer() {
        List<String> applicationContextInitializerNames = SpringFactoriesLoader.loadFactoryNames(ApplicationContextInitializer.class, null);

        applicationContextInitializerNames.forEach(System.out::println);
    }

    /**
     * 测试 LoadFactories方法, 输出 ApplicationContextInitializer 的实现类实例
     */
    public static void testApplicationContextInitializerInstance() {
        List<ApplicationContextInitializer> applicationContextInitializerNames = SpringFactoriesLoader.loadFactories(ApplicationContextInitializer.class, null);

        applicationContextInitializerNames.forEach(System.out::println);
    }

    /**
     * 通过自定义 META-INF/spring.factories, 可以使用Spring SPI机制
     * 当实现Spring Interface的时候, 就可以进行自定义扩展了
     */
    public static void testColor() {
        List<Color> colors = SpringFactoriesLoader.loadFactories(Color.class, null);

        colors.forEach(System.out::println);
    }
}
