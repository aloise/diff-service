package name.aloise.assignment4c.actors

import akka.actor.{Actor, ActorRef, PoisonPill, Props, Stash}
import name.aloise.assignment4c.actors.persistence.PersistenceActorProxy
import name.aloise.assignment4c.models.{AsyncDataBlockStorage, _}

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.pattern._

/**
  * User: aloise
  * Date: 18.05.16
  * Time: 22:35
  *
  * master actor is the parent actor
  *
  */
class DiffServiceActor( id:String, blockSize:Int, persistenceActorProps: String => Props ) extends Actor  with Stash  {

  import DiffServiceActor._
  import DiffServiceActor.Stream._

  implicit val executionContext = context.system.dispatcher

  type ComputedResult = (DataComparisonResult.Value, List[DataDifferentPart])
  type ComputedResultOpt = Option[ComputedResult]

  // todo - move into conf
  val dataAwaitTimeout = 5.minutes


  val blockActors = AllowedStreamNames.map { streamName =>

    val persistentActorProxy = new PersistenceActorProxy( id, streamName, blockSize, persistenceActorProps, Some(context) )

    // retrieve left block data

    persistentActorProxy.initialData( )( dataAwaitTimeout ).map( (_, streamName) ) pipeTo self

    streamName -> persistentActorProxy
  }.toMap


  def receive = initBehavior( Map() )


  def initBehavior( blockStorage:Map[String,AsyncDataBlockStorage] ):Receive = {
    case ( block:AsyncDataBlockStorage, streamName:String ) =>

      val updatedMap = blockStorage + ( streamName -> block )

      if( updatedMap.keys == AllowedStreamNames ) {
        // all parts were received
        unstashAll()

        context.become( defaultBehavior( updatedMap, None ) , discardOld = false)

        // notify parent
        context.parent ! InitDone( id )

      } else {
        context.become( initBehavior( updatedMap ), discardOld = false )
      }

    case m:RequestMessage =>
      stash()

  }


  def defaultBehavior( blockStorage:Map[String,AsyncDataBlockStorage], comparisonResult: ComputedResultOpt ):Receive = {

    case PushData( ident, stream, data ) if ident == id && blockActors.contains( stream ) =>

      val originalSender = sender()

      blockActors( stream ).setData( data )( dataAwaitTimeout ).map { data =>
        AsyncUpdateDataResponse( originalSender, stream, data )
      } pipeTo self

      // context.become( defaultBehavior( , right, None ), discardOld = true)


    case CompareRequest( ident ) if ident == id =>

      val originalSender = sender()
      val currentBlockStorage = blockStorage

      comparisonResult.map{ case ( resultVal, resultDiff ) =>
        Future.successful( AsyncDataCompareResult( originalSender, CompareResponse( id, resultVal, resultDiff ), currentBlockStorage )  )
      } getOrElse {

        {
          // size is either equal and we need to compute the difference
          if (blockStorage(Stream.Left).size == blockStorage(Stream.Right).size) {
            val diffFuture = blockStorage(Stream.Left) getDifferenceWith blockStorage(Stream.Right)

            diffFuture.map { diff =>
              val result = if (diff.isEmpty) DataComparisonResult.Equal else DataComparisonResult.NotEqual
              (result, diff)
            }

          } else {
            // different size - return the appropriate code
            Future.successful((DataComparisonResult.DifferentSize, List()))
          }
        } map {  case ( resultVal, resultDiff ) =>
          AsyncDataCompareResult( originalSender, CompareResponse( id, resultVal, resultDiff ), currentBlockStorage )
        }

      } pipeTo self

    case Remove( ident ) if ident == id =>
      val originalSender = sender()
      val futureSeq =
        blockActors.map { case (_, actorProxy) =>
            actorProxy.
              deleteData()( dataAwaitTimeout ) .
              recover {
                case ex:Throwable => false
              }
        }

      Future.sequence( futureSeq ).map { removeSuccess =>
        // failed - one for all
        removeSuccess.foldLeft( true ){ case (all, result ) => all && result }
      } recover {
        case ex:Throwable =>
          false
      } map( _ => AsyncDataRemoveCompleted( originalSender ) ) pipeTo self




    // Internal messages
    case AsyncUpdateDataResponse( originalSender, streamName, dataBlock ) =>
      originalSender ! PushDataResponse( id, streamName, success = true )

      context.become( defaultBehavior( blockStorage + ( streamName -> dataBlock ), None ), discardOld = true )

    case AsyncDataCompareResult( originalSender, result, blocksForThisResult ) =>

      originalSender ! result

      if( ( blocksForThisResult == blockStorage ) && comparisonResult.isEmpty ){
        context.become( defaultBehavior( blockStorage, Some( ( result.comparisonResult, result.difference ) ) ), discardOld = true )
      }

    case AsyncDataRemoveCompleted( originalSender ) =>
      originalSender ! RemoveResponse( id )
      self ! PoisonPill

  }


}

object DiffServiceActor {

  object Stream {

    val Left = "left"
    val Right = "right"

    val AllowedStreamNames =  Set( Left, Right )

  }

  sealed trait Message
  trait RequestMessage extends Message
  trait ResponseMessage extends Message
  sealed trait InternalMessage

  case class Remove(ident:String) extends RequestMessage
  case class RemoveResponse(ident:String) extends ResponseMessage

  case class PushData( ident:String, stream:String, data:Array[Byte]) extends RequestMessage
  case class PushDataResponse( ident:String, stream:String, success:Boolean ) extends ResponseMessage

  case class CompareRequest( ident:String ) extends RequestMessage
  case class CompareResponse( ident:String, comparisonResult: DataComparisonResult.Value, difference:List[DataDifferentPart] = List() ) extends ResponseMessage

  case class InitDone(ident:String) extends ResponseMessage

  // Internal messages
  case class AsyncUpdateDataResponse( originalSender:ActorRef, stream:String, block: AsyncDataBlockStorage )
  case class AsyncDataCompareResult( originalSender:ActorRef, result: CompareResponse, blocks:Map[String,AsyncDataBlockStorage] )
  case class AsyncDataRemoveCompleted( originalSender:ActorRef )

}