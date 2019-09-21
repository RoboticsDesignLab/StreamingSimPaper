package net.nextlogic.airsim.paper

object Timer {
  def time[R](block: => R, label: String): R = {
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()
    println(s"Elapsed time for $label: " + (t1 - t0)/1000000.0 + "ms")
    result
  }
}
