package name.aloise.assignment4c

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import name.aloise.assignment4c.server.DiffService
import net.ceedubs.ficus.Ficus._

import scala.io.StdIn

/**
  * User: aloise
  * Date: 18.05.16
  * Time: 19:54
  */
object WebServer {

    val conf = ConfigFactory.load()

    val bindAddress = conf.as[Option[String]]("app.http.address").getOrElse( "localhost" )
    val bindPort = conf.as[Option[Int]]("app.http.port").getOrElse(8080)
    val dataBlockSize = conf.as[Option[Int]]("app.data.blockSize").getOrElse(4096)
    val maxPayloadSize = conf.as[Option[Int]]("app.data.maxPayloadSize").getOrElse(16*1024*1024)
    val persistenceActorConf = conf.as[Option[String]]("app.persistence").getOrElse("Memory")

    def serverFactory() = new DiffService( bindAddress, bindPort, dataBlockSize, maxPayloadSize, persistenceActorConf )


    def main(args: Array[String]) {

      val server = serverFactory()

      server.start()

      println( s"Server online at http://$bindAddress:$bindPort/\nPress RETURN to stop..." )

      StdIn.readLine() // let it run until user presses return

      server.stop()

    }
}
