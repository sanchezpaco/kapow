package com.comicify.feature.reader.data

import android.os.ParcelFileDescriptor
import net.sf.sevenzipjbinding.IInStream
import net.sf.sevenzipjbinding.ISeekableStream
import java.io.FileInputStream
import java.nio.ByteBuffer

class DescriptorInStream(private val descriptor: ParcelFileDescriptor) : IInStream {

    private val channel = FileInputStream(descriptor.fileDescriptor).channel
    private var position = 0L

    @Synchronized
    override fun read(data: ByteArray): Int {
        val read = channel.read(ByteBuffer.wrap(data), position)
        if (read <= 0) return 0
        position += read
        return read
    }

    @Synchronized
    override fun seek(offset: Long, origin: Int): Long {
        position = when (origin) {
            ISeekableStream.SEEK_SET -> offset
            ISeekableStream.SEEK_CUR -> position + offset
            else -> channel.size() + offset
        }
        return position
    }

    @Synchronized
    override fun close() {
        channel.close()
        descriptor.close()
    }
}
