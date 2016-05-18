package name.aloise.assignment4c.server

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.Uri.Path._
import akka.http.scaladsl.model.Uri.Path
import akka.stream.ActorMaterializer
import name.aloise.assignment4c.actors.DiffServiceMasterActor

import scala.concurrent.Future

/**
  * User: aloise
  * Date: 18.05.16
  * Time: 19:57
  */
class DiffService( bindAddress:String, bindPort:Int, serviceVersion:Int = 1 ) {

  implicit val system = ActorSystem("diff-service-system")

  implicit val materializer = ActorMaterializer()

  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher
  implicit var serverBindingFuture:Option[Future[ServerBinding]] = None

  val processingSystem = ActorSystem("diff-processing-service-system")
  val diffServiceMasterActor = processingSystem.actorOf( Props( classOf[DiffServiceMasterActor] ) )

  val xxxx: Path = Path./( "v2" )

  protected val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest( GET, Uri.Path("/"), _, _, _) =>
      HttpResponse( entity = greeting )

    case HttpRequest( GET, Uri( _, _, `xxxx`, _, _ ), _, _, _ ) =>
      HttpResponse( entity = "test - v2 - " + 1 )

    case HttpRequest( GET, Uri( _, _, Slash( Segment( "v1", Slash( Segment( "diff", Slash( Segment( str, Slash( Segment ( "left", Uri.Path.Empty ) ) ) ) ) ) ) ), _, _ ) , _, _, _ ) =>
      HttpResponse( entity = "test - left - " + str )

    case HttpRequest( GET, Uri( _, _, Slash( Segment( "v1", Slash( Segment( "diff", Slash( Segment( str, Slash( Segment ( "right", Uri.Path.Empty ) ) ) ) ) ) ) ), _, _ ) , _, _, _ ) =>
      HttpResponse( entity = "test - right - " + str )

    case _: HttpRequest =>
      HttpResponse(404, entity = "Unknown resource!")
  }

  def start():Future[ServerBinding] = {
    val bindingFuture = Http().bindAndHandleSync(requestHandler, bindAddress, bindPort) // Http().bindAndHandle(routes, bindAddress, bindPort)
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
      s"<!doctype html><html lang=en><head><meta charset=utf-8><title>Diff Service</title></head><body><p>Diff Service v$serviceVersion</p></body></html>"
    )

}
