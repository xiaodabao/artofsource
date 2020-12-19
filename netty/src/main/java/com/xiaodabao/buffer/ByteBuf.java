package com.xiaodabao.buffer;

import com.xiaodabao.common.util.ByteProcessor;
import com.xiaodabao.common.util.ReferenceCounted;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;

/**
 * netty定义的对字节缓冲区的各种操作
 * get/set/read/write/index/copy/retain/slice等
 */
public abstract class ByteBuf implements ReferenceCounted, Comparable<ByteBuf> {

    /**
     * buffer的容量（单位：字节）
     * @return
     */
    public abstract int capacity();

    /**
     * 调整buffer的容量，如果newCapacity小于当前的capacity，buffer的内容会被截断。
     * 如果newCapacity大于当前的capacity，则扩大buffer空间至newCapacity
     * 如果大于maxCapacity会抛出异常
     * @param newCapacity
     * @return
     */
    public abstract ByteBuf capacity(int newCapacity);

    /**
     * buffer的容量上限
     * @return
     */
    public abstract int maxCapacity();

    /**
     * 返回创建这个buffer的分配器
     * @return
     */
    public abstract ByteBufAllocator alloc();

    /**
     *
     * @return
     */
    public abstract ByteBuf unwrap();

    /**
     * 是否NIO direct buffer 直接内存
     * @return
     */
    public abstract boolean isDirect();


    public abstract boolean isReadOnly();

    /**
     * 返回一个只读的buffer
     * @return
     */
    public abstract ByteBuf asReadOnly();

    /**
     * 此buffer目前可读索引
     * @return
     */
    public abstract int readerIndex();

    /**
     * 设置此buffer的可读索引  索引范围[0, writerIndex]
     * @param readerIndex
     * @return
     */
    public abstract ByteBuf readerIndex(int readerIndex);

    /**
     * 此buffer目前可写索引
     * @return
     */
    public abstract int writerIndex();

    /**
     * 设置此buffer的可写索引 索引范围[readerIndex, capacity]
     * @param writerIndex
     * @return
     */
    public abstract ByteBuf writerIndex(int writerIndex);

    /**
     * 同时设置读写索引
     * @param readerIndex
     * @param writerIndex
     * @return
     */
    public abstract ByteBuf setIndex(int readerIndex, int writerIndex);

    /**
     * buffer可读的字节长度  =  writerIndex - readerIndex
     * @return
     */
    public abstract int readableBytes();

    /**
     * buffer可写的字节长度 = capacity - writerIndex
     * @return
     */
    public abstract int writableBytes();

    /**
     * 最大可写字节长度 = maxCapacity - writerIndex
     * @return
     */
    public abstract int maxWritableBytes();

    /**
     * 快速写入， 不进行扩容/数据拷贝
     * @return
     */
    public int maxFastWritableBytes() {
        return writableBytes();
    }

    /**
     * buffer是否可读, 当writerIndex大于readerIndex时，说明有数据还未读完，代表buffer可读
     * @return
     */
    public abstract boolean isReadable();

    /**
     * buffer 是否可以读出size个字节， 即 writerIndex - readerIndex >= size
     * @param size
     * @return
     */
    public abstract boolean isReadable(int size);

    /**
     * buffer 是否可写 即 capacity > writerIndex
     * @return
     */
    public abstract boolean isWritable();

    /**
     * buffer是否可写size个字节，即 capacity - writerIndex > size
     * @param size
     * @return
     */
    public abstract boolean isWritable(int size);

    /**
     * 清空buffer，直接将readerIndex和writerIndex置为0
     * @return
     */
    public abstract ByteBuf clear();

    /**
     * 标记当前的readerIndex
     * @return
     */
    public abstract ByteBuf markReaderIndex();

    /**
     * 将readerIndex重新设置为标记时的值  和 markReaderIndex配合使用
     * 使用此方法可以重新读取已读过的数据
     * @return
     */
    public abstract ByteBuf resetReaderIndex();

    /**
     * 标记当前的writerIndex
     * @return
     */
    public abstract ByteBuf markWriterIndex();

    /**
     * 将writerIndex重新设置为标记时的值  和 markWriterIndex配合使用
     * 使用此方法可以覆盖标记后写入的数据
     * @return
     */
    public abstract ByteBuf resetWriterIndex();

    /**
     * 抛弃已读数据  已读数据已经没用了，但还占用着空间，通过将 [readerIndex, writerIndex] 的数据
     * 复制到 [0, writerIndex - readerIndex]中，达到高效利用空间的目的，避免扩容再次进行buffer分配
     * @return
     */
    public abstract ByteBuf discardReadBytes();


    public abstract ByteBuf discardSomeReadBytes();

    /**
     * 确保buffer中可以写入minWritableBytes个字节数据, 若不够，则进行扩容
     * @param minWritableBytes
     * @return
     */
    public abstract ByteBuf ensureWritable(int minWritableBytes);


    public abstract int ensureWritable(int minWritableBytes, boolean force);

    /**
     * 根据index获取各种基本数据类型的数据
     * 这些操作不会改变readerIndex和writerIndex
     * @param index
     * @return
     */
    public abstract boolean getBoolean(int index);
    public abstract byte getByte(int index);
    public abstract short getUnsignedByte(int index);
    public abstract short getShort(int index);
    public abstract short getShortLE(int index);
    public abstract int getUnsignedShort(int index);
    public abstract int getUnsignedShortLE(int index);
    public abstract int getMedium(int index);
    public abstract int getMediumLe(int index);
    public abstract int   getUnsignedMedium(int index);
    public abstract int   getUnsignedMediumLE(int index);
    public abstract int getInt(int index);
    public abstract int getIntLE(int index);
    public abstract long getUnsignedInt(int index);
    public abstract long getUnsignedIntLE(int index);
    public abstract long getLong(int index);
    public abstract long getLongLE(int index);
    public abstract char getChar(int index);
    public abstract float getFloat(int index);
    public float getFloatLE(int index) {
        return Float.intBitsToFloat(getIntLE(index));
    }
    public abstract double getDouble(int index);
    public double getDoubleLE(int index) {
        return Double.longBitsToDouble(getLongLE(index));
    }
    public abstract ByteBuf getBytes(int index, ByteBuf dst);
    public abstract ByteBuf getBytes(int index, ByteBuf dst, int length);
    public abstract ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length);
    public abstract ByteBuf getBytes(int index, byte[] dst);
    public abstract ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length);
    public abstract ByteBuf getBytes(int index, ByteBuffer dst);
    public abstract ByteBuf getBytes(int index, OutputStream out, int length) throws IOException;
    public abstract int getBytes(int index, GatheringByteChannel out, int length) throws IOException;
    public abstract int getBytes(int index, FileChannel out, long position, int length) throws IOException;
    public abstract CharSequence getCharSequence(int index, int length, Charset charset);

    /**
     * 将基本类型的数据设置到ByteBuf的index位置
     * 与set操作都是一一对应的
     * @param index
     * @param value
     * @return
     */
    public abstract ByteBuf setBoolean(int index, boolean value);
    public abstract ByteBuf setByte(int index, int value);
    public abstract ByteBuf setShort(int index, int value);
    public abstract ByteBuf setShortLE(int index, int value);
    public abstract ByteBuf setMedium(int index, int value);
    public abstract ByteBuf setMediumLE(int index, int value);
    public abstract ByteBuf setInt(int index, int value);
    public abstract ByteBuf setIntLE(int index, int value);
    public abstract ByteBuf setLong(int index, long value);
    public abstract ByteBuf setLongLE(int index, long value);
    public abstract ByteBuf setChar(int index, int value);
    public abstract ByteBuf setFloat(int index, float value);
    public ByteBuf setFloatLE(int index, float value) {
        return setIntLE(index, Float.floatToRawIntBits(value));
    }
    public abstract ByteBuf setDouble(int index, double value);
    public ByteBuf setDoubleLE(int index, double value) {
        return setLongLE(index, Double.doubleToRawLongBits(value));
    }
    public abstract ByteBuf setBytes(int index, ByteBuf src);
    public abstract ByteBuf setBytes(int index, ByteBuf src, int length);
    public abstract ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length);
    public abstract ByteBuf setBytes(int index, byte[] src);
    public abstract ByteBuf setBytes(int index, byte[] src, int srcIndex, int length);
    public abstract ByteBuf setBytes(int index, ByteBuffer src);
    public abstract int setBytes(int index, InputStream in, int length) throws IOException;
    public abstract int setBytes(int index, ScatteringByteChannel in, int length) throws IOException;
    public abstract int setBytes(int index, FileChannel in, long position, int length) throws IOException;
    public abstract ByteBuf setZero(int index, int length);
    public abstract int setCharSequence(int index, CharSequence sequence, Charset charset);

    /**
     * 以下是read方法，会改变readerIndex
     * 根据类型长度的不同，增加类型字节长度的值
     * @return
     */
    public abstract boolean readBoolean();
    public abstract byte readByte();
    public abstract short readUnsignedByte();
    public abstract short readShort();
    public abstract short readShortLE();
    public abstract int readUnsignedShort();
    public abstract int   readUnsignedShortLE();
    public abstract int   readMedium();
    public abstract int   readMediumLE();
    public abstract int   readUnsignedMedium();
    public abstract int   readUnsignedMediumLE();
    public abstract int   readInt();
    public abstract int   readIntLE();
    public abstract long  readUnsignedInt();
    public abstract long  readUnsignedIntLE();
    public abstract long  readLong();
    public abstract long  readLongLE();
    public abstract char  readChar();
    public abstract float readFloat();
    public float readFloatLE() {
        return Float.intBitsToFloat(readIntLE());
    }
    public abstract double readDouble();
    public double readDoubleLE() {
        return Double.longBitsToDouble(readLongLE());
    }
    public abstract ByteBuf readBytes(int length);
    public abstract ByteBuf readSlice(int length);
    public abstract ByteBuf readRetainedSlice(int length);
    public abstract ByteBuf readBytes(ByteBuf dst);
    public abstract ByteBuf readBytes(ByteBuf dst, int length);
    public abstract ByteBuf readBytes(ByteBuf dst, int dstIndex, int length);
    public abstract ByteBuf readBytes(byte[] dst);
    public abstract ByteBuf readBytes(byte[] dst, int dstIndex, int length);
    public abstract ByteBuf readBytes(ByteBuffer dst);
    public abstract ByteBuf readBytes(OutputStream out, int length) throws IOException;
    public abstract int readBytes(GatheringByteChannel out, int length) throws IOException;
    public abstract CharSequence readCharSequence(int length, Charset charset);
    public abstract int readBytes(FileChannel out, long position, int length) throws IOException;
    public abstract ByteBuf skipBytes(int length);

    /**
     * 以下是write方法，会改变writerIndex
     * 根据类型长度的不同，增加类型字节长度的值
     * @param value
     * @return
     */
    public abstract ByteBuf writeBoolean(boolean value);
    public abstract ByteBuf writeByte(int value);
    public abstract ByteBuf writeShort(int value);
    public abstract ByteBuf writeShortLE(int value);
    public abstract ByteBuf writeMedium(int value);
    public abstract ByteBuf writeMediumLE(int value);
    public abstract ByteBuf writeInt(int value);
    public abstract ByteBuf writeIntLE(int value);
    public abstract ByteBuf writeLong(long value);
    public abstract ByteBuf writeLongLE(long value);
    public abstract ByteBuf writeChar(int value);
    public abstract ByteBuf writeFloat(float value);
    public ByteBuf writeFloatLE(float value) {
        return writeIntLE(Float.floatToRawIntBits(value));
    }
    public abstract ByteBuf writeDouble(double value);
    public ByteBuf writeDoubleLE(double value) {
        return writeLongLE(Double.doubleToRawLongBits(value));
    }
    public abstract ByteBuf writeBytes(ByteBuf src);
    public abstract ByteBuf writeBytes(ByteBuf src, int length);
    public abstract ByteBuf writeBytes(ByteBuf src, int srcIndex, int length);
    public abstract ByteBuf writeBytes(byte[] src);
    public abstract ByteBuf writeBytes(byte[] src, int srcIndex, int length);
    public abstract ByteBuf writeBytes(ByteBuffer src);
    public abstract int writeBytes(InputStream in, int length) throws IOException;
    public abstract int writeBytes(ScatteringByteChannel in, int length) throws IOException;
    public abstract int writeBytes(FileChannel in, long position, int length) throws IOException;
    public abstract ByteBuf writeZero(int length);
    public abstract int writeCharSequence(CharSequence sequence, Charset charset);

    /**
     * 其他方法 等具体实现时再分析
     * @param fromIndex
     * @param toIndex
     * @param value
     * @return
     */
    public abstract int indexOf(int fromIndex, int toIndex, byte value);
    public abstract int bytesBefore(byte value);
    public abstract int bytesBefore(int length, byte value);
    public abstract int bytesBefore(int index, int length, byte value);
    public abstract int forEachByte(ByteProcessor processor);
    public abstract int forEachByte(int index, int length, ByteProcessor processor);
    public abstract int forEachByteDesc(ByteProcessor processor);
    public abstract int forEachByteDesc(int index, int length, ByteProcessor processor);
    public abstract ByteBuf copy();
    public abstract ByteBuf copy(int index, int length);
    public abstract ByteBuf slice();
    public abstract ByteBuf retainedSlice();
    public abstract ByteBuf slice(int index, int length);
    public abstract ByteBuf retainedSlice(int index, int length);
    public abstract ByteBuf duplicate();
    public abstract ByteBuf retainedDuplicate();
    public abstract int nioBufferCount();
    public abstract ByteBuffer nioBuffer();
    public abstract ByteBuffer nioBuffer(int index, int length);
    public abstract ByteBuffer internalNioBuffer(int index, int length);
    public abstract ByteBuffer[] nioBuffers();
    public abstract ByteBuffer[] nioBUffers(int index, int length);
    public abstract boolean hasArray();
    public abstract byte[] array();
    public abstract int arrayOffset();
    public abstract boolean hasMemoryAddress();
    public abstract long memoryAddress();
    public boolean isContiguous() {
        return false;
    }
    public abstract String toString(Charset charset);
    public abstract String toString(int index, int length, Charset charset);
    @Override
    public abstract int hashCode();
    @Override
    public abstract boolean equals(Object obj);
    @Override
    public abstract int compareTo(ByteBuf buffer);
    @Override
    public abstract String toString();
    @Override
    public abstract ByteBuf retain(int increment);

    @Override
    public abstract ByteBuf retain();

    @Override
    public abstract ByteBuf touch();

    @Override
    public abstract ByteBuf touch(Object hint);
    boolean isAccessible() {
        return refCnt() != 0;
    }

}

