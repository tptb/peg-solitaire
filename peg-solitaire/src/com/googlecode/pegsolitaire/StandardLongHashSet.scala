/**
 * Peg Solitaire
 * Copyright (C) 2010-2013 Bernd Amend <berndamend+pegsolitaire@googlemail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 3 as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.googlecode.pegsolitaire

object StandardLongHashSet {
	def allocateTableMemory(size: Int) = new Array[Long](size)
}

/**
 * A memory-efficient hash set optimized for Longs
 * based on the java HashSet implementation by Google Inc.
 */
class StandardLongHashSet(t: Array[Long], s: Int) extends LongHashSet {

	def table_size = if(table == null) 0 else table.size

	private var table = t
	table_length_minus_1 = if (table == null) 0 else (table.length - 1)

	/**
	 * positions can be used to create a HashSetIterator that only work on a subset of the HashSet
	 * e.g. to read multiple elements from a HashSet at a time without synchronization
	 */
	class Iterator(val groupID: Int = 0, val groupSize: Int = 1) extends HashSetIterator {
		require(groupID >= 0)
		require(groupSize > 0)
		require(groupID < groupSize)

		private var _index = groupID
		private val table_length = table.length

		advanceToItem()

		override def hasNext = _index < table_length

		/**
		 * call this function ONLY if you really know what you are doing
		 */
		override def unsafe_next: Long = {
			val toReturn = table(_index)
			_index += groupSize
			advanceToItem()
			toReturn
		}

		private def advanceToItem() {
			while (_index < table_length && (table(_index) == LongHashSet.INVALID_ELEMENT)) {
				_index += groupSize
			}
		}
	}

	def this() = this(StandardLongHashSet.allocateTableMemory(LongHashSet.INITIAL_TABLE_SIZE), 0)

	def this(expectedSize: Int) = this(
									StandardLongHashSet.allocateTableMemory(
											HashSet.computeCapacityForSize(expectedSize, LongHashSet.INITIAL_TABLE_SIZE)), 0)


	def this(c: LongHashSet) {
		this(c.size)
		this += c
	}

	override def +=(c: LongHashSet) {
		ensureSizeFor(_size + c.size)
		if(c.isInstanceOf[StandardLongHashSet])
			internal_addAll(c.asInstanceOf[StandardLongHashSet].table)
		else {
			c foreach( this += _)
		}
	}

	def +=(c: Array[Long]) {
		ensureSizeFor(_size + c.length)
		internal_addAll(c)
	}

	override def +=(o: Long) {
		require(o != LongHashSet.INVALID_ELEMENT)
		ensureSizeFor(_size + 1)
		internal_add(o)
	}

	// add the elements without checking if there is enough space
	private def internal_addAll(elements: Array[Long]) {
		val length = elements.length
		var i = 0
		while (i < length) {
			if (elements(i) != LongHashSet.INVALID_ELEMENT)
				internal_add(elements(i))
			i += 1
		}
	}

	private def internal_add(o: Long) {
		val index = findOrEmpty(o)
		if (table(index) == LongHashSet.INVALID_ELEMENT) {
			_size += 1
			table(index) = o
		}
	}

	override def clear() {
		table = StandardLongHashSet.allocateTableMemory(LongHashSet.INITIAL_TABLE_SIZE)
		table_length_minus_1 = table.length - 1
		_size = 0
	}

	override def clear(new_expected_size: Int) {
		_size = 0
		table = null
		ensureSizeFor(new_expected_size)
	}

	override def contains(o: Long) = table(findOrEmpty(o)) != LongHashSet.INVALID_ELEMENT

	override def iter: HashSetIterator = new Iterator
	override def iter(groupID: Int, groupSize: Int): HashSetIterator = new Iterator(groupID, groupSize)

	private def ensureSizeFor(expectedSize: Int) {
		if (HashSet.sizeFitsIntoCapacity(expectedSize, table.length))
			return

		val old_table = table
		val old_size = _size
		table = StandardLongHashSet.allocateTableMemory(HashSet.computeCapacityForSize(expectedSize, table.size))
		table_length_minus_1 = table.length - 1
		_size = 0
		if (old_size != 0)
			internal_addAll(old_table)
		require(_size == old_size)
	}

	/**
	 * Returns the index in the table at which a particular item resides, or -1 if
	 * the item is not in the table.
	 */
	private def find(o: Long): Int = {
		val index = findOrEmpty(o)
		if (table(index) == LongHashSet.INVALID_ELEMENT)
			-1
		else
			index
	}

	/**
	 * Returns the index in the table at which a particular item resides, or the
	 * index of an empty slot in the table where this item should be inserted if
	 * it is not already in the table.
	 * @return index
	 */
	private def findOrEmpty(o: Long): Int = {
		@scala.annotation.tailrec
		def loop(index: Int): Int = {
			val existing = table(index)
			if (existing == LongHashSet.INVALID_ELEMENT || o == existing)
				index
			else
				loop((index + 1) & table_length_minus_1)
		}

		loop(getIndexFromLong(o))
	}

	private def getIndexFromLong(value: Long): Int = getIndex((value ^ (value >>> 32)).asInstanceOf[Int])

	override def depth: HashSetDepthTrait = {
		if(size == 0) {
			HashSetDepth(0,0,0)
		} else {
			var averageValue = BigDecimal(0)
			var maxValue = 0
			var oneAccessElements = 0

			var index = 0
			val table_length = table.length
			while (index < table_length) {
				val v = table(index)
				val designated_index = getIndexFromLong(v) // where the element should be

				if (v != LongHashSet.INVALID_ELEMENT) {
					var d = 1
					if (index == designated_index)
						oneAccessElements += 1
					else if (designated_index < index) {
						d += index - designated_index
					} else {
						d += index + (table_length - designated_index)
					}

					maxValue = math.max(maxValue, d)
					averageValue += d
				}
				index += 1
			}

			HashSetDepth((averageValue / size).toDouble, maxValue, oneAccessElements.toDouble / size.toDouble)
		}
	}
}
