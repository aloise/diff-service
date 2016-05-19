package name.aloise.assignment4c.actors.persistence

import akka.actor.Actor

/**
  * User: aloise
  * Date: 19.05.16
  * Time: 20:18
  */
abstract class MemoryBlockActor( ident:String ) extends BlockStorageActor( ident ) {

  import BlockStorageActor._

  val blocks = collection.mutable.Map[Int,Array[Byte]]()


  def receive = {
    case GetBlock( ident2, blockNum) =>
      GetBlockResponse( ident, blockNum, blocks.get( blockNum )  )

    case SetBlock( ident2, blockNum, block ) =>
      blocks.update( blockNum, block )

    case Delete( ident2 ) =>
      blocks.clear()

  }



}

