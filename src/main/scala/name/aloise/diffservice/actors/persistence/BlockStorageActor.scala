package name.aloise.diffservice.actors.persistence

import akka.actor.Actor
import akka.stream.actor.ActorPublisherMessage.Request
import com.typesafe.config.Config
import name.aloise.diffservice.models.AsyncDataBlockStorage
import name.aloise.diffservice.models.AsyncDataBlockStorage._

/**
  * User: aloise
  * Date: 19.05.16
  * Time: 21:37
  */

abstract class BlockStorageActor( ident:String, blockSize:Int, config:Config, val isPersistent:Boolean ) extends Actor {


  def getFingerprint( block:Array[Byte]):Fingerprint = AsyncDataBlockStorage.getBlockFingerprint(block)

}

object BlockStorageActor {

  sealed trait ExternalMessage
  sealed trait Request extends ExternalMessage
  sealed trait Response extends ExternalMessage

  case class GetMetadata(ident:String ) extends Request
  case class GetMetadataResponse(ident:String, fingerprints:Array[Fingerprint], dataSize:Int, blockSize:Int, isPersistent:Boolean ) extends Response


  case class GetBlock( ident:String, blockNum:Int ) extends Request
  case class GetBlockResponse( ident:String, blockNum:Int, block:Option[Array[Byte]], fingerprint: Option[Fingerprint]) extends Response

  case class SetBlock( ident:String, blockNum:Int, block:Array[Byte] ) extends Request
  case class SetBlockResponse( ident:String, blockNum:Int, success:Boolean ) extends Response


  // deletes all blocks
  case class Delete( ident:String ) extends Request
  case class DeleteResponse( ident:String, success:Boolean ) extends Response

}