package org.example.simple.spring.resource;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

public class FileSystemResourceTest {

    public static void main(String[] args) throws IOException {

        Resource[] resources = getResource("org/example/simple");

        Arrays.stream(resources).forEach(r -> {
            try {
                URL url = r.getURL();
                System.out.println(url.getProtocol() + url.getPath());
            } catch (Exception e) {

            }
        });
    }

    private static Resource[] getResource(String basePackagePath) throws IOException {

        // 根据basePackage先获取目录级资源
        Set<Resource> rootDirResources = doFindAllClassPathResource(basePackagePath);

        Set<Resource> result = new LinkedHashSet<>(16);
        for (Resource rootDirResource : rootDirResources) {
            // 依次加载每个根目录下的资源
            Set<Resource> fileResources = doFindPathMatchingFileResources(rootDirResource);
            result.addAll(fileResources);
        }

        return result.toArray(new Resource[0]);
    }

    /**
     * 查找指定路径, 并将其转为URLResource, 路径可能在多个位置存在, 所以返回Set集合
     * @param path
     * @return
     * @throws IOException
     */
    private static Set<Resource> doFindAllClassPathResource(String path) throws IOException {
        Set<Resource> result = new LinkedHashSet<>(16);

        ClassLoader cl = FileSystemResourceTest.class.getClassLoader();
        Enumeration<URL> resourceUrls = cl.getResources(path);

        while (resourceUrls.hasMoreElements()) {
            URL url = resourceUrls.nextElement();
            result.add(convertClassLoaderURL(url));
        }
        return result;
    }

    /**
     * 将url封装为UrlResource
     * @param url
     * @return
     */
    private static Resource convertClassLoaderURL(URL url) {
        return new UrlResource(url);
    }


    private static final Set<Resource> doFindPathMatchingFileResources(Resource rootDirResource) throws IOException {
        File rootDir;
        try {
            // 查找文件的绝对路径
            rootDir = rootDirResource.getFile().getAbsoluteFile();
        } catch (Exception ex) {
            ex.printStackTrace();
            return Collections.emptySet();
        }
        return doFindMatchingFileSystemResources(rootDir);
    }

    /**
     * 查找根目录下的所有文件, 并将其转为FileSystemResource
     * @param rootDir
     * @return
     * @throws IOException
     */
    private static final Set<Resource> doFindMatchingFileSystemResources(File rootDir) throws IOException {
        Set<File> matchingFiles = retrieveMatchingFiles(rootDir);

        Set<Resource> result = new LinkedHashSet<>(matchingFiles.size());
        for (File file : matchingFiles) {
            result.add(new FileSystemResource(file));
        }
        return result;
    }

    /**
     * 查询目录下的所有文件
     * @param rootDir
     * @return
     * @throws IOException
     */
    private static final Set<File> retrieveMatchingFiles(File rootDir) throws IOException {
        if (!rootDir.exists()) {
            return Collections.emptySet();
        }

        if (!rootDir.isDirectory()) {
            return Collections.emptySet();
        }

        if (!rootDir.canRead()) {
            return Collections.emptySet();
        }
        Set<File> result = new LinkedHashSet<>(8);
        doRetrieveMatchingFiles(rootDir, result);
        return result;
    }

    /**
     * 递归查询目录下的所有文件
     * @param dir
     * @param result
     * @throws IOException
     */
    private static final void doRetrieveMatchingFiles(File dir, Set<File> result) throws IOException {
        for (File content : listDirectory(dir)) {
            if (content.isDirectory()) {
                // 对可读目录执行递归操作, 获取所有文件
                if (content.canRead()) {
                    doRetrieveMatchingFiles(content, result);
                } else {
                    // ignore
                }
            }
            // 暂时忽略match的问题, 只要是文件, 就返回
            if (content.isFile()) {
                result.add(content);
            }
        }
    }

    /**
     * 列出指定目录下的所有文件
     * @param dir
     * @return
     */
    private static final File[] listDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return new File[0];
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        return files;
    }
}
