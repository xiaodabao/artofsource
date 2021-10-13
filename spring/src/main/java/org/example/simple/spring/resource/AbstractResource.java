package org.example.simple.spring.resource;

import org.springframework.core.NestedIOException;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * Convenience base class for Resource implementations, pre-implementing typical behavior.
 * 其他Resource实现类的基类, 预实现一些典型的、通用的行为
 *
 * The "exists" method will check whether a File or InputStream can be opened;
 * "isOpen" will always return false; "getURL" and "getFile" throw an exception;
 * and "toString" will return the description.
 *
 * exists 方法将会检查 File或InputStream是否能打开
 * isOpen 方法仍然返回false
 * getURL、getFile 抛出异常
 * toString 方法返回描述信息
 */
public abstract class AbstractResource implements Resource {

    /**
     * This implementation checks whether a File can be opened,
     * falling back to whether an InputStream can be opened.
     * This will cover both directories and content resources.
     *
     * 这个实现检查是否可以打开文件, 是否可以打开InputStream
     * 这将涵盖目录和内容资源
     * @return
     */
    @Override
    public boolean exists() {
        // Try file existence: can we find the file in the file system?
        // 判断文件是否在文件系统中存在
        if (isFile()) {
            try {
                return getFile().exists();
            } catch (IOException ex) {
                // ...记录日志
            }
        }

        // Fall back to stream existence: can we open the stream?
        // 主要判断这个Stream是否可以再次打开?
        try {
            getInputStream().close();
            return true;
        } catch (Throwable ex) {
            // ...记录日志
            return false;
        }
    }

    /**
     * This implementation always returns true for a resource that exists.
     * 资源只要存在, 此方法就一直返回true
     * @return
     */
    @Override
    public boolean isReadable() {
        return exists();
    }

    /**
     * This implementation always returns false.
     * 这个实现也一直返回false
     * @return
     */
    @Override
    public boolean isOpen() {
        return false;
    }

    /**
     * This implementation always returns false.
     * @return
     */
    @Override
    public boolean isFile() {
        return false;
    }

    /**
     * This implementation throws a FileNotFoundException,
     * assuming that the resource cannot be resolved to a URL.
     * 此实现抛出 FileNotFoundException异常
     * 假设此资源不能被解析为URL
     * @return
     * @throws IOException
     */
    @Override
    public URL getURL() throws IOException {
        return null;
    }

    /**
     * This implementation builds a URI based on the URL returned by getURL().
     * 此实现基于URL(getURL方法的返回值)创建URI
     * @return
     * @throws IOException
     */
    @Override
    public URI getURI() throws IOException {
        URL url = getURL();
        try {
            return ResourceUtils.toURI(url);
        } catch (URISyntaxException ex) {
            throw new NestedIOException("Invalid URI [" + url + "]", ex);
        }
    }

    /**
     * This implementation throws a FileNotFoundException,
     * assuming that the resource cannot be resolved to an absolute file path.
     * 此实现抛出FileNotFoundException异常
     * 假设此资源不能被解析为绝对文件路径
     * @return
     * @throws IOException
     */
    @Override
    public File getFile() throws IOException {
        throw new FileNotFoundException(getDescription() + "cannot be resolved to absolute file path");
    }

    /**
     * This implementation returns Channels.newChannel(InputStream) with the result of getInputStream().
     * 此实现返回 Channels.newChannel(InputStream), 使用getInputStream的结果
     *
     * This is the same as in Resource's corresponding default method but mirrored here
     * for efficient JVM-level dispatching in a class hierarchy.
     * 这个实现和Resource中的默认方法一致, 但在这里创建一个镜像, 是为了在类层次结构中进行有效的JVM级别的调度???(少一次查询?)
     * @return
     * @throws IOException
     */
    @Override
    public ReadableByteChannel readableChannel() throws IOException {
        return Channels.newChannel(getInputStream());
    }

    /**
     * This method reads the entire InputStream to determine the content length.
     * 此方法读取InputStream的全部内容来确定内容的长度
     *
     * For a custom sub-class of InputStreamResource,
     * we strongly recommend overriding this method with a more optimal implementation,
     * e.g. checking File length, or possibly simply returning -1 if the stream can only be read once.
     *
     * 对于InputStreamResource的自定义子类, 强烈推荐使用一个更优的实现覆盖此方法
     * 检查文件的长度, 或简单的返回-1, 对于stream只能读一次的情况
     * @return
     * @throws IOException
     */
    @Override
    public long contentLength() throws IOException {
        InputStream is = getInputStream();
        try {
            long size = 0;
            byte[] buf = new byte[256];
            int read;
            // 为了得到内容长度, 竟然要读取资源的全部内容
            while ((read = is.read(buf)) != -1) {
                size += read;
            }
            return size;
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                // ... 记录日志
            }
        }
    }

    /**
     * This implementation checks the timestamp of the underlying File, if available.
     * @return
     * @throws IOException
     */
    @Override
    public long lastModified() throws IOException {
        File fileToCheck = getFileForLastModifiedCheck();
        long lastModified = fileToCheck.lastModified();
        if (lastModified == 0L && !fileToCheck.exists()) {
            throw new FileNotFoundException(getDescription() +
                    "cannot be resolved in the file system for checking its last-modified timestamp");
        }
        return lastModified;
    }

    /**
     * Determine the File to use for timestamp checking.
     * @return
     * @throws IOException
     */
    protected File getFileForLastModifiedCheck() throws IOException {
        return getFile();
    }

    /**
     * This implementation throws a FileNotFoundException,
     * assuming that relative resources cannot be created for this resource.
     * @param relativePath
     * @return
     * @throws IOException
     */
    @Override
    public Resource createRelative(String relativePath) throws IOException {
        throw new FileNotFoundException("Cannot create a relative resource for " + getDescription());
    }

    /**
     * This implementation always returns {@code null},
     * assuming that this resource type does not have a filename.
     * @return
     */
    @Override
    public String getFilename() {
        return null;
    }

    /**
     * This implementation returns the description's hash code.
     * 此实现返回 description 的hashCode
     * @return
     */
    @Override
    public int hashCode() {
        return getDescription().hashCode();
    }

    /**
     * This implementation compares description strings.
     * 此实现比较资源的描述信息字符串
     * @param other
     * @return
     */
    @Override
    public boolean equals(Object other) {
        return (this == other || (other instanceof Resource &&
                ((Resource) other).getDescription().equals(getDescription())));
    }

    /**
     * This implementation returns the description of this resource.
     * 此实现返回资源的描述信息
     * @return
     */
    @Override
    public String toString() {
        return getDescription();
    }
}
