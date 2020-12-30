package io.maana

object Profile {

  def prof[A](str: String)(y: => A): A = {
    val start = System.currentTimeMillis()
    val res   = y
    val time  = System.currentTimeMillis() - start
    println(s"$str completed in $time ms")
    res
  }

}
