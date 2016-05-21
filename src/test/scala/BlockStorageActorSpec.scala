import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.Config
import name.aloise.assignment4c.actors.persistence.BlockStorageActor._
import name.aloise.assignment4c.actors.persistence._
import name.aloise.assignment4c.models.AsyncDataBlockStorage
import name.aloise.assignment4c.models.AsyncDataBlockStorage.Fingerprint
import name.aloise.assignment4c.server.DiffService.Params.UpdateDataBlock
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

/**
  * User: aloise
  * Date: 20.05.16
  * Time: 18:57
  */
abstract class BlockStorageActorSpec( val actorName:String, val config:Config, val isPersistent: Boolean) extends TestKit(ActorSystem( actorName + "PersistenceBlockActorSpecSystem")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def getActorProps( ident:String, blockSize:Int ):Props

  val blockSize = 4096


  actorName + " Block Storage Actor " should {

    val randomIdent = "random-ident-"+System.currentTimeMillis()

    val dataBlock0: Array[Byte] = ( 0 until blockSize ).map(_.toByte).toArray
    val dataBlock0Fingerprint = AsyncDataBlockStorage.getBlockFingerprint( dataBlock0 )

    val altDataBlock0:Array[Byte] = ( 0 until blockSize ).map( b => ( b*37 ).toByte ).toArray
    val altDataBlock0Fingerprint = AsyncDataBlockStorage.getBlockFingerprint( altDataBlock0 )

    val dataBlock1:Array[Byte] = ( 0 until blockSize/2 ).map( _.toByte ).toArray
    val dataBlock1Fingerprint = AsyncDataBlockStorage.getBlockFingerprint( dataBlock1 )

    val actor = system.actorOf(getActorProps(randomIdent, blockSize ))

    "receive the metadata info" in {
      actor ! GetMetadata( randomIdent )
      expectMsgType[GetMetadataResponse]
    }

    "return an empty block info initially" in {
      actor ! GetBlock( randomIdent, 0 )
      val msg = expectMsgType[GetBlockResponse]

      msg.block shouldBe empty
      msg.blockNum shouldBe 0
      msg.fingerprint shouldBe empty
    }

    "update the data block 0" in {
      actor ! SetBlock( randomIdent, 0, dataBlock0 )
      expectMsgType[SetBlockResponse]
    }

    "check the resulting size of 1 block updated" in {
      actor ! GetMetadata( randomIdent )

      val metadata = expectMsgType[GetMetadataResponse]
      metadata.dataSize shouldBe dataBlock0.length
      metadata.ident shouldBe randomIdent
      metadata.dataSize shouldBe dataBlock0.length

    }

    "update the data block 1" in {
      actor ! SetBlock( randomIdent, 1, dataBlock1 )
      val msg = expectMsgType[SetBlockResponse]
      msg.blockNum shouldBe 1
      msg.ident shouldBe randomIdent
    }

    "check the resulting size" in {
      actor ! GetMetadata( randomIdent )

      val metadata = expectMsgType[GetMetadataResponse]
      metadata.ident shouldBe randomIdent
      metadata.dataSize shouldBe dataBlock0.length + dataBlock1.length
      metadata.fingerprints should contain allOf ( dataBlock0Fingerprint, dataBlock1Fingerprint )
    }

    "return the data from block 1" in {
      actor ! GetBlock( randomIdent, 1 )
      val response = expectMsgType[GetBlockResponse]

      response.ident shouldBe randomIdent
      response.block shouldBe defined
      response.block.get shouldBe dataBlock1
    }

    "return the data from block 0" in {
      actor ! GetBlock( randomIdent, 0 )
      val response = expectMsgType[GetBlockResponse]

      response.ident shouldBe randomIdent
      response.block shouldBe defined
      response.block.get shouldBe dataBlock0
    }

    "accept the other block 0" in {
      actor ! SetBlock( randomIdent, 0, altDataBlock0 )
      expectMsgType[SetBlockResponse]
    }

    "contain and updated block 0" in {
      actor ! GetBlock( randomIdent, 0 )
      val response = expectMsgType[GetBlockResponse]

      response.ident shouldBe randomIdent
      response.block shouldBe defined
      response.block.get shouldBe altDataBlock0
      response.fingerprint should contain ( altDataBlock0Fingerprint )
    }

    "delete the data properly" in {
      actor ! Delete( randomIdent )
      expectMsgType[DeleteResponse]
    }

    "report a zero size once removed" in {
      actor ! GetMetadata( randomIdent )
      val response = expectMsgType[GetMetadataResponse]
      response.ident shouldBe randomIdent
      response.dataSize shouldBe 0
      response.fingerprints.length shouldBe 0
    }

    if( isPersistent ) {
      // test the persistence
      val persistArray = ( 0 until blockSize/3 ).map(_.toByte).toArray
      val persistArrayFingerprint = AsyncDataBlockStorage.getBlockFingerprint( persistArray )

      "accept a block to persist" in {
        actor ! SetBlock( randomIdent, 0, persistArray )
        expectMsgType[SetBlockResponse]
      }

      "should terminate correctly" in {
        val probe = TestProbe()
        probe watch actor
        actor ! PoisonPill
        probe.expectTerminated(actor)
      }

      val newActor = system.actorOf(getActorProps(randomIdent, blockSize ))

      "recreate it under the same id and return the metadata" in {
        newActor ! GetMetadata( randomIdent )
        val metadataResponse = expectMsgType[GetMetadataResponse]

        metadataResponse.dataSize shouldBe persistArray.length
      }

      "return the current persisted block" in {
        newActor ! GetBlock( randomIdent, 0 )
        val response = expectMsgType[GetBlockResponse]

        response.fingerprint should contain ( persistArrayFingerprint )

        response.block should contain ( persistArray )
      }


      "remove the persisted data correctly" in {
        newActor ! Delete( randomIdent )
        expectMsgType[DeleteResponse]
        newActor ! GetMetadata( randomIdent )
        val msg = expectMsgType[GetMetadataResponse]
        msg.dataSize shouldBe 0
        msg.ident shouldBe randomIdent

      }



    }

  }


  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


}
