package com.github.balloonupdate.util

/**
 * 网速采样
 * @param samplingPeriod 采样间隔，在采样间隔内重复提交不会改变获取到的速度
 * @param firstSamplingInterval 首次采样延迟，避免多个小文件下载时速度一直显示为0
 */
class SpeedSampler(val samplingPeriod: Int, firstSamplingInterval: Int)
{
    var last = System.currentTimeMillis() - (firstSamplingInterval - 100)
    var bytesSinceLastSampling = 0L
    var speedCache = 0L

    /**
     * 进行采样
     * @param bytes 本次传输了到了多少字节
     * @return 速度是否已经更新
     */
    fun sample(bytes: Long): Boolean
    {
        this.bytesSinceLastSampling += bytes

        val now = System.currentTimeMillis()
        if (now - last <= samplingPeriod)
            return false

        speedCache = (bytesSinceLastSampling.toDouble() / (now - last) * samplingPeriod).toLong()
        last = now
        bytesSinceLastSampling = 0
        return true
    }

    /**
     * 获取当前速度，单位字节
     */
    fun speed(): Long
    {
        return speedCache
    }


}