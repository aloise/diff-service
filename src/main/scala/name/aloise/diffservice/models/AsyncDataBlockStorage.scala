package name.aloise.diffservice.models

import java.util.zip.CRC32

import scala.collection.immutable.{::, IndexedSeq}
import scala.concurrent.{ExecutionContext, Future}

/**
  * User: aloise
  * Date: 19.05.16
  * Time: 21:54
  */

object DataComparisonResult extends Enumeration {
  val Equal, DifferentSize, NotEqual, IdentNotFound = Value
}

case class DataDifferentPart( start:Int, length:Int )

object AsyncDataBlockStorage {

  type Fingerprint = Long

  def getBlockFingerprint( a:Array[Byte] ):Fingerprint = {
    val crc32 = new CRC32()

    crc32.update( a )

    crc32.getValue

  }

  def empty = new AsyncDataBlockStorage(0, _ => Future.failed(new IndexOutOfBoundsException()), 0, Array[Fingerprint]())

  /**
    * Combine adjacent ( start, end ) indexes.
    * TODO inefficient version!
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
          ( start0, end0 ) :: fuseIndexes( ( start1, end1 ) :: tail )
        }

      case _ =>
        indexes
    }
  }


}

case class AsyncDataBlockStorage( size:Int, blocks: Int => Future[Array[Byte]], blockSize:Int, fingerprints:Array[AsyncDataBlockStorage.Fingerprint] ) {


  def getDifferenceWith(that:AsyncDataBlockStorage )( implicit ec:ExecutionContext ):Future[List[DataDifferentPart]] = {
    // compare block fingerprints
    val maxBlocks = Math.min( this.fingerprints.length, that.fingerprints.length )

    // compare fingerprints first
    val differentBlocksFuture: IndexedSeq[Future[(Int, (Array[Byte], Array[Byte]))]] =
      for{
        i <- 0 until maxBlocks
        if this.fingerprints(i) != that.fingerprints(i)
      } yield ( blocks( i ) zip that.blocks( i ) ).map( data => ( i, data ) )

    val diffListFuture: IndexedSeq[Future[List[(Int, Int)]]] =
      differentBlocksFuture.map { futureResult =>
        futureResult.map { case (blockNum, (thisBlock, thatBlock)) =>
          getDifferenceWithinBlock(thisBlock, thatBlock, blockNum)
        }
      }

    Future.sequence( diffListFuture ).map{ list =>
      // combine multiple indexes
      AsyncDataBlockStorage.fuseIndexes( list.flatten.toList ).map { case ( startIndex, endIndex ) =>
        // convert indexes into block length
        DataDifferentPart( startIndex, endIndex - startIndex + 1 )
      }
    }
  }


  /**
    * Compare whole block and return absolute indexes
    *
    * @param thisBlock First block
    * @param thatBlock Second block
    * @param block data
    * @return List of ( start, end ) pairs of different blocks - inclusive indexes
    */
  protected def getDifferenceWithinBlock( thisBlock:Array[Byte], thatBlock: Array[Byte], block: Int): List[(Int, Int)] = {
    val rightBoundary = Math.min( thisBlock.length, thatBlock.length )

    // contains indexes of current block start and end. Inclusive indexes
    var currentBlock:Option[(Int,Int)] = None
    var blocks:List[(Int,Int)] = List()

    for( i <- 0 until rightBoundary ){

      // data is not equal, add it to the block
      if( thisBlock(i) != thatBlock(i) ){
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

    currentBlock.
      fold( blocks ){ finalBlock => finalBlock :: blocks }. // last block
      map{ case ( start, end ) => ( start + block*blockSize, end + block*blockSize ) } // map to global indexes


  }

}
