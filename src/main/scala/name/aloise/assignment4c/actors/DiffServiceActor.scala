package name.aloise.assignment4c.actors

import akka.actor.{Actor, ActorRef, PoisonPill, Props, Stash}
import name.aloise.assignment4c.actors.persistence.PersistenceActorProxy
import name.aloise.assignment4c.models.{AsyncDataBlockStorage, _}

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.pattern._
import name.aloise.assignment4c.actors.persistence.BlockStorageActor.{SetBlock, SetBlockResponse}
import name.aloise.assignment4c.models.AsyncDataBlockStorage.Fingerprint

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
  import PersistenceActorProxy._

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

      blockActors( stream ).setData( data )( dataAwaitTimeout ).map { updatedData =>
        AsyncUpdateDataResponse( originalSender, stream, updatedData )
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
              deleteData()( dataAwaitTimeout ).
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


    case PushDataBlock( ident, stream, newDataBlock ) if ident == id =>

      if( newDataBlock.length > 0 ){

        blockStorage.get( stream ).foreach { blockStorageItem =>

          val existingBlocksCount = if (blockStorageItem.size > 0) ((blockStorageItem.size - 1) / blockSize) + 1 else 0
          val newDataBlockSize = newDataBlock.length

          val newSizeOnSuccess = newDataBlockSize + blockStorageItem.size

          // a list of blocks to push - Start of data in new array
          val blocksToPush: Future[Seq[(Int, ( Array[Byte], Fingerprint ) )]] =
            if (blockStorageItem.size % blockSize == 0) {
              // empty or no need to pull the last block

              val newBlocksCount = ((newDataBlockSize - 1) / blockSize) + 1
              val newBlocksToPush =
                for {
                  i <- 0 until newBlocksCount
                  leftIndex = i * blockSize
                  rightIndex = Math.min((i + 1) * blockSize, newDataBlockSize)
                  newBlock = newDataBlock.slice(leftIndex, rightIndex)
                } yield (existingBlocksCount + i, ( newBlock, AsyncDataBlockStorage.getBlockFingerprint( newBlock ) ) )

              Future.successful(newBlocksToPush)

            } else {

              blockStorageItem.blocks(existingBlocksCount - 1).map { lastExistingBlock =>
                val lastExistingBlockSize = lastExistingBlock.length

                val initialBlockFromExistingWithNewStartIndex: Option[(Int, Array[Byte])] =
                  if (lastExistingBlockSize >= blockSize) {
                    None
                  } else {
                    val newBlock = Array.fill[Byte](Math.min(blockSize, newDataBlockSize))(0)
                    // copy from existing block
                    for (i <- 0 until lastExistingBlockSize) newBlock(i) = lastExistingBlock(i)
                    // fill the rest with new data
                    for (i <- lastExistingBlockSize until Math.min(blockSize, newDataBlockSize)) newBlock(i) = newDataBlock(i - lastExistingBlockSize)

                    Some(blockSize - lastExistingBlockSize, newBlock)
                  }

                val newStartIndex = initialBlockFromExistingWithNewStartIndex.fold(0)(_._1)
                val newBlocksCount = (((newDataBlockSize - newStartIndex) - 1) / blockSize) + 1

                val blocksToPush: Seq[(Int, (Array[Byte],Fingerprint))] =
                  if (newBlocksCount > 0) {
                    for {
                      i <- 0 until newBlocksCount
                      leftIndex = i * blockSize + newStartIndex
                      rightIndex = Math.min((i + 1) * blockSize + newStartIndex, newDataBlockSize)
                      newBlock = newDataBlock.slice(leftIndex, rightIndex)
                    } yield (existingBlocksCount + i, ( newBlock, AsyncDataBlockStorage.getBlockFingerprint( newBlock ) ) )
                  } else {
                    Seq()
                  }

                val firstBlockOptAsSeq = initialBlockFromExistingWithNewStartIndex.map(
                  b => (existingBlocksCount - 1, ( b._2, AsyncDataBlockStorage.getBlockFingerprint( b._2 )) )
                ).toSeq

                firstBlockOptAsSeq ++ blocksToPush

              }
            }

          blocksToPush.flatMap { blocks =>

            val successResultF: Future[Boolean] =
              blockActors.get(stream).map { storageActorProxy =>

                val updateRequests: Future[Boolean] =
                  Future.sequence(
                    blocks.map { case (blockNum, data) =>
                      storageActorProxy.setBlock(blockNum, data._1)(dataAwaitTimeout)
                    }
                  ).map {
                    listOfBooleans: Seq[Boolean] =>
                      listOfBooleans.fold(true) { case (result, current) => result && current }
                  }

                updateRequests

              } getOrElse Future.successful(false)

            successResultF.map { result =>

              if( result ) {

                // TODO - Complete the fingerprint update

                val totalUpdatedDataBlocks = ( newSizeOnSuccess - 1 ) / blockSize + 1

                val fingerPrintByIndex = blocks.map{ case ( blockNum, (_,fingerprint ) ) => blockNum -> fingerprint }.toMap

                val updatedFingerprints = Array.tabulate[Fingerprint]( totalUpdatedDataBlocks ){ i =>
                  fingerPrintByIndex.getOrElse(i,
                    if (i < blockStorageItem.fingerprints.length)
                      blockStorageItem.fingerprints(i)
                    else 0
                  )
                }

                val newDataBlock = new AsyncDataBlockStorage( newSizeOnSuccess, blockStorageItem.blocks, blockStorageItem.blockSize, updatedFingerprints )

                context.become( defaultBehavior( blockStorage + ( stream -> newDataBlock ), None ), discardOld = true )
              }

              PushDataBlockResponse(ident, stream, success = result)
            }

          } recover {
            case _: Throwable =>
              PushDataBlockResponse(ident, stream, success = false)
          } pipeTo sender()

        }

      } else {
        // zero data size - ok!
        sender ! PushDataBlockResponse(ident, stream, success = true)
      }


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

  // Update the whole data block. Previous data is discarded
  case class PushData( ident:String, stream:String, data:Array[Byte]) extends RequestMessage
  case class PushDataResponse( ident:String, stream:String, success:Boolean ) extends ResponseMessage

  // Streaming addition - add multiple additional blocks to the end
  case class PushDataBlock( ident:String, stream:String, dataBlock:Array[Byte] ) extends RequestMessage
  case class PushDataBlockResponse( ident:String, stream:String, success:Boolean ) extends ResponseMessage

  case class CompareRequest( ident:String ) extends RequestMessage
  case class CompareResponse( ident:String, comparisonResult: DataComparisonResult.Value, difference:List[DataDifferentPart] = List() ) extends ResponseMessage

  case class InitDone(ident:String) extends ResponseMessage

  // Internal messages
  case class AsyncUpdateDataResponse( originalSender:ActorRef, stream:String, block: AsyncDataBlockStorage )
  case class AsyncDataCompareResult( originalSender:ActorRef, result: CompareResponse, blocks:Map[String,AsyncDataBlockStorage] )
  case class AsyncDataRemoveCompleted( originalSender:ActorRef )

}