package net.sansa_stack.rdf.common.kryo.jena

import java.nio.ByteBuffer

import com.esotericsoftware.kryo.io.{Input, Output}

object ByteArrayUtils {
  def write(output: Output, bytes: Array[Byte]): Unit = {
    output.write(bytes.length)
    output.write(bytes)
  }

  def write(output: Output, bytes: Array[Byte], offset: Int, length: Int): Unit = {
    output.write(length)
    output.write(bytes, offset, length)
  }

  def read(input: Input): Array[Byte] = {
    val len = input.readInt
    val result = input.readBytes(len)
    result
  }
}
