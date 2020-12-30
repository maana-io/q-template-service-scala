package io.maana.common

import org.slf4j.LoggerFactory

/**
  * Logger that doesn't invoke computation for arguments if low logging levels are disabled.
  * This way if there's expensive computation in debug logging, it will not be executed when debug logging is off
  * With large requests/responses and/or high frequency this *was* invisible tax on both CPU and memory
  * @param underlying underlying slf4j logger
  */
class Logger(val underlying: org.slf4j.Logger) {
  @inline def trace(msg: => String): Unit = if (underlying.isTraceEnabled()) underlying.trace(msg)

  @inline def trace(format: => String, rest: Any): Unit =
    if (underlying.isTraceEnabled()) underlying.trace(format, rest)

  @inline def debug(msg: => String): Unit = if (underlying.isDebugEnabled()) underlying.debug(msg)

  @inline def info(msg: => String): Unit               = underlying.info(msg)
  @inline def info(format: => String, rest: Any): Unit = underlying.info(format, rest)

  @inline def warn(msg: => String): Unit               = underlying.warn(msg)
  @inline def warn(format: => String, rest: Any): Unit = underlying.warn(format, rest)

  @inline def error(msg: => String): Unit               = underlying.error(msg)
  @inline def error(format: => String, rest: Any): Unit = underlying.error(format, rest)
}

object Logger {
  def apply(clazz: Class[_]): Logger = new Logger(LoggerFactory.getLogger(clazz))
}
