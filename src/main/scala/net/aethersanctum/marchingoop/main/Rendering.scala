package net.aethersanctum.marchingoop.main

import java.awt.{Color, Graphics2D}
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO


class Rendering(val screenWidth:Int, val screenHeight:Int) {
  val image = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_ARGB)

  val ctx = image.getGraphics.asInstanceOf[Graphics2D]

  def save(filename: String) {
    ImageIO.write(image, "png", new File(filename))
  }

  def setPixel(x:Int, y:Int, color:Color) {
    ctx.setColor(color)
    ctx.fillRect(x, y, 1, 1)
  }
}

