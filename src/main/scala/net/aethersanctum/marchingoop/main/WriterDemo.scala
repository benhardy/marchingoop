package net.aethersanctum.marchingoop.main

import net.aethersanctum.marchingoop.main.Pigment.{Checker, RED, WHITE}

object WriterDemo {

    def main(args: Array[String]) {
      // for demo/test
      val scene = List(
        Plane(Vector.Y, -3, Checker(RED, WHITE)),
        Sphere(Vector.origin, 3, WHITE)
      )
      println(SceneEntity.write(scene))
    }


}