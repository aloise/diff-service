package name.aloise.assignment4c.actors

import akka.actor.{Actor, ActorRef}
import akka.actor.Actor.Receive

/**
  * User: aloise
  * Date: 18.05.16
  * Time: 20:38
  */
class DiffServiceMasterActor extends Actor {

  override def receive = defaultBehavior( Map() )

  def defaultBehavior( mapping:Map[String,ActorRef] ) :Receive = {
    case _ =>

  }

}
