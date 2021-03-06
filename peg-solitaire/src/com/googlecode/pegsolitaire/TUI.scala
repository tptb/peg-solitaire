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

import Helper._

class ConsolenStatusObserver extends StatusObserver {
	def begin_forward_calculation() = println("Calculate possible fields")

	def end_forward_calculation(required_time: Long) = println("calculation took " + Helper.millisecToString(required_time))

	def begin_backward_cleaning() = println("Delete fields that doesn't result in the requested solution")
	def end_backward_cleaning(required_time: Long) = println("delete took " + Helper.millisecToString(required_time))

	def begin_forward_calculation_step(removed_pegs: Int) = printColoredText("search fields with " + removed_pegs + " removed pegs", Color.green)
	def end_forward_calculation_step(removed_pegs: Int, solution: LongHashSet) {
		printColoredText(", found " + solution.size + " fields", Color.green)
		if (Helper.enableDebug)
			printlnlnDebug(" " + solution.depth)
		else
			println()
	}

	def begin_backward_cleaning_step(removed_pegs: Int) = printColoredText("clean field list with " + removed_pegs + " removed pegs", Color.green)
	def end_backward_cleaning_step(removed_pegs: Int, deadends: Long) = {
		printColoredText(", found " + deadends + " dead ends\n", Color.green)
	}

	def dead_ends(count: Long) = printlnColoredText("There were " + count + " dead ends", Color.blue)
}

/**
 * TODO:
 *  rewrite argument parser
 *  allow the user to search a solution for a given field
 */
object TUI {

	def main(args: Array[String]) {
		System.nanoTime()
		println("Peg Solitaire v7\n" +
			"  Copyright (C) 2010-2013 Bernd Amend <berndamend+pegsolitaire@googlemail.com>\n" +
			"  This program is free software: you can redistribute it and/or modify\n" +
			"  it under the terms of the GNU General Public License version 3 as published by\n" +
			"  the Free Software Foundation. This program comes with ABSOLUTELY NO WARRANTY\n")

		println("usage [user|english|15holes|euro] [-full] [-count] [additional options]\n" +
			"  Available Boards:\n" +
			"    user: create your own board!\n" +
			"    english: standard english (default)\n" +
			"    15holes: simple test board\n" +
			"    euro: standard european\n\n" +
			"  -full                calculate all solutions for all possible start fields,\n" +
			"                        by default you have to select the start and end field(s)\n" +
			"  -count               count the number of ways to a solution (this may take a while!!!)\n" +
			"  -color               enable colored text output\n" +
			"  -threads             number of threads that should be used (default 0 = auto)\n" +
			"  -debug               enable debug output\n\n" +
			"  To reduce memory usage try \"-threads 1\"")

		val observer = new ConsolenStatusObserver

		/*
		 * parse command line arguments
		 */
		var arg_full = false
		var arg_count = false

		var selectedGame = Boards.English
		var thread_count = 0

		var i = 0
		while (i < args.length) {
			/// exit program if argument count is insufficient
			def checkForArguments(name: String, num: Int = 1): Int = {
				if (i + num == args.length) {
					printlnError("error: " + name + " requires an additional parameter")
					sys.exit(-1)
				}
				num
			}

			args(i) match {
				case "-full" => arg_full = true
				case "-count" => arg_count = true
				case "-color" => Helper.enableColor = true
				case "-debug" => Helper.enableDebug = true
				case "-threads" =>
					i += checkForArguments("-threads")
					try {
						thread_count = args(i).toInt
						if (thread_count < 0) {
							printlnError("error: negative arguments for -threads are not allowed, exit")
							return
						}
					} catch {
						case _ : NumberFormatException =>
							printlnError("error: invalid argument for -threads, exit")
							return
					}
				case s =>
					try {
						selectedGame = Boards.withName(args(i))
					} catch {
						case _ : NoSuchElementException =>
							printlnError("error: unknown parameter " + s + " exit")
							return
					}
			}
			i += 1
		}
		thread_count = if (thread_count == 0) Runtime.getRuntime.availableProcessors else thread_count

		println("Use " + thread_count + " threads")

		var solitaire: Solver = null

		val solitaireType = selectedGame match {
			case Boards.English => Boards.EnglishBoard
			case Boards.European => Boards.EuropeanBoard
			case Boards.Holes15 => Boards.Holes15Board
			case Boards.User =>
				println("Examples:")
				println("15 holes board:\n\no . . . .\no o . . .\no o o . .\no o o o .\no o o o o\n")
				println("Simple board:\n\n. o o o o .\no o o o o o\no o . . o o\no o . . o o\no o o o o o\n. o o o o .\n")
				println("Check board (all move directions have to be allowed):\n\n. . . . . . . o o\n. . . . . . o o o\no o . . . o o o .\no o o . o o o . .\n. o o o o o . . .\n. . o o o . . . .\n. . . o . . . . .\n")
				println("Please create a board or copy one from above (max 63 holes).\nPress enter 2x to process your input (o = hole, . = blocked):\n")
				val field = readField
				var moveDirection = List[MoveDirections.Value]()
				MoveDirections.values.foreach {
					m =>
						if (readYesOrNo("Are " + m + " moves allowed? (y/n)"))
							moveDirection ::= m
				}

				if (moveDirection.isEmpty) {
					printlnError("error: no move directions selected, exit")
					return
				}

				var sol: Board = null
				try {
					sol = new Board(field, moveDirection.toArray[MoveDirections.Value])
				} catch {
					case _ : Exception =>
						printlnError("error: the entered field is invalid, exit")
						return
				}
				println("Press enter to start solving. This may take a while.")
				readLine
				sol
		}

		if (Helper.enableDebug) {
			println(solitaireType.debug_output)
		}

		var selection: Iterable[Long] = null
		var end_pegs = 0
		var end_field: Long = LongHashSet.INVALID_ELEMENT

		if (arg_full) {
			selection = solitaireType.possibleStartFields.toList
		} else {
			println("Select a start field or e to enter your own start field:")
			selection = List(selectField(solitaireType, solitaireType.getCompleteList(solitaireType.possibleStartFields).toList, true))
			if (readYesOrNo("Do you want to enter an end field (y/n) ?"))
					end_field = readStateField(solitaireType, 0)
			else {
				println("How many pegs should be left on the field (0 = play as far as possible, -1 = don't clean):")
				end_pegs = readNumber(-1 until java.lang.Long.bitCount(selection.head)-1)
			}
		}

		try {
			Time("Solve") {
				solitaire = new Solver(solitaireType, selection, end_pegs, end_field, observer, thread_count)
			}
		} catch {
			case e: Exception =>
				printlnError("error: " + e.getMessage())
				return
		}

		try {
			solitaire.getStart
		} catch {
			case _ : Throwable =>
				printlnError("error: there is no solution, sorry")
				return
		}

		println("\nPossible fields:")
		var count = 0
		for (i <- 0 until solitaire.game.length) {
			if (solitaire.solution(i) != null) {
				val num = solitaire.solution(i).size
				println("  - removed pegs = " + i + "  possible fields = " + num)
				count += num
				Unit
			}
		}
		printlnColoredText("There are " + count + " possible fields.", Color.blue)

		if (arg_count) {
			println("\nCount how many ways are available to solve the board (this may take a while)")
			printlnColoredText("There are " + solitaire.countPossibleGames + " ways to a solution.", Color.blue)
		}

		println("Possible solutions:")
		printFields(solitaire.game, solitaire.getEnd.toList)

		solutionBrowser(solitaire)

		println("Bye, bye")
	}

	def printFields(game: Board, choices: List[Long]) {
		val sb = new StringBuilder
		var tmp = ""
		for (i <- 0 until choices.length) {
			tmp = Helper.mixStrings(tmp, game.toString(choices(i)), seperator = "     ", str2label = i.toString)

			if ((i + 1) % 4 == 0) {
				sb append tmp + "\n"
				tmp = ""
			}
		}

		if (!tmp.isEmpty)
			sb append tmp + "\n"

		println(sb.toString)
	}

	def readField(): String = {
		val sb = new StringBuilder
		var current = ""
		var done = false
		do {
			current = Console.readLine
			done = current.isEmpty
			if (!done) {
				sb append current
				sb append '\n'
			}
		} while (!done)

		sb.toString
	}

	def readStateField(game: Board, template: Long): Long = {
		println("Please enter a field (x = peg, . = empty)\nPress enter 2x to process your input.")
		println(" template:")
		println(game.toString(template))
		println()
		game.fromString(readField)
	}

	def readNumber(range: Range): Int = {
		var num = range.start-1
		while (num < range.start) {
			print("(x to abort) > ")
			Console.flush
			val input = readLine()
			if (input == null || input.toLowerCase == "x") {
				println("Bye, bye")
				sys.exit(0)
			}
			try {
				num = input.toInt
				if (num < range.start || num >= range.end)
					printlnError("error: invalid selection, please try again")
			} catch {
				case _ : NumberFormatException =>
					printlnError("error: invalid input, please try again")
					num = -1
			}
		}
		num
	}

	/**
	 * Simple console based game-field selection
	 *
	 * @return selected game-field
	 */
	def selectField(game: Board, choices: List[Long], enter_field_allowed: Boolean): Long = {
		printFields(game, choices)

		var selection = -1
		while (selection < 0 || selection >= choices.length) {
			print("(x to abort) > ")
			Console.flush
			val input = readLine()
			if (input == null || input.toLowerCase == "x") {
				println("Bye, bye")
				sys.exit(0)
			}
			try {
				if(input.toLowerCase == "e") {
					if(enter_field_allowed) {
						return readStateField(game, Long.MaxValue)
					} else
						printlnError("e is invalid in this mode")
				} else {
					selection = input.toInt
					if (selection < 0 || selection >= choices.length)
						printlnError("error: invalid selection, please try again")
				}
			} catch {
				case _ : Exception =>
					printlnError("error: invalid input, please try again")
					selection = -1
			}
		}

		choices(selection)
	}

	def solutionBrowser(solitaire: Solver) {
		while (true) {
			println("\nSolution Browser: (x = peg, . = empty)")
			var s = solitaire.getStart.toList
			var f = s(0)
			while (s.length != 0) {
				println("Please choose a move: ")
				f = selectField(solitaire.game, s, false)
				println()
				println("Current field " + (solitaire.game.length - java.lang.Long.bitCount(f)) + "")
				println(solitaire.game.toString(f))
				println()
				s = solitaire.getFollower(f).toList
			}
			println("Game is finished, press enter to restart or 'x' to exit")
			readLine match {
				case "x" => return
				case _ =>
			}
		}
	}
}

