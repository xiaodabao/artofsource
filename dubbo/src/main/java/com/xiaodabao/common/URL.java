package com.xiaodabao.common;

import java.io.Serializable;
import java.util.Map;

/**
 * 先空实现
 */
public class URL implements Serializable {


    /**
     *
     * @return
     */
    public String toIdentityString() {
        return "";
    }

    /**
     * The format of return value is '{group}/{interfaceName}:{version}'
     *
     * @return
     */
    public String getServiceKey() {
        return "";
    }

    public String getAddress() {
        return "";
    }

    public String getMethodParameter(String method, String key, String defaultValue) {
        return "";
    }

    public int getMethodParameter(String method, String key, int defaultValue) {
        return 1;
    }
}
