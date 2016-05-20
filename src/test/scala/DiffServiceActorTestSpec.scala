
import name.aloise.assignment4c.actors.DiffServiceMasterActor
import org.scalatest.WordSpec
import akka.actor.ActorSystem
import akka.actor.Actor
import akka.actor.Props
import akka.testkit.{ImplicitSender, TestActors, TestKit}
import name.aloise.assignment4c.actors.DiffServiceActor.{PushDataResponse, RemoveResponse}
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
import name.aloise.assignment4c.actors._
import name.aloise.assignment4c.actors.persistence.MemoryBlockActor
import name.aloise.assignment4c.models.{DataComparisonResult, DataDifferentPart}

/**
  * User: aloise
  * Date: 19.05.16
  * Time: 15:34
  */

  class DiffServiceActorTestSpec extends TestKit(ActorSystem("DiffActorSystemTestSpec")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  import name.aloise.assignment4c.WebServer



    val persistentActorProps : String => Props = { ident:String => Props( classOf[MemoryBlockActor], ident, dataBlockSize ) }

    val dataBlockSize = 2048

    "Processing Actor " should {
      val ident = "test-ident"
      val actor = system.actorOf( Props( classOf[DiffServiceActor], ident, dataBlockSize, persistentActorProps ) )

      "return a compare response from start with equal response code" in {
        actor ! DiffServiceActor.CompareRequest( ident )
        expectMsg( DiffServiceActor.CompareResponse( ident, DataComparisonResult.Equal ) )
      }

      "accept the left data block silently" in {
        actor ! DiffServiceActor.PushData( ident, DiffServiceActor.Stream.Left, "1234567890".getBytes )
        expectMsgType[PushDataResponse]
      }

      "return a compare response from start with different size response code" in {
        actor ! DiffServiceActor.CompareRequest( ident )
        expectMsg( DiffServiceActor.CompareResponse( ident, DataComparisonResult.DifferentSize ) )
      }

      "accept the right data block with equal data" in {
        actor ! DiffServiceActor.PushData( ident, DiffServiceActor.Stream.Right, "1234567890".getBytes )
        expectMsgType[PushDataResponse]
      }

      "return a compare response from with equal size response code again" in {
        actor ! DiffServiceActor.CompareRequest( ident )
        expectMsg( DiffServiceActor.CompareResponse( ident, DataComparisonResult.Equal ) )
      }

      "accept the right data block with new not equal data" in {
        actor ! DiffServiceActor.PushData( ident, DiffServiceActor.Stream.Right, "123456789X".getBytes )
        expectMsgType[PushDataResponse]
      }

      "return a compare response from with correct difference" in {
        actor ! DiffServiceActor.CompareRequest( ident )
        expectMsg( DiffServiceActor.CompareResponse( ident, DataComparisonResult.NotEqual, List( DataDifferentPart( 9, 1 )) ) )
      }


    }

    "Master Actor" should {

      val masterActor = system.actorOf(Props( classOf[DiffServiceMasterActor], dataBlockSize, persistentActorProps ))
      val ident = "test-ident-" + scala.util.Random.nextInt(10000)

      "not respond on invalid messages" in {

        masterActor ! ( "hello world", 15 )
        expectNoMsg()
      }

      "return a CompareResponse with NotFound status for a random ident" in {

        val identNotFound = "random-ident-not-found"
        masterActor ! DiffServiceActor.CompareRequest( identNotFound )
        expectMsg( DiffServiceActor.CompareResponse( identNotFound, DataComparisonResult.IdentNotFound, List() ) )
      }

      "accept the left data block" in {
        masterActor ! DiffServiceActor.PushData( ident, DiffServiceActor.Stream.Left, "abcdefghij".getBytes )
        expectMsgType[PushDataResponse]
      }

      "return a compare response from start with different size response code" in {
        masterActor ! DiffServiceActor.CompareRequest( ident )
        expectMsg( DiffServiceActor.CompareResponse( ident, DataComparisonResult.DifferentSize ) )
      }

      "accept the right data block with equal data" in {
        masterActor ! DiffServiceActor.PushData( ident, DiffServiceActor.Stream.Right, "abcdefghij".getBytes )
        expectMsgType[PushDataResponse]
      }

      "return a compare response from with equal size response code again" in {
        masterActor ! DiffServiceActor.CompareRequest( ident )
        expectMsg( DiffServiceActor.CompareResponse( ident, DataComparisonResult.Equal ) )
      }

      "accept the right data block with new not equal data" in {
        masterActor ! DiffServiceActor.PushData( ident, DiffServiceActor.Stream.Right, "abcdefghiX".getBytes )
        expectMsgType[PushDataResponse]
      }

      "return a compare response from with correct difference" in {
        masterActor ! DiffServiceActor.CompareRequest( ident )
        expectMsg( DiffServiceActor.CompareResponse( ident, DataComparisonResult.NotEqual, List( DataDifferentPart( 9, 1 )) ) )
      }

      "remove the nested actor silently" in {
        masterActor ! DiffServiceActor.Remove( ident )
        expectMsgType[RemoveResponse]
      }

      "return a NotFound response since it was removed" in {

        masterActor ! DiffServiceActor.CompareRequest( ident )
        expectMsg( DiffServiceActor.CompareResponse( ident, DataComparisonResult.IdentNotFound, List() ) )
      }

    }


    override def afterAll {
      TestKit.shutdownActorSystem(system)
    }

  }


