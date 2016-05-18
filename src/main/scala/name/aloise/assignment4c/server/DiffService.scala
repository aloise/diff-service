package name.aloise.assignment4c.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import scala.concurrent.Future

/**
  * User: aloise
  * Date: 18.05.16
  * Time: 19:57
  */
class DiffService( bindAddress:String, bindPort:Int ) {

  implicit val system = ActorSystem("diff-service-system")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher
  implicit var serverBindingFuture:Option[Future[ServerBinding]] = None


  protected val route =
    path("hello") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
      }
    } ~
    path("") { // index path
      get {
        complete( greeting )
      }
    }

  def start():Future[ServerBinding] = {
    val bindingFuture = Http().bindAndHandle(route, bindAddress, bindPort)
    serverBindingFuture = Some( bindingFuture )
    bindingFuture
  }

  def stop() = {

    serverBindingFuture.foreach{ bindingFuture =>
      bindingFuture
        .flatMap(_.unbind()) // trigger unbinding from the port
        .onComplete(_ => system.terminate()) // and shutdown when done
    }

  }

  def greeting =
    HttpEntity(
      ContentTypes.`text/html(UTF-8)`,
      """<!doctype html>
        |<html lang=en>
        |<head><meta charset=utf-8><title>Diff Service</title></head>
        |<body><p>Diff Sevice</p></body>
        |</html>""".stripMargin
    )

}
