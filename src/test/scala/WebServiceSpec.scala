import akka.event.NoLogging
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Flow
import com.typesafe.config.ConfigFactory
import name.aloise.assignment4c.server.DiffService
import org.scalatest._
import spray.json._
import spray.json.DefaultJsonProtocol._

import scalaj.http._
import net.ceedubs.ficus.Ficus._

/**
  * User: aloise
  * Date: 19.05.16
  * Time: 13:39
  */
class WebServiceSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  import name.aloise.assignment4c.WebServer._

  case class GetIdentResponseDiffItem( start:Int, length:Int )
  case class GetIdentResponse( result:String, difference:Seq[GetIdentResponseDiffItem] )

  implicit val getIdentResponseDiffItemJsonFormat: RootJsonFormat[GetIdentResponseDiffItem] = jsonFormat2( GetIdentResponseDiffItem )
  implicit val getIdentResponseJsonFormat: RootJsonFormat[GetIdentResponse] = jsonFormat2( GetIdentResponse )


  def getServiceUrl:String = "http://" + bindAddress + ":" + bindPort + "/"

  val testServer = serverFactory()

  "Diff Web Service" when {

    ( "running on " + getServiceUrl ) should {

      "listen on" in {
        val result = Http(getServiceUrl).asString
        ( result.code / 100 ) shouldBe 2

      }

      "return a greeting message" in {
        val result = Http(getServiceUrl).asString
        result.body.length should be > 0

      }

      "respond with an error on invalid request" in {
        val result = Http(getServiceUrl + "blahblah").asString
        ( result.code / 100 ) should be !== 2

      }

      "respond on get requests with unknown ident with IdentNotFound" in {
        val result = Http(getServiceUrl +"v1/diff/blahblah" ).asString
        result.body.parseJson.asJsObject.fields( "result" ).convertTo[String] should be === "IdentNotFound"
      }

      "respond with an error for incorrect update params - not left or right" in {
        val result = Http(getServiceUrl +"v1/diff/blahblah/center" ).postData("nothing").asString
        ( result.code / 100 ) should be !== 2
      }

      "respond with an error for incorrect update request with non-POST http code" in {
        val result = Http(getServiceUrl +"v1/diff/blahblah/center" ).asString // get request
        ( result.code / 100 ) should be !== 2
      }

      "not accept new data block with incorrect payload ( not json )" in {
        val result = Http(getServiceUrl +"v1/diff/blahblah/left" ).postData("nothing").asString
        ( result.code / 100 ) should be !== 2
        result.body.parseJson.asJsObject.fields("error").convertTo[String] should be === "json_format_error"
      }

      "not accept new data block with incorrect payload ( invalid json structure )" in {
        val result = Http(getServiceUrl +"v1/diff/blahblah/left" ).postData("""{"test":"nothing"}""").asString
        ( result.code / 100 ) should be !== 2
        result.body.parseJson.asJsObject.fields("error").convertTo[String] should be === "invalid_json_payload_format"
      }

      "not accept new data block with incorrect payload ( json data field doesn't contain a string )" in {
        val result = Http(getServiceUrl +"v1/diff/blahblah/left" ).postData("""{"data":1}""").asString
        ( result.code / 100 ) should be !== 2
        result.body.parseJson.asJsObject.fields("error").convertTo[String] should be === "invalid_json_payload_format"
      }

      "not accept new data block with incorrect payload ( json data field doesn't contain a Base64 encoded string )" in {
        val result = Http(getServiceUrl +"v1/diff/blahblah/left" ).postData("""{"data":"not a base 64 encoded string!"}""").asString
        ( result.code / 100 ) should be !== 2
        result.body.parseJson.asJsObject.fields("error").convertTo[String] should be === "invalid_base64_data"
      }

      "not accept new data block with incorrect payload ( exceeding the max payload size )" in {
        val hugePayload = "xx"*maxPayloadSize
        an [java.io.IOException] should be thrownBy Http(getServiceUrl +"v1/diff/blahblah/left" ).postData(hugePayload).asString
      }

      "accept a valid data block - left with id `test`" in {
        // SGVsbG8gV29ybGQh -> "Hello world!"
        val result = Http(getServiceUrl +"v1/diff/test/left" ).postData("""{"data":"SGVsbG8gd29ybGQh"}""").asString
        ( result.code / 100 ) should be === 2
      }

      "get a response with new `test` ident - size is different since the right part is empty" in {
        val result = Http(getServiceUrl +"v1/diff/test" ).asString
        result.body.parseJson.asJsObject.fields( "result" ).convertTo[String] should be === "DifferentSize"
      }

      "accept a valid data block - right with id `test` with data equal to left" in {
        // SGVsbG8gV29ybGQh -> "Hello world!"
        val result = Http(getServiceUrl +"v1/diff/test/right" ).postData("""{"data":"SGVsbG8gd29ybGQh"}""").asString
        ( result.code / 100 ) should be === 2
      }

      "get a response for `test` ident - content should be equal" in {
        val result = Http(getServiceUrl +"v1/diff/test" ).asString
        ( result.code / 100 ) should be === 2
        result.body.parseJson.asJsObject.fields( "result" ).convertTo[String] should be === "Equal"
      }

      "accept a valid data block - right with id `test` with different data of the same size" in {
        // SGVsbG8gd29ybFgh -> "Hello worlX!"
        val result = Http(getServiceUrl +"v1/diff/test/right" ).postData("""{"data":"SGVsbG8gd29ybFgh"}""").asString
        ( result.code / 100 ) should be === 2
      }

      "get a response for `test` ident - content should be different" in {
        val result = Http(getServiceUrl +"v1/diff/test" ).asString

        val responseObj = result.body.parseJson.convertTo[GetIdentResponse]

        ( result.code / 100 ) should be === 2

        responseObj.result should be === "NotEqual"

        // "Hello world!" against "Hello worlX!"
        responseObj.difference should contain ( GetIdentResponseDiffItem( 10, 1) )

      }

      "accept a valid data block - left with id `test` with should be equal with right" in {
        // SGVsbG8gd29ybFgh -> "Hello worlX!"
        val result = Http(getServiceUrl +"v1/diff/test/left" ).postData("""{"data":"SGVsbG8gd29ybFgh"}""").asString
        ( result.code / 100 ) should be === 2
      }

      "get a response for `test` ident - content should be test same again" in {
        val result = Http(getServiceUrl +"v1/diff/test" ).asString

        val responseObj = result.body.parseJson.convertTo[GetIdentResponse]

        ( result.code / 100 ) should be === 2

        responseObj.result should be === "Equal"

      }

      "remove the `test` ident" in {
        val result = Http(getServiceUrl +"v1/diff/test/remove" ).method("DELETE").asString
        ( result.code / 100 ) should be === 2
      }

      "respond on get requests with `test` ident with IdentNotFound" in {
        val result = Http(getServiceUrl +"v1/diff/test" ).asString
        result.body.parseJson.asJsObject.fields( "result" ).convertTo[String] should be === "IdentNotFound"
      }

    }

  }


  override def beforeAll() = {
    testServer.start()
  }

  override def afterAll() = {
    testServer.stop()
  }






}
