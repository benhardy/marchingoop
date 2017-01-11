package net.aethersanctum.marchingoop.main

object WriterDemo {

    def main(args: Array[String]) {
      // for demo/test
      val scene = List(
        Plane(Vector.Y, -3)
      )
      println(SceneItem.write(scene))
    }


}