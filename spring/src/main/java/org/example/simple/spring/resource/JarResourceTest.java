package org.example.simple.spring.resource;

import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipException;

public class JarResourceTest {

    public static void main(String[] args) throws IOException {
        Resource[] resources = getResource("org/springframework/core/");

        Arrays.stream(resources).forEach(r -> {
            try {
                URL url = r.getURL();
                System.out.println(url.getProtocol() + url.getPath());
                System.out.println(r.getClass().getName());
            } catch (Exception e) {

            }
        });
    }

    private static Resource[] getResource(String basePackage) throws IOException {

        // 根据basePackage先获取目录级资源
        Set<Resource> rootDirResources = doFindAllClassPathResource(basePackage);

        Set<Resource> result = new LinkedHashSet<>(16);
        for (Resource rootDirResource : rootDirResources) {
            // 这里根据rootDirResource开始加载其下的classResource
            Set<Resource> fileResources = doFindPathMatchingJarResources(rootDirResource, rootDirResource.getURL());
            result.addAll(fileResources);
        }

        return result.toArray(new Resource[0]);
    }

    /**
     * 查找指定路径, 并将其转为URLResource, 路径可能在多个位置存在, 所以返回Set
     * @param path
     * @return
     * @throws IOException
     */
    private static Set<Resource> doFindAllClassPathResource(String path) throws IOException {
        Set<Resource> result = new LinkedHashSet<>(16);

        ClassLoader cl = JarResourceTest.class.getClassLoader();
        Enumeration<URL> resourceUrls = cl.getResources(path);

        while (resourceUrls.hasMoreElements()) {
            URL url = resourceUrls.nextElement();
            result.add(convertClassLoaderURL(url));
        }
        return result;
    }

    private static Resource convertClassLoaderURL(URL url) {
        return new UrlResource(url);
    }

    /**
     * 加载Jar包中rootDirResource中的class文件
     * @param rootDirResource
     * @param rootDirURL
     * @return
     * @throws IOException
     */
    private static Set<Resource> doFindPathMatchingJarResources(Resource rootDirResource, URL rootDirURL) throws IOException {

        URLConnection con = rootDirURL.openConnection();
        JarFile jarFile;
        String jarFileUrl;
        String rootEntryPath;
        boolean closeJarFile;

        if (con instanceof JarURLConnection) {
            JarURLConnection jarCon = (JarURLConnection) con;
            ResourceUtils.useCachesIfNecessary(jarCon);

            jarFile = jarCon.getJarFile();
            jarFileUrl = jarCon.getJarFileURL().toExternalForm();
            JarEntry jarEntry = jarCon.getJarEntry();
            rootEntryPath = (jarEntry != null ? jarEntry.getName() : "");
            closeJarFile = !jarCon.getUseCaches();
        } else {
            String urlFile = rootDirURL.getFile();
            try {
                int separatorIndex = urlFile.indexOf(ResourceUtils.WAR_URL_SEPARATOR);
                if (separatorIndex == -1) {
                    separatorIndex = urlFile.indexOf(ResourceUtils.JAR_URL_SEPARATOR);
                }
                if (separatorIndex != -1) {
                    jarFileUrl = urlFile.substring(0, separatorIndex);
                    rootEntryPath = urlFile.substring(separatorIndex + 2);
                    jarFile = getJarFile(jarFileUrl);
                } else {
                    jarFile = new JarFile(urlFile);
                    jarFileUrl = urlFile;
                    rootEntryPath = "";
                }
                closeJarFile = true;
            } catch (ZipException ex) {
                return Collections.emptySet();
            }
        }

        try {
            if (StringUtils.hasLength(rootEntryPath) && !rootEntryPath.endsWith("/")) {
                rootEntryPath = rootEntryPath + "/";
            }

            Set<Resource> result = new LinkedHashSet<>(8);
            // 迭代jarFile.entries, 通过createRelative转化为UrlResource(因为rootDirResource是UrlResource的实例)
            for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                String entryPath = entry.getName();
                if (entryPath.startsWith(rootEntryPath)) {
                    String relativePath = entryPath.substring(rootEntryPath.length());
                    if (relativePath.endsWith("class")) {
                        result.add(rootDirResource.createRelative(relativePath));
                    }
                }
            }
            return result;
        } finally {
            if (closeJarFile) {
                jarFile.close();
            }
        }
    }

    private static JarFile getJarFile(String jarFileUrl) throws IOException {
        if (jarFileUrl.startsWith(ResourceUtils.FILE_URL_PREFIX)) {
            try {
                return new JarFile(ResourceUtils.toURI(jarFileUrl).getSchemeSpecificPart());
            } catch (URISyntaxException ex) {
                return new JarFile(jarFileUrl.substring(ResourceUtils.FILE_URL_PREFIX.length()));
            }
        } else {
            return new JarFile(jarFileUrl);
        }
    }
}
