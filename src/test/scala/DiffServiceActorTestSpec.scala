
import name.aloise.assignment4c.actors.DiffServiceMasterActor
import org.scalatest.WordSpec
import akka.actor.ActorSystem
import akka.actor.Actor
import akka.actor.Props
import akka.testkit.{ImplicitSender, TestActors, TestKit}
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
import name.aloise.assignment4c.actors._
import name.aloise.assignment4c.models.{DataComparisonResult, DataDifferentPart}

/**
  * User: aloise
  * Date: 19.05.16
  * Time: 15:34
  */

  class DiffServiceActorTestSpec extends TestKit(ActorSystem("DiffActorSystemTestSpec")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

    override def afterAll {
      TestKit.shutdownActorSystem(system)
    }

    val dataBlockSize = 2048

    "Processing Actor " should {
      val ident = "test-ident"
      val actor = system.actorOf( Props( classOf[DiffServiceActor], ident, dataBlockSize ) )

      "return a compare response from start with equal response code" in {
        actor ! DiffServiceActor.CompareRequest( ident )
        expectMsg( DiffServiceActor.CompareResponse( ident, DataComparisonResult.Equal ) )
      }

      "accept the left data block silently" in {
        actor ! DiffServiceActor.PushLeft( ident, "1234567890".getBytes )
        expectNoMsg()
      }

      "return a compare response from start with different size response code" in {
        actor ! DiffServiceActor.CompareRequest( ident )
        expectMsg( DiffServiceActor.CompareResponse( ident, DataComparisonResult.DifferentSize ) )
      }

      "accept the right data block silently with equal data" in {
        actor ! DiffServiceActor.PushRight( ident, "1234567890".getBytes )
        expectNoMsg()
      }

      "return a compare response from with equal size response code again" in {
        actor ! DiffServiceActor.CompareRequest( ident )
        expectMsg( DiffServiceActor.CompareResponse( ident, DataComparisonResult.Equal ) )
      }

      "accept the right data block silently with new not equal data" in {
        actor ! DiffServiceActor.PushRight( ident, "123456789X".getBytes )
        expectNoMsg()
      }

      "return a compare response from with correct difference" in {
        actor ! DiffServiceActor.CompareRequest( ident )
        expectMsg( DiffServiceActor.CompareResponse( ident, DataComparisonResult.NotEqual, List( DataDifferentPart( 9, 1 )) ) )
      }


    }

    "Master Actor" should {

      val masterActor = system.actorOf(Props( classOf[DiffServiceMasterActor], dataBlockSize ))
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

      "accept the left data block silently" in {
        masterActor ! DiffServiceActor.PushLeft( ident, "abcdefghij".getBytes )
        expectNoMsg()
      }

      "return a compare response from start with different size response code" in {
        masterActor ! DiffServiceActor.CompareRequest( ident )
        expectMsg( DiffServiceActor.CompareResponse( ident, DataComparisonResult.DifferentSize ) )
      }

      "accept the right data block silently with equal data" in {
        masterActor ! DiffServiceActor.PushRight( ident, "abcdefghij".getBytes )
        expectNoMsg()
      }

      "return a compare response from with equal size response code again" in {
        masterActor ! DiffServiceActor.CompareRequest( ident )
        expectMsg( DiffServiceActor.CompareResponse( ident, DataComparisonResult.Equal ) )
      }

      "accept the right data block silently with new not equal data" in {
        masterActor ! DiffServiceActor.PushRight( ident, "abcdefghiX".getBytes )
        expectNoMsg()
      }

      "return a compare response from with correct difference" in {
        masterActor ! DiffServiceActor.CompareRequest( ident )
        expectMsg( DiffServiceActor.CompareResponse( ident, DataComparisonResult.NotEqual, List( DataDifferentPart( 9, 1 )) ) )
      }

      "remove the nested actor silently" in {
        masterActor ! DiffServiceActor.Remove( ident )
      }

      "return a NotFound response since it was removed" in {

        masterActor ! DiffServiceActor.CompareRequest( ident )
        expectMsg( DiffServiceActor.CompareResponse( ident, DataComparisonResult.IdentNotFound, List() ) )
      }

    }
  }


