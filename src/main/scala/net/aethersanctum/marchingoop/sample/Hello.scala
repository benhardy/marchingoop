package net.aethersanctum.marchingoop.sample

import java.awt.Color.{BLACK, WHITE}

import net.aethersanctum.marchingoop.main.Rendering

object Hello {
  def main(args: Array[String]) = {
    val rendering = new Rendering(640, 480)

    for (yPixel <- 0 until rendering.screenHeight) {
      for (xPixel <- 0 until rendering.screenWidth) {
        val bit = ((xPixel ^ yPixel) >> 5) & 1
        val color = if (bit > 0) WHITE else BLACK
        rendering.setPixel(xPixel, yPixel, color)
      }
    }
    rendering.save("hello.png")
  }
}
