package name.aloise.assignment4c.server

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.Uri.Path._
import akka.http.scaladsl.model.Uri.Path
import akka.stream.ActorMaterializer
import name.aloise.assignment4c.actors.{DiffServiceActor, DiffServiceMasterActor}
import akka.pattern.{AskTimeoutException, ask}
import spray.json._
import DefaultJsonProtocol._
import akka.util.Timeout
import name.aloise.assignment4c.actors.DiffServiceActor.CompareResponse
import name.aloise.assignment4c.models.DataDifferentPart

import scala.concurrent.duration._
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

  val responseTimeout = Timeout( 60.seconds )

  implicit val colorFormat = jsonFormat2(DataDifferentPart)



  // Main request handler - mostly a router
  protected val requestHandler: HttpRequest => Future[HttpResponse] = {

    case HttpRequest( GET, Uri.Path("/"), headers, _, _) if headers.exists( h => h.is("accept") && h.value().contains("text/html") ) =>
      Future.successful( greetingHtml )

    case HttpRequest( GET, Uri.Path("/"), headers, _, _) if headers.exists( h => h.is("accept") && h.value().contains("application/json") ) =>
      Future.successful( greetingJson )

    // Main Route - parse ID and left/right param
    case HttpRequest( httpMethod, Uri( _, _, Slash( Segment( `serviceVersionPrefix`, Slash( Segment( "diff", dataParts ) ) ) ), _, _ ) , _, entity, _ ) =>
      // process all /v1/diff requests
      diffRequestHandler(httpMethod, dataParts, entity)

    case _: HttpRequest =>
      Future.successful( HttpResponse(404, entity = "Unknown resource!") )
  }


  /**
    * Main method to hande /v1/diff requests
    * @param httpMethod - either GET or POST - update the data block or get the difference
    * @param dataParts - the rest of the request URL that consists of ident and options left/right params
    * @param requestEntity - request body - json encoded base64 data in format { data : "BASE64String" }
    * @return Async HTTP response
    */
  def diffRequestHandler(httpMethod: HttpMethod, dataParts: SlashOrEmpty, requestEntity: RequestEntity): Future[HttpResponse] = {
    // Parse the rest of the URL
    dataParts match {

      // update left or right data block
      case Slash(Segment(ident, Slash(Segment(leftOrRight, Uri.Path.Empty))))
        if (leftOrRight == "left" || leftOrRight == "right") && (httpMethod == POST) =>
          pushDataRequest(ident, leftOrRight)

      // query results
      case Slash(Segment(ident, Uri.Path.Empty)) if httpMethod == GET =>

        getDiffResponse(ident)(responseTimeout)


      case Slash(Segment(ident, Slash(Segment(_, Uri.Path.Empty)))) if httpMethod == POST =>
        Future.successful( jsonError("wrong_input_params_left_or_right") )

      case _ =>
        Future.successful( jsonError("wrong_input_params") )

    }
  }

  def start():Future[ServerBinding] = {
    val bindingFuture = Http().bindAndHandleAsync(requestHandler, bindAddress, bindPort) // Http().bindAndHandle(routes, bindAddress, bindPort)
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

  def getDiffResponse( ident:String )(implicit timeout:Timeout) = {

    val response = ( diffServiceMasterActor ask DiffServiceActor.CompareRequest( ident ) ).mapTo[CompareResponse]

    response map { case DiffServiceActor.CompareResponse( _, comparisonResult, difference ) =>
        jsonSuccess( JsObject(  "result" -> JsString( comparisonResult.toString ) , "difference" -> difference.toJson ) )

    } recover {
      case ex:AskTimeoutException =>
        jsonError("service_timeout")
      case _ =>
        jsonError("internal_error", 500)
    }



  }

  def pushDataRequest( ident:String, leftOrRight:String ) =
    Future.successful( jsonSuccess( JsObject() ) )

  def greetingHtml =
    HttpResponse( 200, entity =
      HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        s"<!doctype html><html lang=en><head><meta charset=utf-8><title>Diff Service</title></head><body><p>Diff Service v$serviceVersion</p></body></html>"
      )
    )

  def greetingJson =
    jsonSuccess( JsObject( "version" -> JsString( serviceVersionPrefix ), "service" -> JsString( "DiffService" ) ) )

  def jsonSuccess( jsValue: JsValue, httpCode:Int = 200 ) =
    HttpResponse(httpCode, entity =
      HttpEntity(
        ContentTypes.`application/json`,
        jsValue.toString
      )
    )

  def jsonError( errorStr:String, httpCode:Int = 400 ) =
    HttpResponse(httpCode, entity =
      HttpEntity(
        ContentTypes.`application/json`,
        Map( "error" -> errorStr ).toJson.toString
      )
    )

}
