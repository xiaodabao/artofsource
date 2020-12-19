package com.xiaodabao.buffer;

/**
 * ByteBuf分配器
 */
public interface ByteBufAllocator {

    /**
     * 分配一个ByteBuf
     * 这个buffer的类型是directBuffer还是heapBuffer，取决于具体的allocator实现
     * @return
     */
    ByteBuf buffer();

    /**
     * 根据指定的容量分配一个ByteBuf
     * @param initialCapacity
     * @return
     */
    ByteBuf buffer(int initialCapacity);

    /**
     * 分配一个ByteBuf,　同时指定初始容量和最大容量
     * @param initialCapacity
     * @param maxCapacity
     * @return
     */
    ByteBuf buffer(int initialCapacity, int maxCapacity);

    /**
     * 分配一个ByteBuf, 优先分配directBuffer
     * @return
     */
    ByteBuf ioBuffer();

    ByteBuf ioBuffer(int initialCapacity);

    ByteBuf ioBuffer(int initialCapacity, int maxCapacity);

    /**
     * 分配heapBuffer
     * @return
     */
    ByteBuf heapBuffer();

    ByteBuf heapBuffer(int initialCapacity);

    ByteBuf heapBuffer(int initialCapacity, int maxCapacity);

    /**
     * 分配directBuffer
     * @return
     */
    ByteBuf directBuffer();

    ByteBuf directBuffer(int initialCapacity);

    ByteBuf directBuffer(int initialCapacity, int maxCapacity);

    /**
     * 分配组合ByteBuf
     * @return
     */
    CompositeByteBuf compositeBuffer();

    CompositeByteBuf compositeBuffer(int maxNumComponents);

    CompositeByteBuf compositeHeapBuffer();

    CompositeByteBuf compositeHeapBuffer(int maxNumComponents);

    CompositeByteBuf compositeDirectBuffer();

    CompositeByteBuf compositeDirectBuffer(int maxNumComponents);

    /**
     * DirectBuffer是否是池化的
     * @return
     */
    boolean isDirectBufferPooled();

    /**
     * 根据minCapacity和maxCapacity计算新的capacity
     * @param minNewCapacity
     * @param maxCapacity
     * @return
     */
    int calculateNewCapacity(int minNewCapacity, int maxCapacity);
}
