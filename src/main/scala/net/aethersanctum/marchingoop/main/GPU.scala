package net.aethersanctum.marchingoop.main

import java.awt.Color

import net.aethersanctum.marchingoop.main.Pigment.{Checker, WHITE}
import org.jocl.CL._
import org.jocl._

class GpuDemo(scene:Scene, rendering:Rendering) {

  val constants = "" +
    "__constant double epsilon = 0.0001;\n" +
    "__constant double normal_epsilon = 0.0000001;\n" +
    "__constant int step_max = 1000;\n" +
    "__constant double march_ratio = 0.8;\n" +
    "__constant double4 NO_NORMAL = {0.0, 0.0, 0.0, 1.0};\n" +
    "double distanceForNormal(double4 eye, double4 look, int whoGotHit, double distMax);\n" +
    "double4 calculateNormal(double4 eye, double4 look, int whoGotHit, double distMax);\n" +
    "double4 march(double4 eye, double4 look);" +
    "int findAnyCollision(double4 eye, double4 look, double max_dist);\n"

  val normalCalculators = "" +
    "double distanceForNormal(double4 eye, double4 look, int whoGotHit, double distMax) {\n" +
    "    double distance = epsilon * 2;\n" +
    "    double4 here;\n" +
    "    for (int step = 0; step < step_max && distance > epsilon && distance <distMax; step++) {\n" +
    "      here = eye + (look * distance);\n" +
    "      double howFar = distance_of(whoGotHit, here);\n" +
    "      if (howFar < epsilon) {\n" +
    "        return distance;\n" +
    "      }\n" +
    "      distance += howFar * march_ratio;\n" +
    "    }\n" +
    "    return -1;\n" +
    "}\n" +
    "double4 calculateNormal(double4 eye, double4 look, int whoGotHit, double distMax) {\n" +
    "      double4 slightlyLeft =    { look.x - normal_epsilon, look.y, look.z, 0 };\n" +
    "      double4 slightlyRight =   { look.x + normal_epsilon, look.y, look.z, 0 };\n" +
    "      double4 slightlyDown =    { look.x, look.y - normal_epsilon, look.z, 0 };\n" +
    "      double4 slightlyUp =      { look.x, look.y + normal_epsilon, look.z, 0 };\n" +
    "      double4 slightlyBack =    { look.x, look.y, look.z - normal_epsilon, 0 };\n" +
    "      double4 slightlyForward = { look.x, look.y, look.z + normal_epsilon, 0 };\n" +
    "      double leftDist =    distanceForNormal(eye, slightlyLeft, whoGotHit, distMax);\n" +
    "      double rightDist =   distanceForNormal(eye, slightlyRight,whoGotHit, distMax);\n" +
    "      double upDist =      distanceForNormal(eye, slightlyUp,   whoGotHit, distMax);\n" +
    "      double downDist =    distanceForNormal(eye, slightlyDown, whoGotHit, distMax);\n" +
    "      double backDist =    distanceForNormal(eye, slightlyBack, whoGotHit, distMax);\n" +
    "      double forwardDist = distanceForNormal(eye, slightlyForward, whoGotHit, distMax);\n" +
    "      //if (leftDist<0 || rightDist <0 || upDist < 0 || downDist < 0 || backDist < 0 || forwardDist < 0) {\n" +
    "      //  return NO_NORMAL;\n" +
    "      //}\n" +
    "      double4 normalish = {rightDist-leftDist, upDist-downDist, forwardDist - backDist, 0};\n" +
    "      return normalize(normalish);\n" +
    "}\n"

  val collisionFinderOnly = "" +
    "int findAnyCollision(double4 eye, double4 look, double max_dist) {" +
    "    double distance = epsilon*100;\n" +
    "    double4 here;\n" +
    "    double bestCloseness = 100000;\n" +
    "    for (int step = 0; step < step_max && distance < 100000; step++) {\n" +
    "      here = eye + (look * distance);" +
    "      double closeness;\n" +
    "      for (int index = 0; index < scene_top_level_count; index++) {\n" +
    "        int objectId = scene_top_level_ids[index];\n" +
    "        closeness = distance_of(objectId, here);\n" +
    "        if (closeness < epsilon) {\n" +
    "          return objectId;\n" +
    "        }\n" +
    "        if (closeness < bestCloseness) {\n" +
    "          bestCloseness = closeness;\n" +
    "        }\n" +
    "      }\n" +
    "      distance += bestCloseness * march_ratio;\n" +
    "    }\n" +
    "    return -1;\n" +
    "}"

  val marcher = "" +
    "double4 march(double4 eye, double4 look) {" +
    "    double4 color = {0,0,0,0};\n" +
    "    double distance = epsilon*2;\n" +
    "    double4 here;\n" +
    "    int closestObject = -1;\n" +
    "    int whoGotHit = -1;\n" +
    "    double bestCloseness = 100000;\n" +
    "    for (int step = 0; step < step_max && distance > epsilon; step++) {\n" +
    "      here = eye + (look * distance);" +
    "      double closeness;\n" +
    "      for (int index = 0; index < scene_top_level_count; index++) {\n" +
    "        int objectId = scene_top_level_ids[index];\n" +
    "        closeness = distance_of(objectId, here);\n" +
    "        if (closeness < bestCloseness) {\n" +
    "          closestObject = objectId;\n" +
    "          bestCloseness = closeness;\n" +
    "          if (closeness < epsilon) {\n" +
    "            whoGotHit = objectId;\n" +
    "            break;\n" +
    "          }\n" +
    "        }\n" +
    "      }\n" +
    "      distance += bestCloseness * march_ratio;\n" +
    "    }\n" +
    "    if (whoGotHit > 0) {\n" +
    "      double4 norm = calculateNormal(eye, look, whoGotHit, distance *1.2);\n" +
    "      double4 illum = total_illumination(here, norm);\n"+
    "      color = illum * pigment_of(whoGotHit, here);\n" +
    "    }\n" +
    "    return color;\n" +
    "}"

  val kernelMain = "" +
    constants +
    SceneEntity.write(scene.objects) +
    SceneEntity.writeLights(scene.lights) +
    normalCalculators +
    collisionFinderOnly +
    marcher +
    "\n" +
    "__kernel void " +
    "sampleKernel(__global double *eye_p,\n" +
    "             __global double *look_p,\n" +
    "             __global double *results)\n" +
    "{\n" +
    "    int in = get_global_id(0);\n" +
    "" +
    "    double4 eye =  vload4(0, eye_p);\n" +
    "    double4 look = vload4(in, look_p);\n" +
    "    double4 color = march(eye, look);" +
    "    int out = 4* in;\n" +
    "    results[out] = color.x;\n" +
    "    results[out+1] = color.y;\n" +
    "    results[out+2] = color.z;\n" +
    "}\n"

  println(kernelMain)

  val lookVectorsPerRow = new Array[Double](4 * rendering.screenWidth)
  val colorVectorsPerRow = new Array[Double](4 * rendering.screenWidth)

  def run = generate(kernelMain)

  def generate(source:String) = {
    val cameraLocation = scene.camera.location
    val eyeVector: Array[Double] = Array(cameraLocation.x, cameraLocation.y, cameraLocation.z, 1.0)

    val eyePointer = Pointer.to(eyeVector)
    val lookPointer = Pointer.to(lookVectorsPerRow)
    val resultPointer = Pointer.to(colorVectorsPerRow)

    val contextSetup = new ContextSetup
    val context = contextSetup.context

    // Create a command-queue for the selected device
    val commandQueue = clCreateCommandQueue(context, contextSetup.device, 0, null)

    // Allocate the memory objects for the input- and output data
    val memObjects = new Array[cl_mem](3)
    val singleVectorSize = Sizeof.cl_double * 4
    val rowArraySize = singleVectorSize * rendering.screenWidth

    val eyeBuffer = clCreateBuffer(context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR, singleVectorSize, eyePointer, null)
    val lookBuffer = clCreateBuffer(context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR, rowArraySize, lookPointer, null)
    val resultBuffer = clCreateBuffer(context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR, rowArraySize, resultPointer, null)

    // Create the program from the source code
    val program = clCreateProgramWithSource(context, 1, Array[String](source), null, null)

    // Build the program
    clBuildProgram(program, 0, null, null, null, null)

    // Create the kernel
    val kernel = clCreateKernel(program, "sampleKernel", null)

    // Write the inputs
    clEnqueueWriteBuffer(commandQueue, eyeBuffer, CL_TRUE, 0, singleVectorSize, eyePointer, 0, null, null)
    for (row <- 0 until rendering.screenHeight) {
      printf("\rrendering row %d/%d", row, rendering.screenHeight)
      setupLookVectors(row)
      clEnqueueWriteBuffer(commandQueue, lookBuffer, CL_TRUE, 0, rowArraySize, lookPointer, 0, null, null)

      // Set the arguments for the kernel
      clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(eyeBuffer))
      clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(lookBuffer))
      clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(resultBuffer))

      // Set the work-item dimensions
      val global_work_size = Array[Long](rendering.screenWidth)
      val local_work_size = Array[Long](1)

      // Execute the kernel
      clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, global_work_size, local_work_size, 0, null, null)

      // Read the output data
      clEnqueueReadBuffer(commandQueue, resultBuffer, CL_TRUE, 0, rowArraySize, resultPointer, 0, null, null)
      paintResults(row)

    }
    println("\nCleaning up openCL stuff")
    // Release kernel, program, and memory objects
    clReleaseMemObject(eyeBuffer)
    clReleaseMemObject(lookBuffer)
    clReleaseMemObject(resultBuffer)
    clReleaseKernel(kernel)
    clReleaseProgram(program)
    clReleaseCommandQueue(commandQueue)
    clReleaseContext(context)
    println("\nDone")
  }

  private def bound(x: Float, min: Float, max:Float) = if (x < min) min else if (x > max) max else x

  private def paintResults(row: Int) = {
    for (x <- 0 until rendering.screenWidth) {
      val color = new Color(
        bound(colorVectorsPerRow(x * 4 + 0).toFloat, 0, 1),
        bound(colorVectorsPerRow(x * 4 + 1).toFloat, 0, 1),
        bound(colorVectorsPerRow(x * 4 + 2).toFloat,0, 1))
      rendering.setPixel(x, row, color)
    }
  }

  private def setupLookVectors(row: Int) = {
    for (x <- 0 until rendering.screenWidth) {
      val looking = scene.camera.rayForPixel(x, row)
      lookVectorsPerRow(x * 4 + 0) = looking.x
      lookVectorsPerRow(x * 4 + 1) = looking.y
      lookVectorsPerRow(x * 4 + 2) = looking.z
    }
  }
}

object GpuDemo {
  /**
    * The entry point of this sample
    *
    * @param args Not used
    */
  def main(args: Array[String]) = {

    val rendering = new Rendering(400, 300)
    val camera = new Camera(rendering = rendering, location = Vector(0, 10, -10), lookAt = Vector(0,-10,10).norm)
    val MAGENTA = Pigment.RGB(1,0,1)
    val scene = new Scene(camera,
      objects = List(
        Plane(Vector.Y, 0, Checker(MAGENTA, WHITE)),
        RadialRidge(Sphere(Vector(-1, 1, 2), 5, Pigment.RGB(1.0, 0.8, 0.6)))
      ),
      lights = Light(color = Vector(1,1,1) *5, location = Vector(20,20,-10)) ::
        Light(color = Vector(0.1, 0.1, 1)*1, location = Vector(-5, 0.1, -5)) :: Nil
    )
    val demo = new GpuDemo(scene, rendering)
    try {
      demo.run
    } catch {
      case e: CLException if e.getStatus == CL_BUILD_PROGRAM_FAILURE => {
        println(e.getMessage)
      }
    }
    println("\nSaving file")
    rendering.save("hello.png")
  }
}

class ContextSetup {
  // The platform, device type and device number
  // that will be used
  val platformIndex = 0
  val deviceType: Long = CL_DEVICE_TYPE_ALL
  val deviceIndex = 0

  // Enable exceptions and subsequently omit error checks in this sample
  CL.setExceptionsEnabled(true)

  // Obtain the number of platforms
  val numPlatformsArray = new Array[Int](1)
  clGetPlatformIDs(0, null, numPlatformsArray)
  val numPlatforms = numPlatformsArray(0)

  // Obtain a platform ID
  val platforms = new Array[cl_platform_id](numPlatforms)
  clGetPlatformIDs(platforms.length, platforms, null)
  val platform = platforms(platformIndex)

  // Initialize the context properties
  val contextProperties = new cl_context_properties()
  contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform)

  // Obtain the number of devices for the platform
  val numDevicesArray = new Array[Int](1)
  clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray)
  val numDevices = numDevicesArray(0)

  // Obtain a device ID
  val devices = new Array[cl_device_id](numDevices)
  clGetDeviceIDs(platform, deviceType, numDevices, devices, null)
  val device = devices(deviceIndex)

  // Create a context for the selected device
  val context = clCreateContext(
    contextProperties, 1, Array[cl_device_id](device),
    null, null, null)

}
