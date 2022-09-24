package com.github.balloonupdate.util

import java.util.concurrent.atomic.AtomicLong

/**
 * 网速采样
 * @param samplingPeriod 采样间隔，在采样间隔内重复提交不会改变获取到的速度
 * @param firstSamplingInterval 首次采样延迟，避免多个小文件下载时速度一直显示为0
 */
class SpeedSampler(val samplingPeriod: Int, firstSamplingInterval: Int)
{
    var last = AtomicLong(System.currentTimeMillis() - (firstSamplingInterval - 100))
    var bytesSinceLastSampling = AtomicLong(0L)
    var speedCache = AtomicLong(0L)

    /**
     * 进行采样
     * @param bytes 本次传输了到了多少字节
     * @return 速度是否已经更新
     */
    fun sample(bytes: Long): Boolean
    {
        this.bytesSinceLastSampling.addAndGet(bytes)

        val now = System.currentTimeMillis()
        if (now - last.get() <= samplingPeriod)
            return false

        speedCache.set((bytesSinceLastSampling.toDouble() / (now - last.get()) * samplingPeriod).toLong())
        last.set(now)
        bytesSinceLastSampling.set(0)
        return true
    }

    /**
     * 获取当前速度，单位字节
     */
    fun speed(): Long
    {
        return speedCache.get()
    }


}