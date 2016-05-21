package name.aloise.assignment4c

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import name.aloise.assignment4c.server.DiffService
import net.ceedubs.ficus.Ficus._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.io.StdIn

/**
  * User: aloise
  * Date: 18.05.16
  * Time: 19:54
  */
object WebServer {

    object Config {
      val conf = ConfigFactory.load()

      val bindAddress = conf.as[Option[String]]("app.http.address").getOrElse( "localhost" )
      val bindPort = conf.as[Option[Int]]("app.http.port").getOrElse(8080)
      val dataBlockSize = conf.as[Option[Int]]("app.data.blockSize").getOrElse(4096)
      val maxPayloadSize = conf.as[Option[Int]]("app.data.maxPayloadSize").getOrElse(16*1024*1024)
      val storageConf = conf.getConfig("app.storage")

    }

    def serverFactory() = {
      import Config._

      new DiffService(bindAddress, bindPort, dataBlockSize, maxPayloadSize, storageConf)
    }


    def main(args: Array[String]) {

      val server = serverFactory()

      val serverBindingFuture = server.start()

      val ( storageEngine, _ ) = server.storageEngineWithClass

      import Config._

      println( s"Diff Server is online at http://$bindAddress:$bindPort/" )
      println( s"Using $storageEngine storage engine")

      Await.result( serverBindingFuture, Duration.Inf )

//      server.stop()

    }
}
