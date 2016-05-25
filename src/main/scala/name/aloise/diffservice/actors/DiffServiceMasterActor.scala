package name.aloise.diffservice.actors

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.actor.Actor.Receive
import name.aloise.diffservice.actors.persistence.PersistenceActorProxy
import name.aloise.diffservice.models.DataComparisonResult

/**
  * User: aloise
  * Date: 18.05.16
  * Time: 20:38
  */
class DiffServiceMasterActor( dataBlockSize:Int, persistenceActorProps: String => Props ) extends Actor {

  import DiffServiceActor._

  override def receive = defaultBehavior( Map() )

  def defaultBehavior( mapping:Map[String,ActorRef] ) :Receive = {

    case msg@PushData( ident, _, _ ) =>

      getActor( ident, mapping ) forward msg

    case msg@PushDataBlock( ident, _, _ ) =>
      getActor( ident, mapping ) forward msg

    case c@CompareRequest( ident  ) =>
      if( mapping.contains( ident ) ){
        mapping( ident ) forward c
      } else {
        // actor not found - return the response immediately
        sender ! CompareResponse( ident, DataComparisonResult.IdentNotFound )

      }

    case msg@Remove( ident ) =>
      mapping.get( ident ).foreach{ actor =>
        // actor would kill itself on completion
        actor forward msg
        // actor ! PoisonPill
        context.become( defaultBehavior( mapping - ident ), discardOld = true )

      }
      if( !mapping.contains( ident ) ) {
        // send a response immediately
        sender ! RemoveResponse( ident )
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
    context.actorOf(
      Props( classOf[DiffServiceActor], ident, dataBlockSize, persistenceActorProps )
    )

}
