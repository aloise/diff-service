package name.aloise.assignment4c.actors.persistence

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import akka.pattern.ask
import name.aloise.assignment4c.actors.persistence.BlockStorageActor.{GetBlockResponse, SetBlockResponse}
import name.aloise.assignment4c.models.AsyncDataBlockStorage
import name.aloise.assignment4c.models.AsyncDataBlockStorage.Fingerprint

import scala.collection.immutable.IndexedSeq
/**
  * User: aloise
  * Date: 19.05.16
  * Time: 23:05
  */



class PersistenceActorProxy(dataIdent:String, stream:String, blockSize:Int, persistenceActorProps: String => Props, spawnActorWithParent:Option[ActorRefFactory] = None )(implicit ec:ExecutionContext) {

  import PersistenceActorProxy._

  val persistenceIdent = getStorageIdent( dataIdent, stream )

  var persistenceActor : Option[ActorRef] = spawnActorWithParent.map { parentActor =>
    spawnActor( parentActor )
  }

  def spawnActor( parent:ActorRefFactory ) = {
    parent.actorOf( persistenceActorProps( persistenceIdent ) )
  }

  def getBlock( blockNum:Int )( implicit t:Timeout ):Future[Array[Byte]] = {
    persistenceActor.fold[Future[Array[Byte]]] {
      Future.failed(new PersistenceActorNotCreatedException(dataIdent, stream) )
    } { actor =>

      ( actor ask BlockStorageActor.GetBlock( persistenceIdent, blockNum  ) ).mapTo[BlockStorageActor.GetBlockResponse].map {
        case GetBlockResponse( _, _, Some(data), _ ) =>
          data
        case _ =>
          throw new BlockNotFoundException(dataIdent, stream)
      }

    }
  }

  def setBlock( blockNum:Int, data:Array[Byte] )( implicit t:Timeout ):Future[Boolean] = {
    persistenceActor.fold[Future[Boolean]] {
      Future.failed(new PersistenceActorNotCreatedException(dataIdent, stream) )
    } { actor =>
      ( actor ask BlockStorageActor.SetBlock( persistenceIdent, blockNum, data ) ).mapTo[SetBlockResponse].map {
        case SetBlockResponse(_,_, true ) =>
          true
        case _ =>
          throw new BlockUpdateFailedException(dataIdent, stream)
      }
    }
  }

  def setData( wholeDataArray:Array[Byte] )( implicit t:Timeout ):Future[AsyncDataBlockStorage] = {

    persistenceActor.fold[Future[AsyncDataBlockStorage]] {
      Future.failed(new PersistenceActorNotCreatedException(dataIdent, stream) )
    } { actor =>

      val dataSize = wholeDataArray.length
      val blockCount = if( dataSize > 0 ) (dataSize - 1) / blockSize else -1 // amount of blocks minus 1

      // delete previous blocks first

      deleteData()( t ).flatMap { deletedSuccessfully =>
          // save the block with the fingerprint
          val setCallbacks: IndexedSeq[Future[(Int, Fingerprint)]] =
            ( 0 to blockCount).map { i =>
              val leftIndex = i * blockSize
              val rightIndex = Math.min(wholeDataArray.length, (i + 1) * blockSize)
              val block = wholeDataArray.slice(leftIndex, rightIndex)
              val fingerprint = AsyncDataBlockStorage.getBlockFingerprint( block )

              setBlock( i, block ).map{ _ => (i, fingerprint ) }
            }

          Future.sequence(setCallbacks).map { results =>
            val fingerprintArray = Array.fill[Fingerprint]( blockCount + 1 )( 0 )
            for( ( blockIndex, fingerprint ) <- results ){
              fingerprintArray( blockIndex ) = fingerprint
            }

            val getBlockHandler: Int => Future[Array[Byte]] = getBlock( _ )( t )

            new AsyncDataBlockStorage( dataSize, getBlockHandler, blockSize, fingerprintArray )

          }

      }

    }

  }

  def deleteData( )( implicit t:Timeout ) = {
    persistenceActor.fold[Future[Boolean]] {
      Future.failed(new PersistenceActorNotCreatedException(dataIdent, stream))
    } { actor =>

      (actor ask BlockStorageActor.Delete(persistenceIdent)).mapTo[BlockStorageActor.DeleteResponse].map {
        case BlockStorageActor.DeleteResponse(_, true) =>
          true
        case _ =>
          throw new DeleteFailedException(dataIdent, stream)
      }
    }
  }

  def initialData( )( implicit t:Timeout ) :Future[AsyncDataBlockStorage] = {
    getMetadata()(t).map { case ( fingerprintArray, dataSize ) =>

      val getBlockHandler: Int => Future[Array[Byte]] = getBlock( _ )( t )

      new AsyncDataBlockStorage( dataSize, getBlockHandler, blockSize, fingerprintArray )

    }
  }


  def getMetadata()( implicit t:Timeout ):Future[(Array[Fingerprint], Int )] = {
    persistenceActor.fold[Future[(Array[Fingerprint], Int )]] {
      Future.failed(new PersistenceActorNotCreatedException(dataIdent, stream) )
    } { actor =>
      ( actor ask BlockStorageActor.GetMetadata( persistenceIdent ) ).mapTo[BlockStorageActor.GetMetadataResponse].map {
        case BlockStorageActor.GetMetadataResponse( _, fingerprints, dataSize, _, _ ) =>
          ( fingerprints, dataSize )
        case _ =>
          throw new FingerprintRetrievalFailed(dataIdent, stream)
      }
    }
  }

}

object PersistenceActorProxy {

  def getStorageIdent(dataIdent:String, stream:String ):String = dataIdent+"-"+stream

  abstract class BlockPersistenceException(ident:String, stream:String) extends Exception
  class PersistenceActorNotCreatedException(ident:String, stream:String)  extends BlockPersistenceException(ident, stream)
  class BlockNotFoundException(ident:String, stream:String)  extends BlockPersistenceException(ident, stream)
  class BlockUpdateFailedException(ident:String, stream:String)  extends BlockPersistenceException(ident, stream)
  class DeleteFailedException(ident:String, stream:String)  extends BlockPersistenceException(ident, stream)
  class FingerprintRetrievalFailed(ident:String, stream:String)  extends BlockPersistenceException(ident, stream)

}


