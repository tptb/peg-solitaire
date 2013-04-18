package com.googlecode.pegsolitaire

object StandardLongHashSet {
	def allocateTableMemory(size: Int) = new Array[Long](size)
}

/**
 * A memory-efficient hash set optimized for Longs
 * based on the java HashSet implementation by Google Inc.
 */
class StandardLongHashSet(t: Array[Long], s: Int) extends LongHashSet {

	protected var _size = s

	override def size = _size
	def table_size = table.size

	override def used = if (table == null) 1.0 else (_size.toDouble / table.length.toDouble)

	private var table = t
	private var table_length_minus_1 = if (t == null) 0 else (t.length - 1)

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

	def this(expectedSize: Int) {
		this(null, 0)
		require(expectedSize >= 0)
		ensureSizeFor(expectedSize)
	}

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
		if (table != null && table.length * 3 >= expectedSize * 4)
			return

		// calculate table size
		var newCapacity = if (table == null) 1 else table.length

		while (newCapacity * 3 < expectedSize * 4)
			newCapacity <<= 1

		val old_table = table
		val old_size = _size
		table = StandardLongHashSet.allocateTableMemory(newCapacity)
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
				loop((index + 1) % table.length)
		}

		loop(getIndex(o))
	}

	private def getIndex(value: Long): Int = {
		var h = (value ^ (value >>> 32)).asInstanceOf[Int] // hashCode
		// Copied from Apache's AbstractHashedMap; prevents power-of-two collisions.
		h += ~(h << 9)
		h ^= (h >>> 14)
		h += (h << 4)
		h ^= (h >>> 10)
		// Power of two trick.
		h & table_length_minus_1
	}

	override def depth: HashSetDepth = {
		var averageValue = BigDecimal(0)
		var maxValue = 0
		var oneAccessElements = 0

		var index = 0
		val table_length = table.length
		while (index < table_length) {
			val v = table(index)
			val designated_index = getIndex(v) // where the element should be

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
