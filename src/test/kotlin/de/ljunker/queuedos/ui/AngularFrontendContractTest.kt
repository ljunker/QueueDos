package de.ljunker.queuedos.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
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
        assertTrue(projectFile("frontend/src/app/shared/organisms/board-view.component.ts").isNotBlank())
        assertTrue(projectFile("frontend/src/app/pages/workspace/workspace-page.component.ts").isNotBlank())
    }

    @Test
    fun migratedFeatureContractsArePresent() {
        val workspace = projectFile("frontend/src/app/pages/workspace/workspace-page.component.ts")
        val admin = projectFile("frontend/src/app/shared/organisms/admin-view.component.ts")
        val board = projectFile("frontend/src/app/shared/organisms/board-view.component.ts")
        val ticketCard = projectFile("frontend/src/app/shared/molecules/ticket-card.component.ts")
        val detail = projectFile("frontend/src/app/shared/organisms/ticket-detail-view.component.ts")

        assertContains(workspace, "QueueActions.ticketDialogOpened")
        assertContains(workspace, "QueueActions.workflowSaveRequested")
        assertContains(admin, "Workflow")
        assertContains(ticketCard, "dragstart")
        assertContains(board, "ticketTransitioned")
        assertContains(detail, "commentSubmitted")
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
