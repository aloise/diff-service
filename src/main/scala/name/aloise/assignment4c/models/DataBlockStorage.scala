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


case class DataBlockStorage( size:Int, blocks:Vector[DataBlock] ) {

  def this( data:Array[Byte]) = {
    this( data.length, Vector() )
  }

  def this() = {
    this(0, Vector())
  }

  def getDifferenceWith( other:DataBlockStorage ):Seq[DataDifferentPart] = {
    Seq()
  }


}



case class DataDifferentPart( start:Int, length:Int )



