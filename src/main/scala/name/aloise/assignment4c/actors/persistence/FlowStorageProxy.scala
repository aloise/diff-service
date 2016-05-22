package name.aloise.assignment4c.actors.persistence

import akka.actor.{Actor, ActorRef, PoisonPill, Stash}
import name.aloise.assignment4c.actors.DiffServiceActor.{PushDataBlock, PushDataBlockResponse}

/**
  * User: aloise
  * Date: 22.05.16
  * Time: 12:48
  */
class FlowStorageProxy( ident:String, stream:String, master:ActorRef ) extends Actor with Stash {

  import FlowStorageProxy._
  var onCompleteRef:ActorRef = Actor.noSender
  var blockResponses = collection.mutable.ArrayBuffer[Boolean]()

  def receive = init

  def init:Receive = complete orElse {

    case Init( _ ) =>
      // nothing
      sender() ! AckMessage()
      context.become(onRequestAwait, discardOld = true)

  }

  def onRequestAwait:Receive = complete orElse  {

    case p:PushDataBlock =>
      master ! p
      context.become( awaitResponse(sender()), discardOld = true )

  }

  def awaitResponse(ackSender:ActorRef):Receive = {

    case PushDataBlockResponse( _, _, success ) =>
      ackSender ! AckMessage()

      blockResponses.prepend( success )

      unstashAll()
      context.become( onRequestAwait, discardOld = true )

    case _ =>
      stash()

  }

  def complete:Receive = {

    case IsComplete( _ ) =>
      onCompleteRef = sender()

    case Complete( _ )  =>
      println("COMPLETE")
//      sender() ! AckMessage
      onCompleteRef ! PushBlockResponses( blockResponses )
      self ! PoisonPill

  }

}

object FlowStorageProxy {

  case class AckMessage(i:Int = 0)
  case class Init(i:Int = 0)
  case class Complete(i:Int = 0)
  case class PushBlockResponses( responses:Seq[Boolean] )
  case class IsComplete( i:Int = 0)
}