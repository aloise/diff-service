package name.aloise.assignment4c.server

import akka.stream._
import akka.stream.stage._
import akka.util.ByteString

/**
  * User: aloise
  * Date: 21.05.16
  * Time: 23:57
  */
class BlockDecoder(blockSize:Int) extends GraphStage[FlowShape[ByteString, ByteString]] {

  val in = Inlet[ByteString]("BlockDecoder.in")
  val out = Outlet[ByteString]("BlockDecoder.out")

  override val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    // val digest = MessageDigest.getInstance(algorithm)
    val buffer:Array[Byte] = Array.fill[Byte](blockSize)(0)

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {

        pull(in)
      }
    })

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        val chunk = grab(in)

//        digest.update(chunk.toArray)
        pull(in)
      }

      override def onUpstreamFinish(): Unit = {
//        emit(out, ByteString(digest.digest()))
        completeStage()
      }
    })

  }


}
