package net.aethersanctum.marchingoop.sample

import java.awt.Color.{BLACK, WHITE, BLUE}

import net.aethersanctum.marchingoop.main.Vector
import net.aethersanctum.marchingoop.main.Vector.{Y, Z}
import net.aethersanctum.marchingoop.main.{Camera, Rendering}

object Hello {
  def main(args: Array[String]) = {
    val rendering = new Rendering(640, 480)

    val location = Y * 10
    val looking = Z
    val camera = new Camera(rendering, location, looking)
    for (yPixel <- 0 until rendering.screenHeight) {
      for (xPixel <- 0 until rendering.screenWidth) {
        val ray = camera.rayForPixel(xPixel, yPixel)
        val hit = location + Vector(ray.x, 0, ray.z) * location.y / ray.y
        val color = if (hit.z > 0) {
          checkerPattern(hit)
        } else {
          BLUE
        }
        rendering.setPixel(xPixel, yPixel, color)
      }
    }
    rendering.save("hello.png")
  }

  private def checkerPattern(hit: Vector) = {
    val xp = if (hit.x - math.floor(hit.x) < 0.5) 0 else 1
    val yp = if (hit.y - math.floor(hit.y) < 0.5) 0 else 1
    val zp = if (hit.z - math.floor(hit.z) < 0.5) 0 else 1
    val bit = xp ^ yp ^ zp
    if (bit > 0) WHITE else BLACK
  }
}
