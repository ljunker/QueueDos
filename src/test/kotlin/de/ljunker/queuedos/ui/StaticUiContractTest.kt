package de.ljunker.queuedos.ui

import kotlin.test.Test
import kotlin.test.assertContains

class StaticUiContractTest {
    private val html = resource("static/index.html")
    private val script = resource("static/app.js")

    @Test
    fun loginUiContractIsPresent() {
        assertContains(html, "id=\"loginForm\"")
        assertContains(html, "id=\"loginEmail\"")
        assertContains(html, "id=\"loginPassword\"")
        assertContains(script, "#loginForm")
        assertContains(script, "/api/auth/login")
        assertContains(script, "localStorage.setItem(\"queuedosToken\"")
    }

    @Test
    fun ticketCreationUiContractIsPresent() {
        assertContains(html, "id=\"newTicketBtn\"")
        assertContains(html, "id=\"ticketDialog\"")
        assertContains(html, "id=\"ticketForm\"")
        assertContains(html, "id=\"ticketLabelsInput\"")
        assertContains(html, "id=\"ticketDueDateInput\"")
        assertContains(html, "id=\"ticketEstimateInput\"")
        assertContains(script, "openTicketDialog()")
        assertContains(script, "saveTicketFromDialog")
        assertContains(script, "await api(\"/api/tickets\"")
    }

    @Test
    fun dragAndDropUiContractIsPresent() {
        assertContains(script, "draggable=\"true\"")
        assertContains(script, "dragstart")
        assertContains(script, "dragover")
        assertContains(script, "drop")
        assertContains(script, "/transition")
        assertContains(script, "canTransition(ticket, statusId)")
    }

    @Test
    fun adminWorkflowUiContractIsPresent() {
        assertContains(html, "id=\"adminTab\"")
        assertContains(html, "id=\"addStatusBtn\"")
        assertContains(html, "id=\"addTransitionBtn\"")
        assertContains(html, "id=\"saveWorkflowBtn\"")
        assertContains(script, "renderWorkflowEditor")
        assertContains(script, "saveWorkflow")
        assertContains(script, "globalTransition")
        assertContains(script, "allowBackward")
        assertContains(script, "/api/projects/${'$'}{project.id}/workflow")
    }

    @Test
    fun ticketDetailAndUrlStateContractsArePresent() {
        assertContains(html, "id=\"detailTab\"")
        assertContains(html, "id=\"ticketDetail\"")
        assertContains(html, "id=\"labelFilter\"")
        assertContains(script, "openTicketDetail")
        assertContains(script, "renderTicketDetail")
        assertContains(script, "/api/tickets/${'$'}{ticket.id}/comments")
        assertContains(script, "new URLSearchParams(window.location.search)")
        assertContains(script, "window.history.replaceState")
    }

    private fun resource(path: String): String =
        requireNotNull(Thread.currentThread().contextClassLoader.getResource(path)) {
            "Missing test resource $path"
        }.readText()
}
