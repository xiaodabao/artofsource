package org.example.simple.spring.resource;

import org.springframework.core.io.Resource;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardOpenOption;

/**
 * Abstract base class for resources which resolve URLs into File references,
 * such as UrlResource or ClassPathResource.
 * 用于将URL解析为文件引用的资源抽象基类, 例如: UrlResource、ClassPathResource
 *
 * Detects the "file" protocol as well as the JBoss "vfs" protocol in URLs,
 * resolving file system references accordingly.
 * 检测URL中的file协议和JBoss vfs协议, 相应的解析为文件系统引用
 */
public abstract class AbstractFileResolvingResource extends AbstractResource {

    @Override
    public boolean exists() {
        try {
            URL url = getURL();
            if (ResourceUtils.isFileURL(url)) {
                // Proceed with file system resolution
                return getFile().exists();
            } else {
                // Try a URL connection content-length header
                URLConnection con = url.openConnection();
                customizeConnection(con);

                HttpURLConnection httpCon = (con instanceof HttpURLConnection ? (HttpURLConnection) con : null);
                if (httpCon != null) {
                    int code = httpCon.getResponseCode();
                    // HTTP_OK、HTTP_NOT_FOUND 是两个明确的状态, 应该有明确的结果返回
                    if (code == HttpURLConnection.HTTP_OK) {
                        return true;
                    } else if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                        return false;
                    }
                }
                // 只要contentLength有数据, 就代表资源存在
                if (con.getContentLengthLong() > 0) {
                    return true;
                }

                if (httpCon != null) {
                    // No HTTP OK status, and no content-length header: give up
                    // 这个不知道是什么情况下才会出现
                    httpCon.disconnect();
                    return false;
                } else {
                    getInputStream().close();
                    return true;
                }
            }
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public boolean isReadable() {
        try {
            URL url = getURL();
            if (ResourceUtils.isFileURL(url)) {
                // Proceed with file system resolution
                File file = getFile();
                // 奇怪, 这里不使用file.isFile()方法
                return (file.canRead() && !file.isDirectory());
            } else {
                // Try InputStream resolution for jar resources
                URLConnection con = url.openConnection();
                customizeConnection(con);
                if (con instanceof HttpURLConnection) {
                    HttpURLConnection httpCon = (HttpURLConnection) con;
                    int code = httpCon.getResponseCode();
                    if (code != HttpURLConnection.HTTP_OK) {
                        httpCon.disconnect();
                        return false;
                    }
                }

                long contentLength = con.getContentLengthLong();
                if (contentLength > 0) {
                    return true;
                } else if (contentLength == 0) {
                    // Empty file or directory -> not considered readable...
                    return false;
                } else {
                    // Fall back to stream existence: can we open the stream?
                    getInputStream().close();
                    return true;
                }
            }
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public boolean isFile() {
        try {
            URL url = getURL();
            if (url.getProtocol().startsWith(ResourceUtils.URL_PROTOCOL_VFS)) {
                return VfsResourceDelegate.getResource(url).isFile();
            }
            // 根据url的协议字段即可判断是不是文件
            return ResourceUtils.URL_PROTOCOL_FILE.equals(url.getProtocol());
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public File getFile() throws IOException {
        URL url = getURL();
        if (url.getProtocol().startsWith(ResourceUtils.URL_PROTOCOL_VFS)) {
            return VfsResourceDelegate.getResource(url).getFile();
        }
        return ResourceUtils.getFile(url, getDescription());
    }

    @Override
    protected File getFileForLastModifiedCheck() throws IOException {
        URL url = getURL();
        if (ResourceUtils.isJarURL(url)) {
            URL actualUrl = ResourceUtils.extractArchiveURL(url);
            if (actualUrl.getProtocol().startsWith(ResourceUtils.URL_PROTOCOL_VFS)) {
                return VfsResourceDelegate.getResource(url).getFile();
            }
            return ResourceUtils.getFile(actualUrl, "Jar URL");
        } else {
            return getFile();
        }
    }

    protected boolean isFile(URI uri) {
        try {
            if (uri.getScheme().startsWith(ResourceUtils.URL_PROTOCOL_VFS)) {
                return VfsResourceDelegate.getResource(uri).isFile();
            }
            return ResourceUtils.URL_PROTOCOL_FILE.equals(uri.getScheme());
        } catch (IOException ex) {
            return false;
        }
    }

    protected File getFile(URI uri) throws IOException {
        if (uri.getScheme().startsWith(ResourceUtils.URL_PROTOCOL_VFS)) {
            return VfsResourceDelegate.getResource(uri).getFile();
        }
        return ResourceUtils.getFile(uri, getDescription());
    }

    @Override
    public ReadableByteChannel readableChannel() throws IOException {
        try {
            // Try file system channel
            return FileChannel.open(getFile().toPath(), StandardOpenOption.READ);
        } catch (FileNotFoundException | NoSuchFileException ex) {
            // Fall back to InputStream adaptation in superclass
            return super.readableChannel();
        }
    }

    @Override
    public long contentLength() throws IOException {
        URL url = getURL();
        // ResourceUtils.isFileURL 同时判断三种协议 file、vfsfile、vfs
        if (ResourceUtils.isFileURL(url)) {
            // Proceed with file system resolution
            File file = getFile();
            long length = file.length();
            if (length == 0L && !file.exists()) {
                throw new FileNotFoundException(getDescription() +
                        " cannot be resolved in the file system for checking its content length");

            }
            return length;
        } else {
            // Try a URL connection content-length header
            URLConnection con = url.openConnection();
            customizeConnection(con);
            return con.getContentLengthLong();
        }
    }

    @Override
    public long lastModified() throws IOException {
        URL url = getURL();
        boolean fileCheck = false;
        if (ResourceUtils.isFileURL(url) || ResourceUtils.isJarURL(url)) {
            fileCheck = true;

            try {
                File fileToCheck = getFileForLastModifiedCheck();
                long lastModified = fileToCheck.lastModified();
                if (lastModified > 0L || fileToCheck.exists()) {
                    return lastModified;
                }
            } catch (FileNotFoundException ex) {

            }
        }

        URLConnection con = url.openConnection();
        customizeConnection(con);
        long lastModified = con.getLastModified();
        if (fileCheck && lastModified == 0 && con.getContentLengthLong() <= 0) {
            throw new FileNotFoundException(getDescription() +
                    " cannot be resolved in the file system for checking its last-modified timestamp");
        }
        return lastModified;
    }

    protected void customizeConnection(URLConnection con) throws IOException {
        ResourceUtils.useCachesIfNecessary(con);
        if (con instanceof HttpURLConnection) {
            customizeConnection((HttpURLConnection) con);
        }
    }

    /**
     * 设置请求方式为HEAD
     * @param con
     * @throws IOException
     */
    protected void customizeConnection(HttpURLConnection con) throws IOException {
        con.setRequestMethod("HEAD");
    }

    private static class VfsResourceDelegate {

        public static Resource getResource(URL url) throws IOException {
//            return new VfsResource(VfsUtils.getRoot(url));
            return null;
        }

        public static Resource getResource(URI uri) throws IOException {
            // 因为VfsUtils.getRoot方法是protected的, 无法在此使用, 所以忽略vfs协议的资源
//            return new VfsResource(VfsUtils.getRoot(uri));
            return null;
        }
    }
}
