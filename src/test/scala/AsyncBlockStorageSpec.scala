
import java.util.zip.CRC32

import name.aloise.diffservice.models.AsyncDataBlockStorage.Fingerprint
import name.aloise.diffservice.models._
import org.scalatest._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
/**
  * User: aloise
  * Date: 19.05.16
  * Time: 9:53
  */
class AsyncBlockStorageSpec extends WordSpec with Matchers {

  "DataBlockStorage" when {

    "empty" should {
      "have zero size" in {
        AsyncDataBlockStorage.empty.size shouldBe 0
      }

      "have zero count of fingerprints" in {
        AsyncDataBlockStorage.empty.fingerprints.length shouldBe 0
      }

      "be equal with other empty data block" in {
        Await.result( AsyncDataBlockStorage.empty.getDifferenceWith( AsyncDataBlockStorage.empty ), 30.seconds ) shouldBe Nil
      }


    }


    def fromArray( a:Array[Byte], blockSize:Int = 4 ) = {

      val blockCount = (a.length - 1) / blockSize // amount of blocks minus 1
      val blocks = Array.tabulate[Array[Byte]]( blockCount + 1 ){i =>
          a.slice( i*blockSize, Math.min(a.length, (i+1)*blockSize ) )
        }

      val fingerprints = Array.tabulate[Fingerprint]( blockCount + 1 ){ i =>
        AsyncDataBlockStorage.getBlockFingerprint( blocks(i) )

      }

      new AsyncDataBlockStorage( a.length, ( i:Int ) => Future.successful( blocks(i) ), blockSize, fingerprints  )

    }


    "non-empty" should {
      "be equal with other non-empty data block ( comparison is done by min array size of two )" in {

        val result = Await.result( AsyncDataBlockStorage.empty.getDifferenceWith( fromArray( Array[Byte](1,2,3,4) ) ), 30.seconds )
        result shouldBe Nil
      }

      "return a correct block from fixed array" in {
        val result = Await.result( fromArray( Array[Byte](1,2,3,4,5), 4 ).blocks(0), 30.seconds )
        result shouldBe Array( 1,2,3,4 )
      }

      "have correct amount of fingerprints based on block size 4 with 5 elements in array" in {
        fromArray( Array[Byte](1,2,3,4,5), 4 ).fingerprints.length shouldBe 2
      }


      "have correct amount of fingerprints based on block size 4 with 2 elements in array" in {
        fromArray( Array[Byte]( 1,2 ), 4 ).fingerprints.length shouldBe 1
      }

      "calculate the CRC32 fingerprint correctly" in {
        val a = Array[Byte](1,2,3,4,5)
        val crc32 = new CRC32()
        crc32.update( Array[Byte](1,2,3,4) )

        fromArray( a ).fingerprints(0) shouldBe crc32.getValue
      }


      "return an empty list of different blocks for equal arrays" in {
        val result = fromArray( Array[Byte](1,2,3,4,5) ) getDifferenceWith fromArray(Array[Byte](1, 2, 3, 4, 5))

        Await.result( result, 30.seconds ) shouldBe Nil
      }



      "return a empty list of different blocks for 2 array equal to min size of two" in {
        val result = fromArray( Array[Byte](1,2,3,4,5) ) getDifferenceWith fromArray( Array[Byte](1,2,3,4,5,6,7,8) )
        Await.result( result, 30.seconds ) shouldBe Nil
      }

      "should merge indexes properly - case 1 same bounds " in {
        val indexes = List( (0,5 ), (5,7 ), ( 15,20 ) )
        AsyncDataBlockStorage.fuseIndexes( indexes ) should contain allOf ( (0,7), (15,20) )
      }

      "should merge indexes properly - case 2 - intersect " in {
        val indexes = List( (0,5 ), (6,7 ), ( 15,20 ) )
        AsyncDataBlockStorage.fuseIndexes( indexes ) should contain allOf ( (0,7), (15,20) )
      }

      "should merge indexes properly - case 3 - includes " in {
        val indexes = List( (0,7 ), (3,4 ), ( 15,20 ) )
        AsyncDataBlockStorage.fuseIndexes( indexes ) should contain allOf ( (0,7), (15,20) )
      }

      "should merge indexes properly - case 4 - no intersection" in {
        val indexes = List( (0,1 ), (3,4 ), ( 15,20 ) )
        AsyncDataBlockStorage.fuseIndexes( indexes ) should contain allOf ( (3,4), (0,1), (15,20) )
      }

      "should merge indexes properly - case 4 - same blocks" in {
        val indexes = List( (0,1 ), (0,1 ), ( 15,20 ) )
        AsyncDataBlockStorage.fuseIndexes( indexes ) shouldBe List( (0,1), (15,20) )
      }


      "return a correct list of different blocks - case 1" in {
        val resultF = fromArray( Array[Byte](1,2,3,4,5,6) ) getDifferenceWith fromArray(Array[Byte](-1, 2, -3, 4, 5, -6))
        val result = Await.result( resultF, 30.seconds )
        result.length shouldBe 3
        result should contain allOf( DataDifferentPart(0,1), DataDifferentPart(2,1), DataDifferentPart(5,1) )
      }


      "return a correct list of different blocks with blocks size - 5" in {
        val resultF = fromArray( Array[Byte](1,2,3,4,5,6,7,8,9,10), 5 ) getDifferenceWith fromArray( Array[Byte](-1,-2,-3,4,5,6,7,8,9,10) , 5 )
        val result = Await.result( resultF, 30.seconds )
        result shouldBe List( DataDifferentPart(0,3) )
      }

      "return a correct list of different blocks - 2 items" in {
        val resultF = fromArray( Array[Byte](1,2,3,4,5,6,7,8,9,10,11), 4 ). getDifferenceWith( fromArray( Array[Byte](-1,-2,-3,4,5,6,7,8,-9,-10,-11) , 4 ) )
        val result = Await.result( resultF, 30.seconds )

        result should contain allOf ( DataDifferentPart(0,3) , DataDifferentPart(8,3) )
      }



    }


  }


}
