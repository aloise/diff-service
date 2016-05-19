package name.aloise.assignment4c.actors.persistence

import akka.actor.Actor

/**
  * User: aloise
  * Date: 19.05.16
  * Time: 21:37
  */

abstract class BlockStorageActor( ident:String ) extends Actor {

}

object BlockStorageActor {

  case class GetBlock( ident:String, blockNum:Int )
  case class SetBlock( ident:String, blockNum:Int, block:Array[Byte] )
  case class GetBlockResponse( ident:String, blockNum:Int, block:Option[Array[Byte]])
  case class Delete( ident:String )

}