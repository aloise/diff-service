package name.aloise.assignment4c.models

import java.util.zip.CRC32

import scala.annotation.tailrec
import scala.collection.immutable.::

/**
  * User: aloise
  * Date: 18.05.16
  * Time: 23:19
  */
object DataComparisonResult extends Enumeration {
  val Equal, DifferentSize, NotEqual, IdentNotFound = Value
}


class DataBlockStorage( val size:Int, val data:Array[Byte], val blockSize:Int, val fingerprints:Array[DataBlockStorageBuilder.Fingerprint] ) {


  protected def getDifferenceWithinBlock(that: DataBlockStorage, block: Int) = {
    val rightBoundary = Math.min( ( block + 1)*blockSize, Math.min( this.size, that.size ) )

    // contains indexes of current block start and end. Inclusive indexes
    var currentBlock:Option[(Int,Int)] = None
    var blocks:List[(Int,Int)] = List()

    for( i <- block*blockSize until rightBoundary ){

      // data is not equal, add it to the block
      if( this.data(i) != that.data(i) ){
        // updates an index or creates a new block
        currentBlock = Some( currentBlock.fold( ( i, i ) ){ case ( start, end ) => (start, i ) } )

      } else {
        // add a non-empty block to a list of diff blocks
        currentBlock.foreach{ blockData =>
          blocks = blockData :: blocks
        }

        // reset current block data
        currentBlock = None

      }
    }

    currentBlock.fold( blocks ){ finalBlock => finalBlock :: blocks }


  }

  /**
    * Calculates the list of different blocks from `that` data block. It would check only bytes up to a min size of both
    * @param that Another block to check with
    * @return
    */
  def getDifferenceWith(that:DataBlockStorage ):List[DataDifferentPart] = {
    // compare block fingerprints
    val maxBlocks = Math.min( this.fingerprints.length, that.fingerprints.length )

    // compare fingerprints first
    val blocks =
      for{
        i <- 0 until maxBlocks
        if this.fingerprints(i) != that.fingerprints(i)
      } yield getDifferenceWithinBlock( that, i )

    // combine multiple indexes
    DataBlockStorageBuilder.fuseIndexes( blocks.toList.flatten ).map { case ( startIndex, endIndex ) =>
      // convert indexes into block length
      DataDifferentPart( startIndex, endIndex - startIndex + 1 )
    }

  }


}


object DataBlockStorageBuilder {

  /**
    * Calculates the fingerprint
    *
    * @param a Array
    * @param from Inclusive index
    * @param to Exclusive index
    */

  type Fingerprint = Long

  /**
    * Calculate the CRC32 fingerprint of the array block [from..to) ( from - inclusive, to - exclusive )
    * @param a Input array
    * @param from start index - inclusive
    * @param to end index - exclusive
    * @return a CRC32 Fingerprint
    */

  def getFingerprint( a:Array[Byte], from:Int, to:Int ):Fingerprint = {
    val crc32 = new CRC32()

    crc32.update( a, from, to - from )

    crc32.getValue

  }


  def empty = new DataBlockStorage(0, Array[Byte](), 0, Array[Fingerprint]())

  /**
    * Builds a data storage block. Creates a list of fingerprints for every block of blockSize bytes
    * @param a Initial Array
    * @param blockSize Block Size int bytes. Min value - 4 as smaller value won't makes sense with CRC32
    * @return
    */
  def fromArray( a:Array[Byte], blockSize:Int = 4 ) = {

    require( blockSize >= 4 )

    val blockCount = ( a.length - 1 ) / blockSize // amount of blocks minus 1

    val fingerprintArray = if( blockCount >= 0 ) Array.fill[Fingerprint]( blockCount + 1 )(0) else Array[Fingerprint]()

    for( i <- 0 to blockCount ){
      val rightIndex = Math.min( a.length, ( i + 1 )*blockSize )
      fingerprintArray( i ) = getFingerprint( a, i*blockSize, rightIndex )
    }

    new DataBlockStorage( a.length, a, blockSize, fingerprintArray )


  }

  /**
    * Combine adjacent ( start, end ) indexes.
    */
  def fuseIndexes( indexes:List[(Int,Int)] ):List[(Int,Int)] = {
    indexes.sortBy( _._1 ) match {
      case ( start0, end0 ) :: ( start1, end1 ) :: tail =>
        if( end0 >= end1 ){
          // consume smaller block
          fuseIndexes(  ( start0, end0 ) :: tail )
        } else if( end0 + 1 >= start1 ){
          // fuse two records
          fuseIndexes(  ( start0, end1 ) :: tail )
        } else {
          ( start0, end0 ) :: fuseIndexes( indexes.tail )
        }

      case _ =>
        indexes
    }
  }



}


case class DataDifferentPart( start:Int, length:Int )



