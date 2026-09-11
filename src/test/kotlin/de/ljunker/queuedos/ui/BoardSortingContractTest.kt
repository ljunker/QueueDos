package de.ljunker.queuedos.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class BoardSortingContractTest {
    @Test
    fun projectBoardSupportsPriorityAndLabelSorting() {
        val board = File("frontend/src/app/shared/organisms/board-view.component.ts").readText()

        assertContains(board, "<option value=\"priority\">Priority</option>")
        assertContains(board, "<option value=\"label\">Label</option>")
        assertContains(board, "priorityRank(right.priority) - priorityRank(left.priority)")
        assertContains(board, "labelSortKey(left.labels)")
    }
}
