package net.aethersanctum.marchingoop.main

import java.awt.Color

import org.jocl.CL._
import org.jocl._

class GpuDemo(scene:Scene) {

  val kernelMain = "" +
    "__constant double4 BLACK = { 0, 0, 0, 1 };\n" +
    "__constant double4 WHITE = { 1, 1, 1, 1 };\n" +
    "\n" +
    "double4 checker(double4 position) {\n" +
    "  int px = position.x - floor(position.x) > 0.5 ? 1 :0;\n" +
    "  int py = position.y - floor(position.y) > 0.5 ? 1 :0;\n" +
    "  int pz = position.z - floor(position.z) > 0.5 ? 1 :0;\n" +
    "  return px ^ py ^ pz ? WHITE : BLACK;\n" +
    "}\n" +
    SceneItem.write(scene.objects) +
    "\n" +
    "__kernel void " +
    "sampleKernel(__global double *eye_p,\n" +
    "             __global double *look_p,\n" +
    "             __global double *results)\n" +
    "{\n" +
    "    int in = get_global_id(0);\n" +
    "" +
    "    double4 color;\n" +
    "    double4 eye =  vload4(0, eye_p);\n" +
    "    double4 look = vload4(in, look_p);\n" +
    "    double epsilon = 0.0001;\n" +
    "    int step_max = 500;\n" +
    "    double distance = epsilon*2;\n" +
    "    double march_ratio = 0.5;\n" +
    "    double4 here;\n" +
    "    int whoGotHit = -1;\n" +
    "    for (int step = 0; step < step_max && distance > epsilon; step++) {\n" +
    "      here = eye + (look * distance);\n" +
    "      double howFar = distance_of(1, here);\n" + // for a plane
    "      if (howFar < epsilon) {" +
    "        whoGotHit = 1;" +
    "        break;" +
    "      }\n" + // for a plane
    "      distance += howFar * march_ratio;\n" +
    "    }" +
    "    if (whoGotHit == 1) {\n" +
    "        color = checker(here);\n" +
    "    } else {\n" +
    "      color.x = fmaxf(0, 1- look.y*8);\n" + // sky
    "      color.z = fmaxf(0, 1- look.y*4);\n" +
    "    } " +
    "    int out = 4* in;\n" +
    "    results[out] = color.x;\n" +
    "    results[out+1] = color.y;\n" +
    "    results[out+2] = color.z;\n" +
    "}\n"

  val lookVectorsPerRow = new Array[Double](4 * scene.rendering.screenWidth)
  val colorVectorsPerRow = new Array[Double](4 * scene.rendering.screenWidth)

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
    val rowArraySize = singleVectorSize * scene.rendering.screenWidth

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
    for (row <- 0 until scene.rendering.screenHeight) {
      printf("\rrendering row %d/%d", row, scene.rendering.screenHeight)
      setupLookVectors(row)
      clEnqueueWriteBuffer(commandQueue, lookBuffer, CL_TRUE, 0, rowArraySize, lookPointer, 0, null, null)

      // Set the arguments for the kernel
      clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(eyeBuffer))
      clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(lookBuffer))
      clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(resultBuffer))

      // Set the work-item dimensions
      val global_work_size = Array[Long](scene.rendering.screenWidth)
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
    println("\nSaving file")
    scene.rendering.save("hello.png")
    println("\nDone")
  }

  private def bound(x: Float, min: Float, max:Float) = if (x < min) min else if (x > max) max else x

  private def paintResults(row: Int) = {
    for (x <- 0 until scene.rendering.screenWidth) {
      val color = new Color(
        bound(colorVectorsPerRow(x * 4 + 0).toFloat, 0, 1),
        bound(colorVectorsPerRow(x * 4 + 1).toFloat, 0, 1),
        bound(colorVectorsPerRow(x * 4 + 2).toFloat,0, 1))
      scene.rendering.setPixel(x, row, color)
    }
  }

  private def setupLookVectors(row: Int) = {
    for (x <- 0 until scene.rendering.screenWidth) {
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
    val rendering = new Rendering(640, 480)
    val camera = new Camera(rendering = rendering, location = Vector(0, 10, 0), lookAt = Vector.Z)
    val scene = new Scene(camera, rendering, List(
      Plane(Vector.Y, 0)
    ))
    val demo = new GpuDemo(scene)
    try {
      demo.run
    } catch {
      case e: CLException if e.getStatus == CL_BUILD_PROGRAM_FAILURE => {
        println(e.getMessage)
      }
      case e => throw e
    }
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
