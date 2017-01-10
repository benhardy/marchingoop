package net.aethersanctum.marchingoop.main

import scala.math._


case class Vector(x: Double, y: Double, z: Double) {
  def *(k: Double) = Vector(k * x, k * y, k * z)

  def /(k: Double) = this * (1 / k)

  def -(v2: Vector) = Vector(x - v2.x, y - v2.y, z - v2.z)

  def +(v2: Vector) = Vector(x + v2.x, y + v2.y, z + v2.z)

  def dot(v2: Vector) = x * v2.x + y * v2.y + z * v2.z

  def mag = sqrt(x * x + y * y + z * z)

  def mag2 = x * x + y * y + z * z

  def norm: Vector = {
    val m = mag
    val div = if (m == 0) java.lang.Double.MAX_VALUE else 1.0 / m
    this * div
  }

  def cross(v2: Vector) = {
    Vector(y * v2.z - z * v2.y, z * v2.x - x * v2.z, x * v2.y - y * v2.x)
  }

  def unary_- = Vector(-x, -y, -z)

  def abs = Vector(math.abs(x), math.abs(y), math.abs(z))

  def max: Double = math.max(x, math.max(y, z))

  def min: Double = math.min(x, math.min(y, z))

  def max(n: Double): Vector = Vector(math.max(x, n), math.max(y, n), math.max(z, n))

  def max(v: Vector): Vector = Vector(math.max(x, v.x), math.max(y, v.y), math.max(z, v.z))

  def min(n: Double): Vector = Vector(math.min(x, n), math.min(y, n), math.min(z, n))

  def min(v: Vector): Vector = Vector(math.min(x, v.x), math.min(y, v.y), math.min(z, v.z))

  def rotateAboutX(radians: Double): Vector = {
    val s = sin(radians)
    val c = cos(radians)
    Vector(x, c * y - s * z, s * y + c * z)
  }

  def rotateAboutY(radians: Double): Vector = {
    val s = sin(radians)
    val c = cos(radians)
    Vector(c * x - s * z, y, s * x + c * z)
  }

  def rotateAboutZ(radians: Double): Vector = {
    val s = sin(radians)
    val c = cos(radians)
    Vector(c * x - s * y, s * x + c * y, z)
  }
}

object Vector {
  val X = Vector(1, 0, 0)
  val Y = Vector(0, 1, 0)
  val Z = Vector(0, 0, 1)
  val origin = Vector(0, 0, 0)
}