package com.xiaodabao.common;

/**
 * 节点
 */
public interface Node {

    URL getUrl();


    boolean isAvailable();


    void destroy();
}
