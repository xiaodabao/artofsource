package org.example.simple.spring.resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Simple interface for objects that are sources for an {@link InputStream}.
 * 用作以InputStream作为源的对象的简单接口, 简单来说, source object 中存在一个InputStream对象, 此方法用来返回InputStream
 * <p>This is the base interface for Spring's more extensive {@link org.springframework.core.io.Resource} interface.
 * 当前接口是 Spring Resource 接口的 基本接口, Spring Resource接口继承了 InputStreamSource
 * */
public interface InputStreamSource {

    /**
     * 获取InputStream对象, 将不同的实现(文件、网络、byte数组等)统一抽象成InputStream
     * @return
     * @throws IOException
     */
    InputStream getInputStream() throws IOException;
}
