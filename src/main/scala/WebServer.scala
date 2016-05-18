package name.aloise.assignment4c

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.io.StdIn
import akka.actor._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._



object WebServer {

    def main(args: Array[String]) {

      val conf = ConfigFactory.load()

      println("hello world")

      implicit val system = ActorSystem("my-system")
      implicit val materializer = ActorMaterializer()
      // needed for the future flatMap/onComplete in the end
      implicit val executionContext = system.dispatcher

      val route =
        path("hello") {
          get {
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
          }
        }

      val bindAddress = conf.as[Option[String]]("app.http.address").getOrElse( "localhost" )
      val bindPort = conf.as[Option[Int]]("app.http.port").getOrElse(8080)
      val bindingFuture = Http().bindAndHandle(route, bindAddress, bindPort)

      println( s"Server online at http://$bindAddress:$bindPort/\nPress RETURN to stop..." )

      StdIn.readLine() // let it run until user presses return
      bindingFuture
        .flatMap(_.unbind()) // trigger unbinding from the port
        .onComplete(_ => system.terminate()) // and shutdown when done

    }
}
