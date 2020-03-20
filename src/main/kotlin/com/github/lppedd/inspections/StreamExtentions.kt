package com.github.lppedd.inspections

import java.util.stream.Stream

internal operator fun <T> Stream<T>.plus(other: Stream<T>) =
  Stream.concat(this, other)

@Suppress("UNCHECKED_CAST")
internal fun <T, R> Stream<T>.filterIsInstance(klass: Class<R>): Stream<R> =
  filter(klass::isInstance) as Stream<R>
