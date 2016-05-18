package name.aloise.assignment4c.models

/**
  * User: aloise
  * Date: 18.05.16
  * Time: 23:19
  */
object DataComparisonResult extends Enumeration {
  val Equal, DifferentSize, NotEqual = Value
}

case class DataBlock( data:Array[Byte], crc32:Int )


case class DataBlockStorage( size:Int, blocks:Vector[DataBlock], blockSize:Int ) {

  def getDifferenceWith( other:DataBlockStorage ):Seq[DataDifferentPart] = {
    Seq()
  }


}


object DataBlockStorageBuilder {

  def empty = DataBlockStorage(0, Vector(), 0)

  def fromArray( a:Array[Byte], blockSize:Int ) = {
    empty
  }



}


case class DataDifferentPart( start:Int, length:Int )



