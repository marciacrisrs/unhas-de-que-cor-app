package br.com.unhasdequecor.data.vision.nail

import io.mockk.MockKAnswerScope

/** MockK 1.14 only ships first/second/thirdArg; Bitmap.getPixels stubs need the rest. */
internal inline fun <reified T : Any> MockKAnswerScope<*, *>.fourthArg(): T = invocation.args[3] as T
internal inline fun <reified T : Any> MockKAnswerScope<*, *>.fifthArg(): T = invocation.args[4] as T
internal inline fun <reified T : Any> MockKAnswerScope<*, *>.sixthArg(): T = invocation.args[5] as T
internal inline fun <reified T : Any> MockKAnswerScope<*, *>.seventhArg(): T = invocation.args[6] as T
