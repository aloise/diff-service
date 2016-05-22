package name.aloise.assignment4c.server.flow

import akka.stream._
import akka.stream.stage._
import akka.util.ByteString

/**
  * User: aloise
  * Date: 22.05.16
  * Time: 0:16
  */
class Chunker(val chunkSize: Int) extends GraphStage[FlowShape[ByteString, ByteString]] {
  val in = Inlet[ByteString]("Chunker.in")
  val out = Outlet[ByteString]("Chunker.out")
  override val shape = FlowShape.of(in, out)

  var c = 0

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var buffer = ByteString.empty

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {

        if (isClosed(in))
          emitChunk()
        else
          pull(in)
      }
    })
    setHandler(in, new InHandler {
      override def onPush(): Unit = {

        val elem = grab(in)
        buffer ++= elem
        emitChunk()
      }

      override def onUpstreamFinish(): Unit = {
        if (buffer.isEmpty) completeStage()
        // elements left in buffer, keep accepting downstream pulls
        // and push from buffer until buffer is emitted
      }
    })

    private def emitChunk(): Unit = {
      if (buffer.isEmpty /*|| ( buffer.length < chunkSize )*/ ) {
        if (isClosed(in))
          completeStage()
        else
          pull(in)
      } else {
          if( isClosed(in) ){

            push(out, buffer)

            completeStage()

          } else {
            val (chunk, nextBuffer) = buffer.splitAt(chunkSize)
            buffer = nextBuffer
            println("buffer size : " + buffer.length)
            push(out, chunk)
          }



      }
    }

  }
}
