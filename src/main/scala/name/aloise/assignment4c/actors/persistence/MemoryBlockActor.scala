package name.aloise.assignment4c.actors.persistence

import akka.actor.Actor
import com.typesafe.config.Config
import name.aloise.assignment4c.models.AsyncDataBlockStorage.Fingerprint


/**
  * User: aloise
  * Date: 19.05.16
  * Time: 20:18
  */
class MemoryBlockActor( ident:String, blockSize:Int, config:Config ) extends BlockStorageActor( ident, blockSize, config, false ) {

  import BlockStorageActor._

  val blocks = collection.mutable.Map[Int,( Array[Byte], Fingerprint )]( )

  var dataSize:Int = 0

  def receive = {
    case GetBlock( _, blockNum) =>

      val block = blocks.get( blockNum )

      sender ! GetBlockResponse( ident, blockNum, block.map(_._1), block.map(_._2) )

    case SetBlock( _, blockNum, block ) =>
      blocks.update( blockNum, ( block, getFingerprint(block) ) )

      // increase the data size
      dataSize = Math.max( dataSize, blockNum*blockSize + block.length )

      sender ! SetBlockResponse( ident, blockNum, success = true)

    case Delete( _ ) =>
      dataSize = 0
      blocks.clear()
      sender ! DeleteResponse( ident, success = true )

    case GetMetadata( _ ) =>
      // collect fingerprints
      val fg = Array.fill[Fingerprint]( blocks.size )( 0 )
      for( ( blockNum, ( _, fingerprint ) ) <- blocks ){
        if( blockNum < fg.length ){
          fg( blockNum ) = fingerprint
        }
      }

      sender ! GetMetadataResponse( ident, fg, dataSize, blockSize, isPersistent )

  }



}

