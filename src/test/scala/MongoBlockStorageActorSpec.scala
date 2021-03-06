import akka.actor.Props
import com.typesafe.config.ConfigFactory
import name.aloise.diffservice.actors.persistence.{MemoryBlockActor, MongoBlockActor}

/**
  * User: aloise
  * Date: 20.05.16
  * Time: 19:23
*/
class MongoBlockStorageActorSpec extends BlockStorageActorSpec("Mongo", ConfigFactory.parseString("{ uri = \"mongodb://localhost/test_assignment4c\" }")  , true ) {

  override def getActorProps(ident: String, blockSize: Int): Props = {
    Props( classOf[MongoBlockActor], ident, blockSize, config )
  }
}
