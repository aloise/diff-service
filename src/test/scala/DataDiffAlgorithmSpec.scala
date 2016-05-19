
import java.util.zip.CRC32

import name.aloise.assignment4c.models.{DataBlockStorageBuilder, DataDifferentPart}
import org.scalatest._
/**
  * User: aloise
  * Date: 19.05.16
  * Time: 9:53
  */
class DataDiffAlgorithmSpec extends WordSpec with Matchers {

  "DataBlockStorage" when {

    "empty" should {
      "have zero size" in {
        DataBlockStorageBuilder.empty.size shouldBe 0
      }

      "have zero data length" in {
        DataBlockStorageBuilder.empty.data.length shouldBe 0
      }

      "have zero count of fingerprints" in {
        DataBlockStorageBuilder.empty.fingerprints.length shouldBe 0
      }

      "be equal with other empty data block" in {
        DataBlockStorageBuilder.empty.getDifferenceWith( DataBlockStorageBuilder.empty ) shouldBe Nil
      }

      "be equal with other non-empty data block ( comparision is done by min array size of two )" in {
        DataBlockStorageBuilder.empty.getDifferenceWith( DataBlockStorageBuilder.fromArray( Array[Byte](1,2,3,4) ) ) shouldBe Nil
      }

    }

    "non-empty" should {
      "have a correct length" in {
        DataBlockStorageBuilder.fromArray( Array[Byte](1,2,3,4) ).data.length shouldBe 4
      }

      "not accept the block size less than 4" in {
        an [Exception] should be thrownBy DataBlockStorageBuilder.fromArray( Array[Byte](1,2,3,4), 2 )
      }

      "have correct amount of fingerprints based on block size 4 with 5 elements in array" in {
        DataBlockStorageBuilder.fromArray( Array[Byte](1,2,3,4,5), 4 ).fingerprints.length shouldBe 2
      }

      "have correct amount of fingerprints based on block size 4 with 2 elements in array" in {
        DataBlockStorageBuilder.fromArray( Array[Byte]( 1,2 ), 4 ).fingerprints.length shouldBe 1
      }

      "calculate the CRC32 fingerprint correctly" in {
        val a = Array[Byte](1,2,3,4,5)
        val crc32 = new CRC32()
        crc32.update( Array[Byte](1,2,3,4) )

        DataBlockStorageBuilder.fromArray( a ).fingerprints(0) shouldBe crc32.getValue
      }

      "return an empty list of different blocks for equal arrays" in {
        val result =
          DataBlockStorageBuilder.fromArray( Array[Byte](1,2,3,4,5) ).
            getDifferenceWith( DataBlockStorageBuilder.fromArray( Array[Byte](1,2,3,4,5) ) )
        result shouldBe Nil
      }

      "return a empty list of different blocks for 2 array equal to min size of two" in {
        val result =
          DataBlockStorageBuilder.fromArray( Array[Byte](1,2,3,4,5) ).
            getDifferenceWith( DataBlockStorageBuilder.fromArray( Array[Byte](1,2,3,4,5,6,7,8) ) )
        result shouldBe Nil
      }

      "return a correct count of different blocks" in {
        val result =
          DataBlockStorageBuilder.fromArray( Array[Byte](1,2,3,4,5,6) ).
            getDifferenceWith( DataBlockStorageBuilder.fromArray( Array[Byte](-1,2,-3,4,5,-6) ) )
        result.length shouldBe 3
      }


      "return a correct list of different blocks" in {
        val result =
          DataBlockStorageBuilder.fromArray( Array[Byte](1,2,3,4,5,6,7,8,9,10), 4 ).
            getDifferenceWith( DataBlockStorageBuilder.fromArray( Array[Byte](-1,-2,-3,4,5,6,7,8,9,10) , 4 ) )
        result.head shouldBe DataDifferentPart(0,3)
      }

      "return a correct list of different blocks - 2 items" in {
        val result =
          DataBlockStorageBuilder.fromArray( Array[Byte](1,2,3,4,5,6,7,8,9,10,11), 4 ).
            getDifferenceWith( DataBlockStorageBuilder.fromArray( Array[Byte](-1,-2,-3,4,5,6,7,8,-9,-10,-11) , 4 ) )


        result should contain allOf ( DataDifferentPart(0,3) , DataDifferentPart(8,3) )
      }


    }

  }


}
