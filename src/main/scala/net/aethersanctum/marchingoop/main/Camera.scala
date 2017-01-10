package net.aethersanctum.marchingoop.main


class Camera(rendering: Rendering, val location: Vector, lookAt: Vector, initialUp: Vector = Vector.Y) {
  private val wideness = 1.5
  private val right = initialUp cross lookAt
  private val up = lookAt cross right

  def rayForPixel(x:Int, y:Int): Vector = {
    val xp = x.toDouble / rendering.screenWidth - 0.5
    val yp = - (y.toDouble / rendering.screenHeight - 0.5) * rendering.aspectRatio

    (lookAt + ((right * xp) + (up * yp)) * wideness).norm
  }
}
