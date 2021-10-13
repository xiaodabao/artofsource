package org.example.simple.spring.resource;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * Interface for a resource descriptor that abstracts from the actual type of underlying resource,
 * such as a file or class path resource.
 *
 * 从底层资源的实际类型（如文件或类路径资源）提取的资源描述符接口
 *
 * An InputStream can be opened for every resource if it exists in physical form,
 * but a URL or File handle can just be returned for certain resources.
 * The actual behavior is implementation-specific.
 *
 * 如果资源是以物理形式存在的, 那么其InputStream可以打开, 但某些资源只能返回一个URL或文件句柄
 * 实际的行为由具体实现决定.
 */
public interface Resource extends InputStreamSource {

    /**
     * Determine whether this resource actually exists in physical form.
     * 确定此资源是否实际以物理形式存在
     * @return
     */
    boolean exists();

    /**
     * Indicate whether non-empty contents of this resource can be read via getInputStream().
     * 指示是否可以通过getInputStream()方法读取资源的非空内容
     * @return
     */
    default boolean isReadable() {
        return exists();
    }

    /**
     * Indicate whether this resource represents a handle with an open stream.
     * 指示此资源是否已存在一个句柄关联到已打开的stream
     * If true, the InputStream cannot be read multiple times,
     * and must be read and closed to avoid resource leaks.
     * Will be false for typical resource descriptors.
     * 如果返回true, 这个InputStream不能多次读取, 且必须进行读取和关闭, 避免资源泄露
     * 对于典型的资源描述符, 将返回false
     * @return
     */
    default boolean isOpen() {
        return false;
    }

    /**
     * Determine whether this resource represents a file in a file system.
     * A value of true strongly suggests (but does not guarantee) that a getFile() call will succeed.
     * 确定此资源是否代表文件系统中的一个文件
     * 当返回值为true时, 强烈建议(但不保证)对getFile()方法的调用将成功.
     * @return
     */
    default boolean isFile() {
        return false;
    }

    /**
     * Return a URL handle for this resource.
     * 返回此资源的URL句柄
     * @return
     * @throws IOException
     */
    URL getURL() throws IOException;

    /**
     * Return a URI handle for this resource.
     * 返回此资源的URI句柄
     * @return
     * @throws IOException
     */
    URI getURI() throws IOException;

    /**
     * Return a File handle for this resource.
     * 返回此资源的文件句柄
     * @return
     * @throws IOException
     */
    File getFile() throws IOException;

    /**
     * Return a ReadableByteChannel.
     * It is expected that each call creates a fresh channel.
     * 返回一个ReadableByteChannel
     * 每次调用都会创建一个新的channel
     * @return
     * @throws IOException
     */
    default ReadableByteChannel readableChannel() throws IOException {
        return Channels.newChannel(getInputStream());
    }

    /**
     * Determine the content length for this resource.
     * 确定资源的内容长度
     * @return
     * @throws IOException
     */
    long contentLength() throws IOException;

    /**
     * Determine the last-modified timestamp for this resource.
     * 确定资源的最后修改时间
     * @return
     * @throws IOException
     */
    long lastModified() throws IOException;

    /**
     * Create a resource relative to this resource.
     * 创建与此资源相关的资源
     * @param relativePath
     * @return
     * @throws IOException
     */
    Resource createRelative(String relativePath) throws IOException;

    /**
     * Determine a filename for this resource,
     * i.e. typically the last part of the path: for example, "myfile.txt".
     * 确定此资源的文件名, 通常是路径的最后一部分, 比如: myfile.txt
     * @return
     */
    String getFilename();

    /**
     * Return a description for this resource, to be used for error output when working with the resource.
     * 返回此资源的描述信息, 用于处理此资源时的错误输出
     * @return
     */
    String getDescription();
}
