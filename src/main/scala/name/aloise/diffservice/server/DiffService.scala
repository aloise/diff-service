package name.aloise.diffservice.server

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.Uri.Path._
import akka.http.scaladsl.model.Uri.Path
import akka.stream.ActorMaterializer
import name.aloise.diffservice.actors.{DiffServiceActor, DiffServiceMasterActor}
import akka.pattern.{AskTimeoutException, ask}
import spray.json._
import spray.json.DefaultJsonProtocol._
import akka.stream.scaladsl._
import akka.util.{ByteString, Timeout}
import name.aloise.diffservice.actors.DiffServiceActor._
import name.aloise.diffservice.models.DataDifferentPart
import java.util.Base64
import java.nio.charset.StandardCharsets

import akka.{Done, NotUsed}
import com.typesafe.config.Config
import name.aloise.diffservice.actors.persistence.FlowStorageProxy.PushBlockResponses
import name.aloise.diffservice.actors.persistence.{BlockStorageActor, FlowStorageProxy, MemoryBlockActor, MongoBlockActor}
import name.aloise.diffservice.server.flow.{BlockDecoder, Chunker}

import scala.concurrent.duration._
import scala.concurrent.Future
import net.ceedubs.ficus.Ficus._

/**
  *
  * @param bindAddress IP address to bind
  * @param bindPort server port
  * @param dataBlockSize data would be split into multiple blocks up to dataBlockSize bytes each
  * @param maxPayloadSize max payload size of the HTTP request
  * @param storageConf storage conf
  * @param serviceVersion Service version - /v1/ ..
*/

class DiffService(bindAddress:String, bindPort:Int, dataBlockSize:Int, maxPayloadSize:Int, storageConf: Config, serviceVersion:Int = 1) {

  import DiffService.Params._

  implicit val system = ActorSystem("diff-service-system")

  implicit val materializer = ActorMaterializer()

  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  implicit var serverBindingFuture:Option[Future[ServerBinding]] = None

  val responseTimeout = Timeout( storageConf.as[Option[FiniteDuration]]("responseTimeout").getOrElse(1.minute) )


  val storageEngineWithClass: (String, Class[_]) = {
    storageConf.as[Option[String]]("engine").map(_.toLowerCase) match {

      case Some("mongo") =>
        ("Mongo", classOf[MongoBlockActor])

      case _ =>
        ("Memory", classOf[MemoryBlockActor])

    }
  }

  val storageActorProps : String => Props = { ident =>

    val ( _, clazz ) = storageEngineWithClass

    Props( clazz, ident, dataBlockSize, storageConf )

  }

  // data processing system
  val processingSystem = ActorSystem("diff-processing-service-system")

  val diffServiceMasterActor = processingSystem.actorOf( Props( classOf[DiffServiceMasterActor], dataBlockSize, storageActorProps ) )

  val serviceVersionPrefix = "v"+serviceVersion

  // Main request handler - mostly a router
  protected val requestHandler: HttpRequest => Future[HttpResponse] = {

    case HttpRequest( GET, Uri.Path("/"), headers, _, _) if headers.exists( h => h.is("accept") && h.value().contains("text/html") ) =>
      Future.successful( greetingHtml )

    case HttpRequest( GET, Uri.Path("/"), headers, _, _) if headers.exists( h => h.is("accept") && h.value().contains("application/json") ) =>
      Future.successful( greetingJson )

    // Main Route - parse ID and left/right param
    case h@HttpRequest( httpMethod, Uri( _, _, Slash( Segment( `serviceVersionPrefix`, Slash( Segment( "diff", dataParts ) ) ) ), _, _ ) , _, entity, _ ) =>
      // process all /v1/diff requests
      diffRequestHandler(httpMethod, dataParts, entity)

    case _: HttpRequest =>
      Future.successful( HttpResponse(404, entity = "Unknown resource!") )
  }




  /**
    * Main method to handle /v1/diff requests
    *
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
          pushDataRequest(ident, leftOrRight, requestEntity, maxPayloadSize)(responseTimeout)


      // more efficient streaming version - binary data
      case Slash(Segment(ident, Slash(Segment(leftOrRight, Uri.Path.Empty))))
        if (leftOrRight == "left.bin" || leftOrRight == "right.bin") && (httpMethod == POST) =>
        pushStreamDataRequest(ident, leftOrRight, requestEntity, maxPayloadSize)(responseTimeout)


      // remove the ident
      case Slash(Segment(ident, Slash(Segment("remove", Uri.Path.Empty)))) if httpMethod == DELETE =>
        diffServiceMasterActor.ask(DiffServiceActor.Remove(ident))(responseTimeout).mapTo[RemoveResponse].map { _ =>
          jsonSuccess( JsObject("success" -> JsTrue))
        } recover {

          case ex:Throwable =>
            jsonSuccess( JsObject("success" -> JsFalse))
        }

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
        .onComplete(_ => {
            system.terminate()
            processingSystem.terminate()
          }
        ) // and shutdown when done
    }

  }

  def getDiffResponse( ident:String )(implicit timeout:Timeout) = {

    val response = ( diffServiceMasterActor ask DiffServiceActor.CompareRequest( ident ) ).mapTo[CompareResponse]

    response map { case DiffServiceActor.CompareResponse( _, comparisonResult, difference ) =>
        jsonSuccess( JsObject( "result" -> JsString( comparisonResult.toString ) , "difference" -> difference.toJson ) )

    } recover {
      case ex:AskTimeoutException =>
        jsonError("service_timeout")
      case _ =>
        jsonError("internal_error", 500)
    }

  }

  def pushStreamDataRequest( ident:String, leftOrRightWithExtension:String, requestEntity: RequestEntity, sizeLimit:Int = 16*1024*1024 )(implicit responseAwaitTimeout:Timeout) = {

    val Array( leftOrRight, _ ) = leftOrRightWithExtension.split("\\.", 2 )

    if ( DiffServiceActor.Stream.AllowedStreamNames.contains( leftOrRight ) ) {

      val entitySource =
        requestEntity.
          withoutSizeLimit().
          dataBytes.
//          via( new Chunker( dataBlockSize ) ).
//          via( new BlockDecoder(dataBlockSize)).
          via( Flow.fromFunction( byteString => PushDataBlock( ident, leftOrRight, byteString.toArray ) ) )
          // via( Flow[PushDataBlock].map { block => ( diffServiceMasterActor ask block ).mapTo[PushDataBlockResponse] } )




      // clean existing data first
      ( diffServiceMasterActor ask PushData( ident, leftOrRight, Array[Byte]() ) ).mapTo[PushDataResponse].flatMap { response =>

        val processingProxy = processingSystem.actorOf( Props( classOf[FlowStorageProxy], ident, leftOrRight, diffServiceMasterActor  ) )

        val completionFuture = processingProxy.ask( FlowStorageProxy.IsComplete() )( Timeout( 1.hour ), ActorRef.noSender )

        if (response.success) {
          entitySource.
            to( Sink.actorRefWithAck(processingProxy, FlowStorageProxy.Init(), FlowStorageProxy.AckMessage(), FlowStorageProxy.Complete() )).
            run()
            completionFuture.mapTo[FlowStorageProxy.PushBlockResponses].map { pushBlockResponses =>

              val isSuccess:Boolean = pushBlockResponses.responses.forall( b => b )

              jsonSuccess(JsObject("success" -> JsBoolean( isSuccess ), "ident" -> JsString(ident), "stream" -> JsTrue))
            }

        } else {
          Future.successful(jsonError("failed_to_clean_the_stream"))
        }

      }

    } else {
      Future.successful(jsonError("invalid_data_block_param"))
    }

  }


  /**
    * Update indent with data blocks. Allows JSON payload up to sizeLimit Bytes
    *
    * @param ident ID of the data block
    * @param leftOrRight Either left or right data block
    * @param requestEntity HTTP request entity
    * @param sizeLimit Max allowed JSON payload
    * @return
    */
  def pushDataRequest( ident:String, leftOrRight:String, requestEntity: RequestEntity, sizeLimit:Int = 16*1024*1024 )(implicit responseAwaitTimeout:Timeout) = {

    if ( DiffServiceActor.Stream.AllowedStreamNames.contains( leftOrRight ) ) {

      requestEntity.
        withoutSizeLimit().
        dataBytes.
        fold(ByteString()) { case (total, chunk) => total ++ chunk }.
        takeWhile(_.size < sizeLimit).
        runWith(Sink.seq).flatMap { byteString =>

          val resultingString = byteString.map(_.compact.utf8String).mkString

          val updateDataBlock = resultingString.parseJson.convertTo[UpdateDataBlock]

          val data = Base64.getDecoder.decode(updateDataBlock.data)

          ( diffServiceMasterActor ask DiffServiceActor.PushData(ident, leftOrRight, data) ).mapTo[PushDataResponse].map { response =>
            jsonSuccess(JsObject("success" -> JsBoolean( response.success ), "ident" -> JsString(ident)))
          } recover {
            case ex:Throwable =>
              jsonSuccess(JsObject("success" -> JsFalse, "ident" -> JsString(ident)))
          }


      } recover {
        // TODO - add different response codes
        case _: ParsingException =>
          jsonError("json_parse_error")
        case _: DeserializationException =>
          jsonError("invalid_json_payload_format")
        case _: IllegalArgumentException =>
          jsonError("invalid_base64_data")
        case _: Throwable =>
          jsonError("json_format_error")

      }

    } else {
      // TODO - reject the http entity
      // requestEntity.dataBytes.
      Future.successful(jsonError("invalid_data_block_param"))

    }
  }

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

object DiffService {



  object Params {

    import spray.json.DefaultJsonProtocol._

    case class UpdateDataBlock( data: String )

    implicit val updateDataBlockJsonFormat: RootJsonFormat[UpdateDataBlock] = jsonFormat1( UpdateDataBlock.apply )
    implicit val dataDifferentPartJsonFormat: RootJsonFormat[DataDifferentPart] = jsonFormat2( DataDifferentPart.apply )

  }
}
