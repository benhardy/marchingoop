package net.aethersanctum.marchingoop.main

import scala.collection.mutable

sealed trait SceneEntity {
  val id = SceneEntity.nextId
  def name: String = "entity" + id
  def declarations: List[String] = List(functionWhole)
  def functionName = name
  def functionBody: String
  def functionWhole: String
}

abstract class Pigment extends SceneEntity {
  override def functionWhole: String = s"double4 ${functionName}(double4 point) {\n    ${functionBody}\n}\n";
  override def name = "pigment_" + super.name
}

object Pigment {
  case class RGB(red:Double, green:Double, blue:Double) extends Pigment {
    override def declarations: List[String] =
      s"__constant double4 ${name}_rgb = { ${red}, ${green}, ${blue}, 1};\n" :: super.declarations

    override def functionBody = s"return ${name}_rgb;"
  }
  case class Checker(pigmentA: Pigment, pigmentB: Pigment) extends Pigment {
    override def declarations = pigmentA.declarations ++ pigmentB.declarations ++ super.declarations

    override def functionBody = "  int px = point.x - floor(point.x) > 0.5 ? 1 :0;\n" +
      "  int py = point.y - floor(point.y) > 0.5 ? 1 :0;\n" +
      "  int pz = point.z - floor(point.z) > 0.5 ? 1 :0;\n" +
      s"  return px ^ py ^ pz ? ${pigmentA.functionName}(point) : ${pigmentB.functionName}(point);"
  }
  case class Grid(pigmentA: Pigment) extends Pigment {
    override def declarations = pigmentA.declarations ++ super.declarations

    override def functionBody = "  double px = fabs(point.x - floor(point.x) - 0.5);\n" +
      "  double py = fabs(point.y - floor(point.y) - 0.5);\n" +
      "  double pz = fabs(point.z - floor(point.z) - 0.5);\n" +
      "  double least = pow(1- fminf(px, fminf(py, pz)) * 2, 4);\n" +
      s"  return ${pigmentA.functionName}(point) * least;"
  }
  val WHITE = RGB(1,1,1)
  val BLACK = RGB(0,0,0)
  val RED = RGB(1,0,0)
  implicit val DEFAULT = BLACK
}

case class Light(color:Vector, location:Vector) extends SceneEntity {
  override def name = "light_" + id

  override def functionBody: String = "" +
    s"double strength = 1 / pow(distance(point, ${name}_location), 2);\n" +
    s"return ${name}_color * strength;"

  override def declarations: List[String] =
    s"__constant double4 ${name}_color = { ${color.x}, ${color.y}, ${color.z}, 0};\n" ::
    s"__constant double4 ${name}_location = { ${location.x}, ${location.y}, ${location.z}, 0};\n" ::
       super.declarations

  def functionWhole: String = s"double4 ${functionName}_illumination(double4 point) {\n    ${functionBody}\n}\n";
}

abstract class SceneObject extends SceneEntity {
  def pigment:Pigment = Pigment.DEFAULT
  override def functionName = name + "_distance"
  def functionWhole: String = s"double ${functionName}(double4 point) {\n    ${functionBody}\n}\n";
}

object SceneEntity {
  private var maxId = 0

  def nextId = {
    maxId = maxId + 1
    maxId
  }

  def max = maxId

  def write(items: Seq[SceneObject]) = {
    val buf = new StringBuilder
    val done = mutable.Set[String]()
    buf.append(s"__constant int scene_max_item_id = ${maxId};\n")
    buf.append(s"__constant int scene_top_level_ids[] = {${items.map(_.id).mkString(", ")}};\n")
    buf.append(s"__constant int scene_top_level_count = ${items.size};\n\n")

    def writeOnce(dec: String) = {
      if (!done.contains(dec)) {
        buf.append(dec)
        done.add(dec)
      }
    }

    items.foreach(item => {
      item.declarations.foreach(writeOnce)
      writeOnce(item.functionWhole)
    })
    val defaultPigmentName = Pigment.DEFAULT.functionWhole
    if (!done.contains(defaultPigmentName)) {
      Pigment.DEFAULT.declarations.foreach(writeOnce)
      writeOnce(Pigment.DEFAULT.functionWhole)
    }

    // write switching functions
    buf.append("double distance_of(int objectId, double4 pos) {\n")
    buf.append("   switch(objectId) {\n")
    items.foreach(item => {
      buf.append(s"       case ${item.id}: return ${item.functionName}(pos);\n")
    })
    buf.append("   }\n")
    buf.append("}")
    buf.append("double4 pigment_of(int objectId, double4 pos) {\n")
    buf.append("   switch(objectId) {\n")
    items.foreach(item => {
      buf.append(s"       case ${item.id}: return ${item.pigment.functionName}(pos);\n")
    })
    buf.append(s"       default: return ${Pigment.DEFAULT.functionName}(pos);\n")
    buf.append("   }\n")
    buf.append("}")
    buf.toString()
  }
  def writeLights(lights:Seq[Light]): String = {
    val buf = new mutable.StringBuilder()
    for (light <-lights; decl <- light.declarations) {
      buf.append(decl)
    }
    buf.toString()
    buf.append("double4 total_illumination(double4 pos, double4 normal) {\n")
    buf.append("   double4 total = { 0,0,0,0};\n")
    buf.append("   double4 delta;\n")
    buf.append("   double dotp, dist;\n")
    lights.foreach(light => {
      buf.append(s"  delta = ${light.name}_location - pos;\n")
      buf.append(s"  dist = length(delta);\n")
      buf.append(s"  dotp = dot(normal, normalize(delta));\n")
      buf.append(s"  if (dotp > 0) {" +
        s"total += dotp * ${light.name}_color / (dist * dist); }\n")
    })
    buf.append(s"  return 1000 * total;\n")
    buf.append("}\n")
    buf.toString()
  }
}

case class Plane(normal:Vector, offset:Double
                 , override val pigment:Pigment) extends SceneObject {
  override def name = "plane" + id
  override def declarations = List(
    s"__constant double4 ${name}_normal = {${normal.x}, ${normal.y}, ${normal.z}, 0};\n",
    s"__constant double ${name}_offset = ${offset};\n"
  ) ++ pigment.declarations ++ super.declarations
  override def functionBody = s"return dot(${name}_normal, point) + ${name}_offset;"
}

case class Sphere(center:Vector, radius:Double
                  , override val pigment:Pigment) extends SceneObject {
  override def name = "sphere" + id
  override def declarations = List(
    s"__constant double4 ${name}_center = {${center.x}, ${center.y}, ${center.z}, 0};\n",
    s"__constant double ${name}_radius = ${radius};\n"
  ) ++ pigment.declarations ++ super.declarations
  override def functionBody = s"return distance(${name}_center, point) - ${name}_radius;"
}

