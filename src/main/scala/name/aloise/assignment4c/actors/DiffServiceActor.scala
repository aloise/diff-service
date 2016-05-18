package name.aloise.assignment4c.actors

import akka.actor.Actor
import name.aloise.assignment4c.models._

/**
  * User: aloise
  * Date: 18.05.16
  * Time: 22:35
  *
  * master actor is the parent actor
  *
  */
class DiffServiceActor( id:String ) extends Actor {

  def receive = {
    case _ =>
  }

  def defaultBehavior( left:DataBlockStorage, right:DataBlockStorage ) = {

  }


}

object DiffServiceActor {


  sealed trait Message

  case class PushLeft( ident:String, data:Array[Byte]) extends Message
  case class PushRight( ident:String, data:Array[Byte]) extends Message
  case class CompareRequest( ident:String ) extends Message
  case class CompareResponse( ident:String, comparisonResult: DataComparisonResult.Value, difference:Seq[DataDifferentPart] = Seq() ) extends Message

}