package com.comicify.feature.reader.data

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.FloatBuffer

private const val MODEL = "mlspike/lama.onnx"
private const val SIDE = 512
private const val WARMUP_RUNS = 1
private const val TIMED_RUNS = 3

@RunWith(AndroidJUnit4::class)
class InpaintBenchmark {

    @Test
    fun timeOneCrop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(context.filesDir, MODEL)
        assumeTrue(model.isFile)
        val environment = OrtEnvironment.getEnvironment()
        val loadStart = System.nanoTime()
        val session = environment.createSession(model.path, OrtSession.SessionOptions())
        val loadMs = (System.nanoTime() - loadStart) / 1_000_000
        val image = FloatArray(3 * SIDE * SIDE) { 0.5f }
        val mask = FloatArray(SIDE * SIDE) { if (it % SIDE in 200..300 && it / SIDE in 200..300) 1f else 0f }
        val timings = (0 until WARMUP_RUNS + TIMED_RUNS).map {
            val start = System.nanoTime()
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(image), longArrayOf(1, 3, SIDE.toLong(), SIDE.toLong())).use { i ->
                OnnxTensor.createTensor(environment, FloatBuffer.wrap(mask), longArrayOf(1, 1, SIDE.toLong(), SIDE.toLong())).use { m ->
                    session.run(mapOf("image" to i, "mask" to m)).use { }
                }
            }
            (System.nanoTime() - start) / 1_000_000
        }
        File(context.filesDir, "mlspike/inpaint_timing.json")
            .writeText("{\"load_ms\": $loadMs, \"run_ms\": $timings}\n")
    }
}
