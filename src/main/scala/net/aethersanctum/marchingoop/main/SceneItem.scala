package net.aethersanctum.marchingoop.main

trait SceneItem {
  val id = SceneItem.nextId
  def name: String
  def supportingDeclarations: List[String] = Nil
  def distanceFunctionName = name + "_distance"
  def distanceFunctionBody: String
  def distanceFunction: String = s"double ${distanceFunctionName}(double4 point) {\n    ${distanceFunctionBody}\n}\n";
}

object SceneItem {
  private var maxId = 0

  def nextId = {
    maxId = maxId + 1
    maxId
  }

  def max = maxId

  def write(items: Seq[SceneItem]) = {
    val buf = new StringBuilder
    buf.append(s"__constant scene_max_item_id = ${maxId};\n")
    buf.append(s"__constant int scene_top_level_ids[] = {${items.map(_.id).mkString(", ")}};\n")
    buf.append(s"__constant int scene_top_level_count = ${items.size};\n\n")
    items.foreach(item => {
      item.supportingDeclarations.foreach(dec =>
        buf.append(dec)
      )
      buf.append(item.distanceFunction)
    })
    buf.append("double distance_of(int objectId, double4 pos) {\n")
    buf.append("   switch(objectId) {\n")
    items.foreach(item => {
      buf.append(s"       case ${item.id}: return ${item.distanceFunctionName}(pos);\n")
    })
    buf.append("   }\n")
    buf.append("}")
    buf.toString()
  }
}

case class Plane(normal:Vector, offset:Double) extends SceneItem {
  override def name = "plane" + id
  override def supportingDeclarations = List(
    s"__constant double4 ${name}_normal = {${normal.x}, ${normal.y}, ${normal.z}, 0};\n",
    s"__constant double ${name}_offset = ${offset};\n"
  )
  override def distanceFunctionBody = s"return dot(${name}_normal, point) + ${name}_offset;"
}

case class Sphere(center:Vector, radius:Double) extends SceneItem {
  override def name = "sphere" + id
  override def supportingDeclarations = List(
    s"__constant double4 ${name}_center = {${center.x}, ${center.y}, ${center.z}, 0};\n",
    s"__constant double ${name}_radius = ${radius};\n"
  )
  override def distanceFunctionBody = s"return distance(${name}_center, point) - ${name}_radius;"
}

