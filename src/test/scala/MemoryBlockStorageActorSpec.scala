import akka.actor.Props
import com.typesafe.config.{Config, ConfigFactory}
import name.aloise.assignment4c.actors.persistence.MemoryBlockActor
/**
  * User: aloise
  * Date: 20.05.16
  * Time: 19:23
  */
class MemoryBlockStorageActorSpec extends BlockStorageActorSpec("Memory", ConfigFactory.empty(), false ) {

  override def getActorProps(ident: String, blockSize: Int): Props = {
    Props( classOf[MemoryBlockActor], ident, blockSize, config )
  }
}
