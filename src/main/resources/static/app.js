const $ = (selector) => document.querySelector(selector);

const state = {
  token: localStorage.getItem("queuedosToken"),
  data: null,
  selectedProjectId: null,
  activeTab: "board",
  workflowDraft: null,
  draggedTicketId: null,
};

const priorityLabels = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
  CRITICAL: "Critical",
};

document.addEventListener("DOMContentLoaded", () => {
  bindStaticEvents();
  if (state.token) {
    loadBootstrap().catch(() => showLogin());
  } else {
    showLogin();
  }
});

function bindStaticEvents() {
  $("#loginForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    $("#loginError").textContent = "";
    try {
      const response = await api("/api/auth/login", {
        method: "POST",
        body: {
          email: $("#loginEmail").value,
          password: $("#loginPassword").value,
        },
        public: true,
      });
      state.token = response.token;
      localStorage.setItem("queuedosToken", state.token);
      await loadBootstrap();
    } catch (error) {
      $("#loginError").textContent = error.message;
    }
  });

  $("#logoutBtn").addEventListener("click", () => {
    localStorage.removeItem("queuedosToken");
    state.token = null;
    state.data = null;
    showLogin();
  });

  $("#projectSelect").addEventListener("change", () => {
    state.selectedProjectId = $("#projectSelect").value;
    resetWorkflowDraft();
    render();
  });

  document.querySelectorAll(".tab").forEach((tab) => {
    tab.addEventListener("click", () => {
      state.activeTab = tab.dataset.tab;
      render();
    });
  });

  $("#newTicketBtn").addEventListener("click", () => openTicketDialog());
  $("#closeTicketDialog").addEventListener("click", () => $("#ticketDialog").close());
  $("#cancelTicketBtn").addEventListener("click", () => $("#ticketDialog").close());
  $("#ticketForm").addEventListener("submit", saveTicketFromDialog);
  $("#deleteTicketBtn").addEventListener("click", deleteTicketFromDialog);

  ["searchInput", "statusFilter", "typeFilter", "priorityFilter", "assigneeFilter", "sortSelect"].forEach((id) => {
    $(`#${id}`).addEventListener("input", renderList);
  });

  $("#projectForm").addEventListener("submit", createProject);
  $("#userForm").addEventListener("submit", createUser);
  $("#typeForm").addEventListener("submit", createTicketType);
  $("#addStatusBtn").addEventListener("click", addDraftStatus);
  $("#addTransitionBtn").addEventListener("click", addDraftTransition);
  $("#saveWorkflowBtn").addEventListener("click", saveWorkflow);
}

async function api(path, options = {}) {
  const headers = { Accept: "application/json" };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (!options.public && state.token) {
    headers.Authorization = `Bearer ${state.token}`;
  }

  const response = await fetch(path, {
    method: options.method || "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get("content-type") || "";
  const payload = contentType.includes("application/json") ? await response.json() : await response.text();

  if (!response.ok) {
    const message = typeof payload === "string" ? payload : payload.message;
    throw new Error(message || "Request failed.");
  }

  return payload;
}

async function loadBootstrap() {
  const previousProjectId = state.selectedProjectId;
  state.data = await api("/api/bootstrap");
  const projects = state.data.projects.filter((project) => !project.archived);
  state.selectedProjectId = projects.some((project) => project.id === previousProjectId)
    ? previousProjectId
    : projects[0]?.id || state.data.projects[0]?.id || null;
  resetWorkflowDraft();
  showApp();
  render();
}

function showLogin() {
  $("#loginView").classList.remove("hidden");
  $("#appView").classList.add("hidden");
}

function showApp() {
  $("#loginView").classList.add("hidden");
  $("#appView").classList.remove("hidden");
}

function render() {
  if (!state.data) return;
  const project = selectedProject();
  const user = state.data.currentUser;

  $("#orgName").textContent = state.data.organizations[0]?.name || "Organization";
  $("#currentUser").textContent = `${user.displayName} (${roleLabel(user.role)})`;
  $("#projectKey").textContent = project?.key || "";
  $("#projectName").textContent = project?.name || "No project";
  $("#newTicketBtn").disabled = !project;

  renderProjectSelect();
  document.querySelectorAll(".admin-only").forEach((node) => {
    node.classList.toggle("hidden", user.role !== "ADMIN");
  });
  if (state.activeTab === "admin" && user.role !== "ADMIN") {
    state.activeTab = "board";
  }

  document.querySelectorAll(".tab").forEach((tab) => {
    tab.classList.toggle("active", tab.dataset.tab === state.activeTab);
  });
  $("#boardTab").classList.toggle("hidden", state.activeTab !== "board");
  $("#listTab").classList.toggle("hidden", state.activeTab !== "list");
  $("#adminTab").classList.toggle("hidden", state.activeTab !== "admin");

  renderBoard();
  renderFilterOptions();
  renderList();
  if (user.role === "ADMIN") renderAdmin();
}

function renderProjectSelect() {
  const select = $("#projectSelect");
  select.innerHTML = state.data.projects
    .map((project) => `<option value="${escapeHtml(project.id)}">${escapeHtml(project.key)} - ${escapeHtml(project.name)}</option>`)
    .join("");
  select.value = state.selectedProjectId || "";
}

function renderBoard() {
  const board = $("#board");
  const workflow = selectedWorkflow();
  if (!workflow) {
    board.innerHTML = `<p class="muted">No workflow configured.</p>`;
    return;
  }

  const ticketsByStatus = projectTickets().reduce((acc, ticket) => {
    acc[ticket.statusId] = acc[ticket.statusId] || [];
    acc[ticket.statusId].push(ticket);
    return acc;
  }, {});

  board.innerHTML = sortedStatuses(workflow).map((status) => {
    const tickets = (ticketsByStatus[status.id] || []).sort((a, b) => a.number - b.number);
    return `
      <section class="column" data-status-id="${escapeHtml(status.id)}">
        <header class="column-header">
          <span>${escapeHtml(status.name)}</span>
          <span class="badge">${tickets.length}</span>
        </header>
        <div class="column-body">
          ${tickets.map(renderTicketCard).join("")}
        </div>
      </section>
    `;
  }).join("");

  document.querySelectorAll(".ticket-card").forEach((card) => {
    card.addEventListener("dragstart", () => {
      state.draggedTicketId = card.dataset.ticketId;
    });
    card.addEventListener("click", () => openTicketDialog(ticketById(card.dataset.ticketId)));
  });

  document.querySelectorAll(".column").forEach((column) => {
    column.addEventListener("dragover", (event) => {
      event.preventDefault();
      column.classList.add("drag-over");
    });
    column.addEventListener("dragleave", () => column.classList.remove("drag-over"));
    column.addEventListener("drop", async (event) => {
      event.preventDefault();
      column.classList.remove("drag-over");
      const ticket = ticketById(state.draggedTicketId);
      if (!ticket) return;
      const statusId = column.dataset.statusId;
      if (ticket.statusId === statusId) return;
      if (!canTransition(ticket, statusId)) {
        showToast("This workflow transition is not allowed.");
        return;
      }
      try {
        await api(`/api/tickets/${ticket.id}/transition`, {
          method: "POST",
          body: { toStatusId: statusId },
        });
        await loadBootstrap();
      } catch (error) {
        showToast(error.message);
      }
    });
  });
}

function renderTicketCard(ticket) {
  const type = typeById(ticket.typeId);
  const assignee = userById(ticket.assigneeId);
  return `
    <article class="ticket-card" draggable="true" data-ticket-id="${escapeHtml(ticket.id)}">
      <div class="ticket-meta">
        <span class="badge">${escapeHtml(ticket.key)}</span>
        <span class="badge priority-${ticket.priority.toLowerCase()}">${priorityLabels[ticket.priority]}</span>
      </div>
      <strong>${escapeHtml(ticket.title)}</strong>
      <div class="badges">
        <span class="badge"><span class="type-dot" style="background:${escapeHtml(type?.color || "#667085")}"></span>${escapeHtml(type?.name || "Type")}</span>
      </div>
      <div class="card-footer">
        <span class="muted">${escapeHtml(assignee?.displayName || "Unassigned")}</span>
      </div>
    </article>
  `;
}

function renderFilterOptions() {
  const workflow = selectedWorkflow();
  const currentStatus = $("#statusFilter").value;
  const currentType = $("#typeFilter").value;
  const currentPriority = $("#priorityFilter").value;
  const currentAssignee = $("#assigneeFilter").value;

  $("#statusFilter").innerHTML = `<option value="">All statuses</option>` +
    (workflow ? sortedStatuses(workflow).map((status) => `<option value="${escapeHtml(status.id)}">${escapeHtml(status.name)}</option>`).join("") : "");
  $("#typeFilter").innerHTML = `<option value="">All types</option>` +
    projectTypes().map((type) => `<option value="${escapeHtml(type.id)}">${escapeHtml(type.name)}</option>`).join("");
  $("#priorityFilter").innerHTML = `<option value="">All priorities</option>` +
    state.data.priorities.map((priority) => `<option value="${priority}">${priorityLabels[priority]}</option>`).join("");
  $("#assigneeFilter").innerHTML = `<option value="">All assignees</option><option value="unassigned">Unassigned</option>` +
    activeUsers().map((user) => `<option value="${escapeHtml(user.id)}">${escapeHtml(user.displayName)}</option>`).join("");

  $("#statusFilter").value = optionExists("#statusFilter", currentStatus) ? currentStatus : "";
  $("#typeFilter").value = optionExists("#typeFilter", currentType) ? currentType : "";
  $("#priorityFilter").value = optionExists("#priorityFilter", currentPriority) ? currentPriority : "";
  $("#assigneeFilter").value = optionExists("#assigneeFilter", currentAssignee) ? currentAssignee : "";
}

function renderList() {
  const query = $("#searchInput").value.trim().toLowerCase();
  const statusId = $("#statusFilter").value;
  const typeId = $("#typeFilter").value;
  const priority = $("#priorityFilter").value;
  const assigneeId = $("#assigneeFilter").value;
  const sort = $("#sortSelect").value;

  let tickets = projectTickets().filter((ticket) => {
    const searchable = `${ticket.key} ${ticket.title} ${ticket.description}`.toLowerCase();
    return (!query || searchable.includes(query)) &&
      (!statusId || ticket.statusId === statusId) &&
      (!typeId || ticket.typeId === typeId) &&
      (!priority || ticket.priority === priority) &&
      (!assigneeId || (assigneeId === "unassigned" ? !ticket.assigneeId : ticket.assigneeId === assigneeId));
  });

  tickets = sortTickets(tickets, sort);

  $("#ticketRows").innerHTML = tickets.map((ticket) => {
    const type = typeById(ticket.typeId);
    const status = statusById(ticket.statusId);
    const assignee = userById(ticket.assigneeId);
    return `
      <tr data-ticket-id="${escapeHtml(ticket.id)}">
        <td><strong>${escapeHtml(ticket.key)}</strong></td>
        <td>${escapeHtml(ticket.title)}</td>
        <td>${escapeHtml(type?.name || "")}</td>
        <td><span class="badge priority-${ticket.priority.toLowerCase()}">${priorityLabels[ticket.priority]}</span></td>
        <td>${escapeHtml(status?.name || "")}</td>
        <td>${escapeHtml(assignee?.displayName || "Unassigned")}</td>
      </tr>
    `;
  }).join("");

  document.querySelectorAll("tr[data-ticket-id]").forEach((row) => {
    row.addEventListener("click", () => openTicketDialog(ticketById(row.dataset.ticketId)));
  });
}

function renderAdmin() {
  renderProjectList();
  renderUserList();
  renderTypeList();
  renderWorkflowEditor();
}

function renderProjectList() {
  $("#projectList").innerHTML = state.data.projects.map((project) => {
    const count = state.data.tickets.filter((ticket) => ticket.projectId === project.id).length;
    return `
      <div class="admin-item">
        <div>
          <strong>${escapeHtml(project.key)} - ${escapeHtml(project.name)}</strong>
          <small>${count} tickets${project.archived ? " · archived" : ""}</small>
        </div>
        <button data-select-project="${escapeHtml(project.id)}">Open</button>
      </div>
    `;
  }).join("");
  document.querySelectorAll("[data-select-project]").forEach((button) => {
    button.addEventListener("click", () => {
      state.selectedProjectId = button.dataset.selectProject;
      resetWorkflowDraft();
      render();
    });
  });
}

function renderUserList() {
  $("#userList").innerHTML = state.data.users.map((user) => `
    <div class="admin-item">
      <div>
        <strong>${escapeHtml(user.displayName)}</strong>
        <small>${escapeHtml(user.email)} · ${roleLabel(user.role)} · ${user.active ? "active" : "inactive"}</small>
      </div>
      <button data-toggle-user="${escapeHtml(user.id)}">${user.active ? "Disable" : "Enable"}</button>
    </div>
  `).join("");

  document.querySelectorAll("[data-toggle-user]").forEach((button) => {
    button.addEventListener("click", async () => {
      const user = userById(button.dataset.toggleUser);
      try {
        await api(`/api/users/${user.id}`, {
          method: "PUT",
          body: { active: !user.active },
        });
        await loadBootstrap();
      } catch (error) {
        showToast(error.message);
      }
    });
  });
}

function renderTypeList() {
  $("#typeList").innerHTML = projectTypes().map((type) => `
    <div class="admin-item">
      <div>
        <strong><span class="type-dot" style="display:inline-block;background:${escapeHtml(type.color)}"></span> ${escapeHtml(type.name)}</strong>
        <small>${escapeHtml(type.description || "No description")}</small>
      </div>
      <button data-delete-type="${escapeHtml(type.id)}">Delete</button>
    </div>
  `).join("");

  document.querySelectorAll("[data-delete-type]").forEach((button) => {
    button.addEventListener("click", async () => {
      try {
        await api(`/api/ticket-types/${button.dataset.deleteType}`, { method: "DELETE" });
        await loadBootstrap();
      } catch (error) {
        showToast(error.message);
      }
    });
  });
}

function renderWorkflowEditor() {
  if (!state.workflowDraft) resetWorkflowDraft();
  const workflow = state.workflowDraft;
  if (!workflow) {
    $("#statusEditor").innerHTML = "";
    $("#transitionEditor").innerHTML = "";
    return;
  }

  $("#statusEditor").innerHTML = workflow.statuses.map((status, index) => `
    <div class="editor-row status-row" data-status-index="${index}">
      <input value="${escapeAttribute(status.name)}" data-status-field="name" aria-label="Status name">
      <select data-status-field="category" aria-label="Status category">
        <option value="TODO" ${status.category === "TODO" ? "selected" : ""}>Todo</option>
        <option value="IN_PROGRESS" ${status.category === "IN_PROGRESS" ? "selected" : ""}>In progress</option>
        <option value="DONE" ${status.category === "DONE" ? "selected" : ""}>Done</option>
      </select>
      <button data-remove-status="${index}" type="button">Remove</button>
    </div>
  `).join("");

  document.querySelectorAll("[data-status-field]").forEach((input) => {
    input.addEventListener("input", () => {
      const row = input.closest("[data-status-index]");
      const status = workflow.statuses[Number(row.dataset.statusIndex)];
      status[input.dataset.statusField] = input.value;
    });
  });
  document.querySelectorAll("[data-remove-status]").forEach((button) => {
    button.addEventListener("click", () => {
      const index = Number(button.dataset.removeStatus);
      const removed = workflow.statuses[index];
      workflow.statuses.splice(index, 1);
      workflow.transitions = workflow.transitions.filter((transition) => {
        return transition.fromStatusId !== removed.id && transition.toStatusId !== removed.id;
      });
      renderWorkflowEditor();
    });
  });

  $("#transitionEditor").innerHTML = workflow.transitions.map((transition, index) => `
    <div class="editor-row transition-row" data-transition-index="${index}">
      ${statusSelect("fromStatusId", transition.fromStatusId, workflow.statuses)}
      ${statusSelect("toStatusId", transition.toStatusId, workflow.statuses)}
      ${roleSelect(transition.allowedRoles)}
      <input value="${escapeAttribute(transition.requiredFields.join(", "))}" data-transition-field="requiredFields" placeholder="required fields">
      <button data-remove-transition="${index}" type="button">Remove</button>
    </div>
  `).join("");

  document.querySelectorAll("[data-transition-field]").forEach((input) => {
    input.addEventListener("input", () => {
      const row = input.closest("[data-transition-index]");
      const transition = workflow.transitions[Number(row.dataset.transitionIndex)];
      if (input.dataset.transitionField === "requiredFields") {
        transition.requiredFields = input.value.split(",").map((value) => value.trim()).filter(Boolean);
      } else if (input.dataset.transitionField === "roles") {
        transition.allowedRoles = input.value === "BOTH" ? ["ADMIN", "MEMBER"] : [input.value];
      } else {
        transition[input.dataset.transitionField] = input.value;
      }
    });
  });
  document.querySelectorAll("[data-remove-transition]").forEach((button) => {
    button.addEventListener("click", () => {
      workflow.transitions.splice(Number(button.dataset.removeTransition), 1);
      renderWorkflowEditor();
    });
  });
}

function statusSelect(field, selected, statuses) {
  return `
    <select data-transition-field="${field}" aria-label="${field}">
      ${statuses.map((status) => `<option value="${escapeHtml(status.id)}" ${selected === status.id ? "selected" : ""}>${escapeHtml(status.name)}</option>`).join("")}
    </select>
  `;
}

function roleSelect(roles) {
  const value = roles.includes("ADMIN") && roles.includes("MEMBER") ? "BOTH" : roles[0] || "MEMBER";
  return `
    <select data-transition-field="roles" aria-label="Allowed roles">
      <option value="BOTH" ${value === "BOTH" ? "selected" : ""}>Admin + Member</option>
      <option value="ADMIN" ${value === "ADMIN" ? "selected" : ""}>Admin</option>
      <option value="MEMBER" ${value === "MEMBER" ? "selected" : ""}>Member</option>
    </select>
  `;
}

async function createProject(event) {
  event.preventDefault();
  try {
    const project = await api("/api/projects", {
      method: "POST",
      body: {
        key: $("#projectKeyInput").value,
        name: $("#projectNameInput").value,
      },
    });
    $("#projectForm").reset();
    state.selectedProjectId = project.id;
    await loadBootstrap();
  } catch (error) {
    showToast(error.message);
  }
}

async function createUser(event) {
  event.preventDefault();
  try {
    await api("/api/users", {
      method: "POST",
      body: {
        email: $("#userEmailInput").value,
        displayName: $("#userNameInput").value,
        role: $("#userRoleInput").value,
        password: $("#userPasswordInput").value,
      },
    });
    $("#userForm").reset();
    await loadBootstrap();
  } catch (error) {
    showToast(error.message);
  }
}

async function createTicketType(event) {
  event.preventDefault();
  const project = selectedProject();
  if (!project) return;
  try {
    await api("/api/ticket-types", {
      method: "POST",
      body: {
        projectId: project.id,
        name: $("#typeNameInput").value,
        color: $("#typeColorInput").value,
      },
    });
    $("#typeForm").reset();
    $("#typeColorInput").value = "#2563eb";
    await loadBootstrap();
  } catch (error) {
    showToast(error.message);
  }
}

function addDraftStatus() {
  if (!state.workflowDraft) return;
  state.workflowDraft.statuses.push({
    id: newId("status"),
    name: "New status",
    category: "TODO",
    sortOrder: state.workflowDraft.statuses.length,
  });
  renderWorkflowEditor();
}

function addDraftTransition() {
  if (!state.workflowDraft || state.workflowDraft.statuses.length < 2) return;
  const statuses = state.workflowDraft.statuses;
  state.workflowDraft.transitions.push({
    id: newId("transition"),
    fromStatusId: statuses[0].id,
    toStatusId: statuses[1].id,
    allowedRoles: ["ADMIN", "MEMBER"],
    requiredFields: [],
  });
  renderWorkflowEditor();
}

async function saveWorkflow() {
  const project = selectedProject();
  if (!project || !state.workflowDraft) return;
  try {
    await api(`/api/projects/${project.id}/workflow`, {
      method: "PUT",
      body: {
        statuses: state.workflowDraft.statuses,
        transitions: state.workflowDraft.transitions,
      },
    });
    await loadBootstrap();
    showToast("Workflow saved.");
  } catch (error) {
    showToast(error.message);
  }
}

function openTicketDialog(ticket = null) {
  const project = selectedProject();
  const workflow = selectedWorkflow();
  if (!project || !workflow) return;

  $("#ticketFormError").textContent = "";
  $("#ticketIdInput").value = ticket?.id || "";
  $("#ticketTitleInput").value = ticket?.title || "";
  $("#ticketDescriptionInput").value = ticket?.description || "";
  $("#ticketDialogTitle").textContent = ticket ? `${ticket.key}` : "New ticket";

  $("#ticketTypeInput").innerHTML = projectTypes().map((type) => {
    return `<option value="${escapeHtml(type.id)}">${escapeHtml(type.name)}</option>`;
  }).join("");
  $("#ticketPriorityInput").innerHTML = state.data.priorities.map((priority) => {
    return `<option value="${priority}">${priorityLabels[priority]}</option>`;
  }).join("");
  $("#ticketAssigneeInput").innerHTML = `<option value="">Unassigned</option>` +
    activeUsers().map((user) => `<option value="${escapeHtml(user.id)}">${escapeHtml(user.displayName)}</option>`).join("");
  $("#ticketStatusInput").innerHTML = sortedStatuses(workflow).map((status) => {
    return `<option value="${escapeHtml(status.id)}">${escapeHtml(status.name)}</option>`;
  }).join("");

  $("#ticketTypeInput").value = ticket?.typeId || projectTypes()[0]?.id || "";
  $("#ticketPriorityInput").value = ticket?.priority || "MEDIUM";
  $("#ticketAssigneeInput").value = ticket?.assigneeId || "";
  $("#ticketStatusInput").value = ticket?.statusId || workflow.statuses[0]?.id || "";
  $("#deleteTicketBtn").classList.toggle("hidden", !ticket || state.data.currentUser.role !== "ADMIN");

  $("#ticketDialog").showModal();
}

async function saveTicketFromDialog(event) {
  event.preventDefault();
  const project = selectedProject();
  const ticketId = $("#ticketIdInput").value;
  const body = {
    title: $("#ticketTitleInput").value,
    description: $("#ticketDescriptionInput").value,
    typeId: $("#ticketTypeInput").value,
    priority: $("#ticketPriorityInput").value,
    assigneeId: $("#ticketAssigneeInput").value || null,
  };

  try {
    if (ticketId) {
      const current = ticketById(ticketId);
      await api(`/api/tickets/${ticketId}`, { method: "PUT", body });
      const nextStatus = $("#ticketStatusInput").value;
      if (current.statusId !== nextStatus) {
        await api(`/api/tickets/${ticketId}/transition`, {
          method: "POST",
          body: { toStatusId: nextStatus },
        });
      }
    } else {
      await api("/api/tickets", {
        method: "POST",
        body: {
          ...body,
          projectId: project.id,
          statusId: $("#ticketStatusInput").value,
        },
      });
    }
    $("#ticketDialog").close();
    await loadBootstrap();
  } catch (error) {
    $("#ticketFormError").textContent = error.message;
  }
}

async function deleteTicketFromDialog() {
  const ticketId = $("#ticketIdInput").value;
  if (!ticketId) return;
  try {
    await api(`/api/tickets/${ticketId}`, { method: "DELETE" });
    $("#ticketDialog").close();
    await loadBootstrap();
  } catch (error) {
    $("#ticketFormError").textContent = error.message;
  }
}

function selectedProject() {
  return state.data?.projects.find((project) => project.id === state.selectedProjectId);
}

function selectedWorkflow() {
  const project = selectedProject();
  return project ? state.data.workflows.find((workflow) => workflow.projectId === project.id) : null;
}

function resetWorkflowDraft() {
  const workflow = selectedWorkflow();
  state.workflowDraft = workflow ? JSON.parse(JSON.stringify(workflow)) : null;
}

function sortedStatuses(workflow) {
  return [...workflow.statuses].sort((a, b) => a.sortOrder - b.sortOrder);
}

function projectTickets() {
  return state.data.tickets.filter((ticket) => ticket.projectId === state.selectedProjectId);
}

function projectTypes() {
  return state.data.ticketTypes.filter((type) => type.projectId === state.selectedProjectId);
}

function activeUsers() {
  return state.data.users.filter((user) => user.active);
}

function ticketById(id) {
  return state.data.tickets.find((ticket) => ticket.id === id);
}

function typeById(id) {
  return state.data.ticketTypes.find((type) => type.id === id);
}

function userById(id) {
  return state.data.users.find((user) => user.id === id);
}

function statusById(id) {
  const workflow = selectedWorkflow();
  return workflow?.statuses.find((status) => status.id === id);
}

function canTransition(ticket, toStatusId) {
  const workflow = selectedWorkflow();
  if (!workflow) return false;
  return workflow.transitions.some((transition) => {
    return transition.fromStatusId === ticket.statusId &&
      transition.toStatusId === toStatusId &&
      transition.allowedRoles.includes(state.data.currentUser.role);
  });
}

function sortTickets(tickets, sort) {
  const copy = [...tickets];
  if (sort === "title") return copy.sort((a, b) => a.title.localeCompare(b.title));
  if (sort === "priority") return copy.sort((a, b) => priorityRank(b.priority) - priorityRank(a.priority));
  if (sort === "status") {
    const workflow = selectedWorkflow();
    return copy.sort((a, b) => statusRank(workflow, a.statusId) - statusRank(workflow, b.statusId));
  }
  if (sort === "updated") return copy.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  return copy.sort((a, b) => a.number - b.number);
}

function priorityRank(priority) {
  return ["LOW", "MEDIUM", "HIGH", "CRITICAL"].indexOf(priority);
}

function statusRank(workflow, statusId) {
  return workflow?.statuses.find((status) => status.id === statusId)?.sortOrder ?? 999;
}

function optionExists(selector, value) {
  if (!value) return true;
  return [...document.querySelector(selector).options].some((option) => option.value === value);
}

function roleLabel(role) {
  return role === "ADMIN" ? "Admin" : "Member";
}

function showToast(message) {
  const toast = $("#toast");
  toast.textContent = message;
  toast.classList.remove("hidden");
  clearTimeout(showToast.timeout);
  showToast.timeout = setTimeout(() => toast.classList.add("hidden"), 3200);
}

function newId(prefix) {
  if (crypto.randomUUID) {
    return `${prefix}-${crypto.randomUUID()}`;
  }
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function escapeAttribute(value) {
  return escapeHtml(value).replaceAll("`", "&#096;");
}
