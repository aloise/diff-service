package name.aloise.diffservice.actors.persistence
import akka.actor.Actor.Receive
import com.typesafe.config.Config
import name.aloise.diffservice.actors.persistence.BlockStorageActor._
import reactivemongo.api.{MongoConnection, MongoDriver}
import net.ceedubs.ficus.Ficus._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocument, _}
import akka.pattern._
import name.aloise.diffservice.models.AsyncDataBlockStorage
import name.aloise.diffservice.models.AsyncDataBlockStorage._
import reactivemongo.bson

import scala.concurrent.Future

/**
  * User: aloise
  * Date: 20.05.16
  * Time: 21:35
  */
class MongoBlockActor( ident:String, blockSize:Int, config:Config ) extends BlockStorageActor( ident, blockSize, config, false ) {

  import MongoBlockActor._

  implicit val executionContext = context.system.dispatcher

  lazy val driver = new MongoDriver

  val database = for {
    uri <- Future.fromTry(MongoConnection.parseURI( config.as[String]("uri") ))
    con = driver.connection(uri)
    dn <- Future(uri.db.get)
    db <- con.database(dn)
  } yield db

  // By default, you get a Future[BSONCollection].
  val metadataCollection:Future[BSONCollection] = database.map(_("metadata"))
  val blockCollection:Future[BSONCollection] = database.map(_("blocks"))


  override def receive: Receive = defaultReceive(None)



  def defaultReceive(mt:Option[Metadata] ):Receive = {

    case UpdateMetadata( newMt ) =>
      context.become( defaultReceive( newMt ), discardOld = true )

    case GetMetadata( _ ) =>

      getMetadata() map { metadata =>

        GetMetadataResponse( ident, metadata.fingerprints, metadata.dataSize, metadata.blockSize, isPersistent )
      }  recover {
        case ex:Throwable =>
          GetMetadataResponse( ident, Array(), 0, blockSize, isPersistent )
      } pipeTo sender



    case GetBlock( _, blockNum) =>
      getBlock(mt, blockNum) pipeTo sender


    case SetBlock( _, blockNum, block ) =>
      setBlock( mt, blockNum, block ) pipeTo sender

    case Delete( _ ) =>
      deleteData( mt ) pipeTo sender


  }

  def deleteData( mt: Option[Metadata] ):Future[DeleteResponse] = {
    getMetadata() flatMap { metadata: Metadata =>

      for {
        blockColl <- blockCollection
        metaColl <- metadataCollection
        blockDeleteResult <- blockColl.remove( BSONDocument( "metadataId" -> metadata._id ), firstMatchOnly = false )
        metaDeleteResult <- metaColl.remove( BSONDocument( "_id" -> metadata._id ) )

      } yield DeleteResponse( ident, blockDeleteResult.ok && metaDeleteResult.ok )


    } recover {
      case ex:MetadataNotFoundException =>
        DeleteResponse( ident, success = true )
      case _:Throwable =>
        DeleteResponse( ident, success = false )
    }
  }


  /**
    * Updates the data blocks. It includes the metadata collection update - both fingerprint and data size
 *
    * @param mt Current Metadata
    * @param blockNum Block number starting from zero
    * @param block Data block
    * @return case class that indicates the success of the operation
    */
  def setBlock(mt: Option[Metadata], blockNum: Int, block: Array[Byte]):Future[SetBlockResponse] = {
    getMetadataBSONId(mt, create = true) flatMap { mtId =>

      for {
        blockColl <- blockCollection
        metaColl <- metadataCollection
        currentMetadata <- metaColl.find( BSONDocument( "_id" -> mtId) ).one[BSONDocument].map( value => value.map( metadataHandler.read ) )
        fingerprint = AsyncDataBlockStorage.getBlockFingerprint( block )

        blockSelector = BSONDocument( "metadataId" -> mtId, "blockNum" -> blockNum )
        blockSetData = BSONDocument("$set" -> BSONDocument( "block" -> block, "fingerprint" -> fingerprint ) )
        metaSelector = BSONDocument( "_id" -> mtId  )
        updatedDataSize = Math.max( currentMetadata.map(_.dataSize).getOrElse( 0 ), blockNum*blockSize + block.length )
        metaUpdateData = BSONDocument( "$set" -> BSONDocument( "fingerprints." + blockNum -> BSONLong( fingerprint ), "dataSize" -> updatedDataSize ) )
        blockUpdateResult <- blockColl.update( blockSelector, blockSetData, upsert = true )
        metadataUpdateResult <- metaColl.update( metaSelector, metaUpdateData )

      } yield SetBlockResponse( ident, blockNum, blockUpdateResult.ok && metadataUpdateResult.ok )

    } recover {
      case _: Throwable =>
        // return an empty block
        SetBlockResponse(ident, blockNum, success = false)
    }
  }

  def getBlock(mt: Option[Metadata], blockNum: Int): Future[GetBlockResponse] = {
    getMetadataBSONId(mt, create = false) flatMap { mtId =>
      blockCollection.flatMap { collection =>
        collection.find(BSONDocument("metadataId" -> mtId, "blockNum" -> blockNum)).one[BSONDocument].map { result =>

          result.map(blockHandler.read) match {

            case Some(block) =>
              GetBlockResponse(ident, block.blockNum, Some(block.block), Some(block.fingerprint))

            case None =>
              GetBlockResponse(ident, blockNum, None, None)
          }
        }
      }

    } recover {
      case _: Throwable =>
        // return an empty block
        GetBlockResponse(ident, blockNum, None, None)
    }
  }

  /**
    * Get a current BSON Id of the metadata document or creates a new one
    *
    * @return BSON Object Id Future
    */
  protected def getMetadataBSONId( metadata:Option[Metadata], create:Boolean ):Future[BSONObjectID] = {
      metadata.fold{
        getMetadata() map { mt =>

          mt._id

        } recoverWith {
          case ex: MetadataNotFoundException if create =>
            // create a new metadata block
            metadataCollection.flatMap { collection =>
              val mt = Metadata( BSONObjectID.generate(), ident, Array(), 0, blockSize )
              collection.insert( metadataHandler.write( mt ) ).map { writeResult =>
                if( writeResult.ok ){
                  mt._id
                } else {
                  throw new MetadataWriteFailedException
                }

              }
            }
        }
      } { mt =>
        Future.successful( mt._id )
      }
  }

  protected def getMetadata():Future[Metadata] = {
    metadataCollection.flatMap { collection =>
      collection.find[BSONDocument]( BSONDocument( "ident" -> BSONString( ident ) ) ).one[BSONDocument].map{ bson =>
        val resultOpt: Option[Metadata] = bson.map[Metadata]( metadataHandler.read )

        resultOpt match {
          case Some( result ) =>
            result
          case _ =>
            throw new MetadataNotFoundException
        }


      }

    }
  }


}

object MongoBlockActor {

  abstract class MongoBlockActorException extends Exception
  class MetadataNotFoundException extends MongoBlockActorException
  class MetadataWriteFailedException extends MongoBlockActorException


  case class Block( _id:BSONObjectID, metadataId:BSONObjectID, blockNum:Int, block:Array[Byte], fingerprint: Fingerprint )
  case class Metadata( _id:BSONObjectID, ident:String , fingerprints:Array[Fingerprint], dataSize:Int, blockSize:Int )

  implicit val blockHandler: BSONHandler[BSONDocument, Block] = Macros.handler[MongoBlockActor.Block]
  implicit val metadataHandler: BSONHandler[BSONDocument, Metadata] = Macros.handler[MongoBlockActor.Metadata]

  case class UpdateMetadata( metadata:Option[Metadata] )

  // ensure indexes on ident


}