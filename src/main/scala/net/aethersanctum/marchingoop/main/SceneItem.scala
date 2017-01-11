package net.aethersanctum.marchingoop.main

import scala.collection.mutable

sealed trait SceneEntity {
  val id = SceneEntity.nextId
  def name: String = "entity" + id
  def supportingDeclarations: List[String] = Nil
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
    override def supportingDeclarations: List[String] = List(
      s"__constant double4 ${name}_rgb = { ${red}, ${green}, ${blue}, 1};\n"
    )
    override def functionBody = s"return ${name}_rgb;"
  }
  case class Checker(pigmentA: Pigment, pigmentB: Pigment) extends Pigment {
    override def supportingDeclarations = pigmentA.supportingDeclarations ++
      pigmentB.supportingDeclarations ++
      List(pigmentA.functionWhole, pigmentB.functionWhole)

    override def functionBody = "  int px = point.x - floor(point.x) > 0.5 ? 1 :0;\n" +
      "  int py = point.y - floor(point.y) > 0.5 ? 1 :0;\n" +
      "  int pz = point.z - floor(point.z) > 0.5 ? 1 :0;\n" +
      s"  return px ^ py ^ pz ? ${pigmentA.functionName}(point) : ${pigmentB.functionName}(point);"
  }
  val WHITE = RGB(1,1,1)
  val BLACK = RGB(0,0,0)
  val RED = RGB(1,0,0)
  implicit val DEFAULT = BLACK
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
    buf.append(s"__constant scene_max_item_id = ${maxId};\n")
    buf.append(s"__constant int scene_top_level_ids[] = {${items.map(_.id).mkString(", ")}};\n")
    buf.append(s"__constant int scene_top_level_count = ${items.size};\n\n")
    items.foreach(item => {
      item.supportingDeclarations.foreach(dec =>
        if (!done.contains(dec)) {
          buf.append(dec)
          done.add(dec)
        }
      )
      val whole = item.functionWhole
      if (!done.contains(whole)) {
        buf.append(whole)
        done.add(whole)
      }
    })
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
}

case class Plane(normal:Vector, offset:Double
                 , override val pigment:Pigment) extends SceneObject {
  override def name = "plane" + id
  override def supportingDeclarations = List(
    s"__constant double4 ${name}_normal = {${normal.x}, ${normal.y}, ${normal.z}, 0};\n",
    s"__constant double ${name}_offset = ${offset};\n"
  ) ++ pigment.supportingDeclarations ++ List(pigment.functionWhole)
  override def functionBody = s"return dot(${name}_normal, point) + ${name}_offset;"
}

case class Sphere(center:Vector, radius:Double
                  , override val pigment:Pigment) extends SceneObject {
  override def name = "sphere" + id
  override def supportingDeclarations = List(
    s"__constant double4 ${name}_center = {${center.x}, ${center.y}, ${center.z}, 0};\n",
    s"__constant double ${name}_radius = ${radius};\n"
  ) ++ pigment.supportingDeclarations ++ List(pigment.functionWhole)
  override def functionBody = s"return distance(${name}_center, point) - ${name}_radius;"
}

