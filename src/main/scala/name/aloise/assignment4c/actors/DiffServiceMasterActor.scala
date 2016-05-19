package name.aloise.assignment4c.actors

import akka.actor.{Actor, ActorRef, Props}
import akka.actor.Actor.Receive
import name.aloise.assignment4c.models.DataComparisonResult

/**
  * User: aloise
  * Date: 18.05.16
  * Time: 20:38
  */
class DiffServiceMasterActor( dataBlockSize:Int ) extends Actor {

  import DiffServiceActor._

  override def receive = defaultBehavior( Map() )

  def defaultBehavior( mapping:Map[String,ActorRef] ) :Receive = {

    case p@PushLeft( ident, data ) =>

      getActor( ident, mapping ) forward p

    case p@PushRight( ident, data ) =>

      getActor( ident, mapping ) forward p

    case c@CompareRequest( ident:String ) =>
      if( mapping.contains( ident ) ){
        mapping( ident ) forward c
      } else {
        // actor not found - return the response immediately
        sender ! CompareResponse( ident, DataComparisonResult.IdentNotFound )

      }

  }

  def getActor(ident:String, mapping:Map[String,ActorRef]) = {
    val actor = mapping.getOrElse( ident , { spawnActor(ident) } )

    if( !mapping.contains(ident)){
      context.become( defaultBehavior( mapping + ( ident -> actor ) ) , discardOld = true )
    }

    actor
  }



  def spawnActor( ident:String ) =
    context.actorOf( Props( classOf[DiffServiceActor], ident, dataBlockSize ) )

}
