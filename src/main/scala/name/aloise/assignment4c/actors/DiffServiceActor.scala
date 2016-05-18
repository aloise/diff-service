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
class DiffServiceActor( id:String, blockSize:Int ) extends Actor {

  import DiffServiceActor._

  type ComputedResult = (DataComparisonResult.Value, Seq[DataDifferentPart])
  type ComputedResultOpt = Option[ComputedResult]

  /**
    * By default data array are empty and equal
    * @return
    */
  def receive =
    defaultBehavior( DataBlockStorageBuilder.empty, DataBlockStorageBuilder.empty, Some( DataComparisonResult.Equal, Seq() ) )

  def defaultBehavior( left:DataBlockStorage, right:DataBlockStorage, comparisonResult: ComputedResultOpt ):Receive = {

    case PushLeft( ident, data ) if ident == id =>
      context.become( defaultBehavior( DataBlockStorageBuilder.fromArray(data, blockSize), right, None ), discardOld = true)


    case PushRight( ident, data ) if ident == id =>
      context.become( defaultBehavior( left, DataBlockStorageBuilder.fromArray(data, blockSize), None ), discardOld = true)


    case CompareRequest( ident ) if ident == id =>

      val result:ComputedResult = comparisonResult.getOrElse {

        if( left.size == right.size ){
          val diff = left getDifferenceWith right
          val result = if( diff.isEmpty) DataComparisonResult.Equal else DataComparisonResult.NotEqual
          ( result, diff )

        } else {
          ( DataComparisonResult.DifferentSize, Seq() )
        }
      }

      sender ! CompareResponse( id, result._1, result._2 )

      context.become( defaultBehavior( left, right, Some(result) ) , discardOld = true)


  }


}

object DiffServiceActor {

  sealed trait Message

  case class PushLeft( ident:String, data:Array[Byte]) extends Message
  case class PushRight( ident:String, data:Array[Byte]) extends Message
  case class CompareRequest( ident:String ) extends Message
  case class CompareResponse( ident:String, comparisonResult: DataComparisonResult.Value, difference:Seq[DataDifferentPart] = Seq() ) extends Message

}