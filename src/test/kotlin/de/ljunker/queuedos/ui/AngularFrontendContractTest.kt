package de.ljunker.queuedos.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AngularFrontendContractTest {
    @Test
    fun angularStoreContractIsPresent() {
        val packageJson = projectFile("frontend/package.json")
        val main = projectFile("frontend/src/main.ts")
        val effects = projectFile("frontend/src/app/state/queue.effects.ts")
        val reducer = projectFile("frontend/src/app/state/queue.reducer.ts")

        assertContains(packageJson, "\"@ngrx/store\"")
        assertContains(packageJson, "\"@ngrx/effects\"")
        assertContains(main, "provideStore({ queue: queueReducer })")
        assertContains(main, "provideEffects([QueueEffects])")
        assertContains(effects, "ticketDialogSaved")
        assertContains(reducer, "workflowDraft")
    }

    @Test
    fun atomicDesignStructureIsPresent() {
        assertTrue(projectFile("frontend/src/app/shared/atoms/badge.component.ts").isNotBlank())
        assertTrue(projectFile("frontend/src/app/shared/molecules/ticket-card.component.ts").isNotBlank())
        assertTrue(projectFile("frontend/src/app/shared/molecules/ticket-form-fields.component.ts").isNotBlank())
        assertTrue(projectFile("frontend/src/app/shared/molecules/workflow-transition-editor.component.ts").isNotBlank())
        assertTrue(projectFile("frontend/src/app/shared/organisms/board-view.component.ts").isNotBlank())
        assertTrue(projectFile("frontend/src/app/shared/organisms/admin-projects-panel.component.ts").isNotBlank())
        assertTrue(projectFile("frontend/src/app/shared/organisms/workspace-tab-host.component.ts").isNotBlank())
        assertTrue(projectFile("frontend/src/app/pages/workspace/workspace-page.component.ts").isNotBlank())
    }

    @Test
    fun migratedFeatureContractsArePresent() {
        val workspace = projectFile("frontend/src/app/pages/workspace/workspace-page.component.ts")
        val workflowPanel = projectFile("frontend/src/app/shared/organisms/admin-workflow-panel.component.ts")
        val board = projectFile("frontend/src/app/shared/organisms/board-view.component.ts")
        val ticketCard = projectFile("frontend/src/app/shared/molecules/ticket-card.component.ts")
        val detail = projectFile("frontend/src/app/shared/organisms/ticket-detail-view.component.ts")
        val comments = projectFile("frontend/src/app/shared/organisms/ticket-comments-panel.component.ts")
        val effects = projectFile("frontend/src/app/state/queue.effects.ts")
        val transitionEffect =
            effects.substringAfter("readonly transitionTicket$").substringBefore("readonly deleteTicket$")

        assertContains(workspace, "QueueActions.ticketDialogOpened")
        assertContains(workspace, "QueueActions.workflowSaveRequested")
        assertContains(workflowPanel, "qd-workflow-status-editor")
        assertContains(workflowPanel, "qd-workflow-transition-editor")
        assertContains(ticketCard, "dragstart")
        assertContains(ticketCard, "handleClick")
        assertContains(ticketCard, "lastDragEndedAt")
        assertContains(board, "ticketTransitioned")
        assertContains(detail, "commentSubmitted")
        assertContains(comments, "commentSubmitted")
        assertContains(transitionEffect, "QueueActions.mutationSucceeded({})")
        assertFalse("focusTicketId" in transitionEffect)
    }

    @Test
    fun ktorServesAngularSpaAssets() {
        val routes = projectFile("src/main/kotlin/de/ljunker/queuedos/api/ApiRoutes.kt")

        assertContains(routes, "respondFrontendAsset(\"index.html\")")
        assertContains(routes, "get(\"/{assetPath...}\")")
        assertContains(routes, "static/index.html")
    }

    private fun projectFile(path: String): String =
        File(path).readText()
}
