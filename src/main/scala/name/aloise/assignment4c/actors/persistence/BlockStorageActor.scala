package name.aloise.assignment4c.actors.persistence

import akka.actor.Actor
import name.aloise.assignment4c.models.AsyncDataBlockStorage
import name.aloise.assignment4c.models.AsyncDataBlockStorage._

/**
  * User: aloise
  * Date: 19.05.16
  * Time: 21:37
  */

abstract class BlockStorageActor( ident:String, blockSize:Int ) extends Actor {


  def getFingerprint( block:Array[Byte]):Fingerprint = AsyncDataBlockStorage.getBlockFingerprint(block)

}

object BlockStorageActor {

  case class GetBlock( ident:String, blockNum:Int )
  case class GetBlockResponse( ident:String, blockNum:Int, block:Option[Array[Byte]], fingerprint: Option[Fingerprint])

  case class GetMetadata(ident:String )
  case class GetMetadataResponse(ident:String, fingerprints:Array[Fingerprint], dataSize:Int )

  case class SetBlock( ident:String, blockNum:Int, block:Array[Byte] )
  case class SetBlockResponse( ident:String, blockNum:Int, success:Boolean )


  // deletes all blocks
  case class Delete( ident:String )
  case class DeleteResponse( ident:String, success:Boolean )

}