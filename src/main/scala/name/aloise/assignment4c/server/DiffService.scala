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

import spray.json._
import DefaultJsonProtocol._

import scala.concurrent.Future

/**
  * User: aloise
  * Date: 18.05.16
  * Time: 19:57
  */
class DiffService( bindAddress:String, bindPort:Int, dataBlockSize:Int, serviceVersion:Int = 1 ) {

  implicit val system = ActorSystem("diff-service-system")

  implicit val materializer = ActorMaterializer()

  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher
  implicit var serverBindingFuture:Option[Future[ServerBinding]] = None

  val processingSystem = ActorSystem("diff-processing-service-system")
  val diffServiceMasterActor = processingSystem.actorOf( Props( classOf[DiffServiceMasterActor], dataBlockSize ) )

  val serviceVersionPrefix = "v"+serviceVersion



  protected val requestHandler: HttpRequest => HttpResponse = {

    case HttpRequest( GET, Uri.Path("/"), headers, _, _) if headers.exists( h => h.is("accept") && h.value().contains("text/html") ) =>
      HttpResponse( entity = greeting )

    case HttpRequest( GET, Uri.Path("/"), headers, _, _) if headers.exists( h => h.is("accept") && h.value().contains("application/json") ) =>
      HttpResponse( entity = greetingJson )

    // Main Route - parse ID and left/right param
    case HttpRequest( GET, Uri( _, _, Slash( Segment( `serviceVersionPrefix`, Slash( Segment( "diff", dataParts ) ) ) ), _, _ ) , _, _, _ ) =>

      // Parse the rest of the URL
      dataParts match {
        case Slash( Segment( ident, Slash( Segment ( leftOrRight, Uri.Path.Empty ) ) ) ) if leftOrRight == "left" || leftOrRight == "right" =>
          processDiffRequest( ident, leftOrRight )

        case Slash( Segment( ident, Slash( Segment ( _, Uri.Path.Empty ) ) ) ) =>
          HttpResponse( 400,  entity = jsonError("wrong_input_params_left_or_right") )

        case Slash( Segment( ident, Uri.Path.Empty  ) ) =>
          // query results
          HttpResponse( 400,  entity = jsonError("not_implemented") )

        case _ =>
          HttpResponse( 400,  entity = jsonError("wrong_input_params") )

      }




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

  def processDiffRequest( ident:String, leftOrRight:String ) =
    HttpResponse( entity = "test - left - " + ident )

  def greeting =
    HttpEntity(
      ContentTypes.`text/html(UTF-8)`,
      s"<!doctype html><html lang=en><head><meta charset=utf-8><title>Diff Service</title></head><body><p>Diff Service v$serviceVersion</p></body></html>"
    )

  def greetingJson =
    HttpEntity(
      ContentTypes.`application/json`,
      Map( "version" -> serviceVersionPrefix, "service" -> "DiffService" ).toJson.toString

    )

  def jsonError( errorStr:String ) =
    HttpEntity(
      ContentTypes.`application/json`,
      Map( "error" -> errorStr ).toJson.toString
    )

}
