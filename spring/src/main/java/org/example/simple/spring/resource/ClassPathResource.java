package org.example.simple.spring.resource;

import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Resource implementation for class path resources.
 * Uses either a given ClassLoader or a given Class for loading resources.
 *
 * 对 classpath 类型的资源的Resource实现
 * 使用指定的ClassLoader或Class来加载资源
 *
 * Supports resolution as java.io.File if the class path resource resides in the file system,
 * but not for resources in a JAR. Always supports resolution as URL.
 *
 * 如果classpath资源位于文件系统中, 则支持将其解析为java.io.File
 * 不支持位于JAR中的资源. 始终支持解析为URL
 */
public class ClassPathResource extends AbstractFileResolvingResource {

    private final String path;

    private ClassLoader classLoader;

    private Class<?> clazz;

    /**
     * Create a new ClassPathResource for ClassLoader usage.
     * A leading slash will be removed, as the ClassLoader resource access methods will not accept it.
     * The thread context class loader will be used for loading the resource.
     *
     * 使用类加载器创建新的ClassPathResource
     * 前导/将被移除, 因为类加载器的资源访问方法不接收它
     * 线程上下文类加载器将会用来加载资源
     * @param path the absolute path within the class path: classpath中的绝对路径
     */
    public ClassPathResource(String path) {
        this(path, (ClassLoader) null);
    }

    public ClassPathResource(String path, ClassLoader classLoader) {
        Assert.notNull(path, "Path must not be null");
        String pathToUse = StringUtils.cleanPath(path);
        if (pathToUse.startsWith("/")) {
            pathToUse = pathToUse.substring(1);
        }
        this.path = pathToUse;
        this.classLoader = (classLoader != null ? classLoader : ClassUtils.getDefaultClassLoader());
    }

    public ClassPathResource(String path, Class<?> clazz) {
        Assert.notNull(path, "Path must not be null");
        this.path = StringUtils.cleanPath(path);
        this.clazz = clazz;
    }

    public final String getPath() {
        return path;
    }

    public final ClassLoader getClassLoader() {
        return (this.clazz != null ? this.clazz.getClassLoader() : this.classLoader);
    }

    @Override
    public boolean exists() {
        return resolveURL() != null;
    }

    /**
     * Resolves a URL for the underlying class path resource.
     * 将底层的classpath资源解析为URL
     * @return the resolved URL, or null if not resolvable : 解析失败返回null
     */
    protected URL resolveURL() {
        try {
            if (this.clazz != null) {
                return this.clazz.getResource(this.path);
            } else if (this.classLoader != null) {
                return this.classLoader.getResource(this.path);
            } else {
                return ClassLoader.getSystemResource(this.path);
            }
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * This implementation opens an InputStream for the given class path resource.
     * 对给定的classpath资源打开InputStream
     * @return
     * @throws IOException
     */
    @Override
    public InputStream getInputStream() throws IOException {
        InputStream is;

        if (this.clazz != null) {
            is = this.clazz.getResourceAsStream(this.path);
        } else if (this.classLoader != null) {
            is = this.classLoader.getResourceAsStream(this.path);
        } else {
            is = ClassLoader.getSystemResourceAsStream(this.path);
        }
        if (is == null) {
            throw new FileNotFoundException(getDescription() + " cannot be opened because it does not exist");
        }
        return is;
    }

    /**
     * This implementation returns a URL for the underlying class path resource, if available.
     * 对底层的classpath资源返回一个URL
     * @return
     * @throws IOException
     */
    @Override
    public URL getURL() throws IOException {
        URL url = resolveURL();
        if (url == null) {
            throw new FileNotFoundException(getDescription() + " cannot be resolved to URL because it does not exist");
        }
        return url;
    }

    @Override
    public Resource createRelative(String relativePath) throws IOException {
        String pathToUse = StringUtils.applyRelativePath(this.path, relativePath);
        return (this.clazz != null ? new ClassPathResource(pathToUse, this.clazz) :
                new ClassPathResource(pathToUse, this.classLoader));
    }

    @Override
    public String getFilename() {
        return StringUtils.getFilename(this.path);
    }

    @Override
    public String getDescription() {
        StringBuilder builder = new StringBuilder("class path resource [");
        String pathToUse = this.path;
        if (this.clazz != null && !pathToUse.startsWith("/")) {
            builder.append(ClassUtils.classPackageAsResourcePath(this.clazz));
            builder.append('/');
        }
        if (pathToUse.startsWith("/")) {
            pathToUse = pathToUse.substring(1);
        }
        builder.append(pathToUse);
        builder.append(']');
        return builder.toString();
    }

    @Override
    public int hashCode() {
        return this.path.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof ClassPathResource)) {
            return false;
        }
        ClassPathResource otherRes = (ClassPathResource) other;
        return (this.path.equals(otherRes.path) &&
                ObjectUtils.nullSafeEquals(this.classLoader, otherRes.classLoader) &&
                ObjectUtils.nullSafeEquals(this.clazz, otherRes.clazz));
    }
}
