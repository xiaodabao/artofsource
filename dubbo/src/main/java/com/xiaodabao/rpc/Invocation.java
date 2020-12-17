package com.xiaodabao.rpc;

public interface Invocation {

    String getTargetServiceUniqueName();


    String getProtocolServiceKey();

    /**
     * 获取方法名
     * @return
     */
    String getMethodName();


    /**
     * 获取调用的参数
     * @return
     */
    Object[] getArguments();
}
