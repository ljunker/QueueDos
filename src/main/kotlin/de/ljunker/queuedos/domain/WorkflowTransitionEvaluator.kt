package de.ljunker.queuedos.domain

import de.ljunker.queuedos.validation.validateRequiredFields

class WorkflowTransitionEvaluator {
    fun resolve(
        workflow: Workflow,
        ticket: Ticket,
        targetStatusId: String,
        actorRole: Role
    ): WorkflowTransitionResolution {
        if (workflow.statuses.none { it.id == targetStatusId }) {
            return WorkflowTransitionResolution.MissingStatus
        }
        if (ticket.statusId == targetStatusId) {
            return WorkflowTransitionResolution.Unchanged
        }
        val transition = workflow.transitions.firstOrNull {
            (it.globalTransition || it.fromStatusId == ticket.statusId) && it.toStatusId == targetStatusId
        } ?: return WorkflowTransitionResolution.NotAllowed
        if (actorRole !in transition.allowedRoles) {
            return WorkflowTransitionResolution.RoleDenied
        }
        if (isBackwardTransition(workflow, ticket.statusId, targetStatusId) && !transition.allowBackward) {
            return WorkflowTransitionResolution.BackwardDenied
        }
        validateRequiredFields(ticket, transition.requiredFields)
        return WorkflowTransitionResolution.Allowed
    }

    private fun isBackwardTransition(workflow: Workflow, fromStatusId: String, toStatusId: String): Boolean {
        val fromOrder = workflow.statuses.firstOrNull { it.id == fromStatusId }?.sortOrder ?: return false
        val toOrder = workflow.statuses.firstOrNull { it.id == toStatusId }?.sortOrder ?: return false
        return toOrder < fromOrder
    }
}

sealed interface WorkflowTransitionResolution {
    data object Allowed : WorkflowTransitionResolution
    data object Unchanged : WorkflowTransitionResolution
    data object MissingStatus : WorkflowTransitionResolution
    data object NotAllowed : WorkflowTransitionResolution
    data object RoleDenied : WorkflowTransitionResolution
    data object BackwardDenied : WorkflowTransitionResolution
}
