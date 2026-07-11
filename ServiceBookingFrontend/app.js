const DEFAULT_API_BASE = "http://121.40.96.66:8080/api";

const state = {
  apiBase: DEFAULT_API_BASE,
  token: localStorage.getItem("eldercare.token") || "",
  role: localStorage.getItem("eldercare.role") || "",
  name: localStorage.getItem("eldercare.name") || "",
  authMode: "login",
  activeAdminTab: "services",
  appointmentsFilter: "",
  selectedDate: today(),
  selectedServiceId: "",
  data: {}
};

const el = {
  authPanel: document.getElementById("authPanel"),
  sessionPanel: document.getElementById("sessionPanel"),
  viewTitle: document.getElementById("viewTitle"),
  viewActions: document.getElementById("viewActions"),
  content: document.getElementById("content"),
  toast: document.getElementById("toast")
};

document.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.target;
  const action = form.dataset.submit;
  if (!action) return;
  const payload = formData(form);

  try {
    setBusy(form, true);
    if (action === "login") await login(payload);
    if (action === "register") await register(payload);
    if (action === "create-service") await createService(payload);
  } catch (error) {
    notify(error.message, "error");
  } finally {
    setBusy(form, false);
  }
});

document.addEventListener("click", async (event) => {
  const button = event.target.closest("[data-action]");
  if (!button) return;
  const action = button.dataset.action;

  try {
    button.disabled = true;
    if (action === "switch-auth") {
      state.authMode = button.dataset.mode;
      renderAuth();
      return;
    }
    if (action === "logout") {
      logout();
      return;
    }
    if (action === "refresh") {
      await refreshDashboard();
      return;
    }
    if (action === "load-available") {
      await loadAvailableEmployees();
      return;
    }
    if (action === "book") {
      await bookEmployee(button.dataset.employeeId);
      return;
    }
    if (action === "cancel-user") {
      await cancelUserAppointment(button.dataset.id);
      return;
    }
    if (action === "employee-status") {
      await updateEmployeeStatus(button.dataset.working === "true");
      return;
    }
    if (action === "employee-complete") {
      await employeeComplete(button.dataset.id);
      return;
    }
    if (action === "employee-cancel") {
      await employeeCancel(button.dataset.id);
      return;
    }
    if (action === "navigate") {
      openAmapNavigation(button.dataset.address);
      return;
    }
    if (action === "admin-tab") {
      state.activeAdminTab = button.dataset.tab;
      renderAdminDashboard();
      return;
    }
    if (action === "save-service") {
      await saveService(button.dataset.id);
      return;
    }
    if (action === "delete-service") {
      await deleteService(button.dataset.id);
      return;
    }
    if (action === "toggle-user") {
      await toggleUser(button.dataset.id, button.dataset.active === "true");
      return;
    }
    if (action === "delete-user") {
      await deleteUser(button.dataset.id);
      return;
    }
    if (action === "toggle-employee") {
      await toggleEmployee(button.dataset.id, button.dataset.active === "true");
      return;
    }
    if (action === "delete-employee") {
      await deleteEmployee(button.dataset.id);
    }
  } catch (error) {
    notify(error.message, "error");
  } finally {
    button.disabled = false;
  }
});

document.addEventListener("change", async (event) => {
  const target = event.target;
  if (target.id === "registerRole") renderRegisterRoleFields(target.value);
  if (target.id === "serviceSelect") state.selectedServiceId = target.value;
  if (target.id === "bookingDate") state.selectedDate = target.value || today();
  if (target.id === "appointmentFilter") {
    state.appointmentsFilter = target.value;
    await refreshDashboard();
  }
  if (target.id === "adminStatusFilter") {
    state.appointmentsFilter = target.value;
    await loadAdminData();
    renderAdminDashboard();
  }
});

render();
if (state.token) refreshDashboard().catch((error) => notify(error.message, "error"));

function render() {
  renderAuth();
  renderSession();
  if (!state.token) {
    el.viewTitle.textContent = "登录后开始";
    el.viewActions.innerHTML = "";
    el.content.innerHTML = `
      <div class="grid two">
        <section class="card">
          <h3>工作方式</h3>
          <p class="muted">这个网页前端直接调用 Spring Boot 后端接口，不连接 MySQL。登录成功后会按角色进入老人端、员工端或管理员后台。</p>
        </section>
        <section class="card flat">
          <h3>接口状态</h3>
          <p class="muted">请先登录。除注册和登录外，后端接口需要在请求头里携带 JWT 令牌。</p>
          <button class="btn secondary" type="button" data-action="refresh">重新检测</button>
        </section>
      </div>
    `;
  }
}

function renderAuth() {
  if (state.token) {
    el.authPanel.classList.add("hidden");
    return;
  }
  el.authPanel.classList.remove("hidden");
  if (state.authMode === "register") {
    el.authPanel.innerHTML = `
      <h2 id="auth-title">注册账号</h2>
      <div class="tabs" role="tablist">
        <button class="tab" type="button" data-action="switch-auth" data-mode="login">登录</button>
        <button class="tab active" type="button" data-action="switch-auth" data-mode="register">注册</button>
      </div>
      <div class="divider"></div>
      <form data-submit="register">
        <label class="field">
          <span>角色</span>
          <select id="registerRole" name="role">
            <option value="USER">老人用户</option>
            <option value="EMPLOYEE">护工员工</option>
          </select>
        </label>
        <label class="field"><span>用户名</span><input name="username" required autocomplete="username"></label>
        <label class="field"><span>密码</span><input name="password" type="password" required autocomplete="new-password"></label>
        <label class="field"><span>姓名</span><input name="name" required></label>
        <label class="field"><span>联系电话</span><input name="phone" required inputmode="tel"></label>
        <label class="field"><span>年龄</span><input name="age" type="number" min="1" max="120" required></label>
        <div id="roleFields"></div>
        <button class="btn" type="submit">创建账号</button>
      </form>
    `;
    renderRegisterRoleFields("USER");
    return;
  }

  el.authPanel.innerHTML = `
    <h2 id="auth-title">登录系统</h2>
    <div class="tabs" role="tablist">
      <button class="tab active" type="button" data-action="switch-auth" data-mode="login">登录</button>
      <button class="tab" type="button" data-action="switch-auth" data-mode="register">注册</button>
    </div>
    <div class="divider"></div>
    <form data-submit="login">
      <label class="field"><span>用户名</span><input name="username" required autocomplete="username"></label>
      <label class="field"><span>密码</span><input name="password" type="password" required autocomplete="current-password"></label>
      <button class="btn" type="submit">登录</button>
    </form>
  `;
}

function renderRegisterRoleFields(role) {
  const box = document.getElementById("roleFields");
  if (!box) return;
  if (role === "EMPLOYEE") {
    box.innerHTML = `<label class="field"><span>薪资要求</span><input name="salary" type="number" min="0" step="0.01" required></label>`;
  } else {
    box.innerHTML = `<label class="field"><span>居住地址</span><textarea name="address" required></textarea></label>`;
  }
}

function renderSession() {
  if (!state.token) {
    el.sessionPanel.classList.add("hidden");
    el.sessionPanel.innerHTML = "";
    return;
  }
  el.authPanel.classList.add("hidden");
  el.sessionPanel.classList.remove("hidden");
  el.sessionPanel.innerHTML = `
    <h2 id="session-title">当前账号</h2>
    <p><strong>${escapeHtml(state.name || "已登录")}</strong></p>
    <p class="muted small">${roleName(state.role)}</p>
    <div class="divider"></div>
    <button class="btn secondary" type="button" data-action="refresh">刷新数据</button>
    <button class="btn ghost" type="button" data-action="logout">退出登录</button>
  `;
}

async function login(body) {
  const res = await api("/auth/login", { method: "POST", body });
  state.token = res.token;
  state.role = res.role;
  state.name = res.name;
  localStorage.setItem("eldercare.token", state.token);
  localStorage.setItem("eldercare.role", state.role);
  localStorage.setItem("eldercare.name", state.name);
  renderSession();
  notify("登录成功", "success");
  await refreshDashboard();
}

async function register(body) {
  body.age = Number(body.age);
  if (body.salary !== undefined) body.salary = Number(body.salary);
  await api("/auth/register", { method: "POST", body, auth: false });
  notify("注册成功，请登录", "success");
  state.authMode = "login";
  renderAuth();
}

function logout() {
  state.token = "";
  state.role = "";
  state.name = "";
  state.data = {};
  localStorage.removeItem("eldercare.token");
  localStorage.removeItem("eldercare.role");
  localStorage.removeItem("eldercare.name");
  render();
  notify("已退出登录", "success");
}

async function refreshDashboard() {
  if (!state.token) {
    render();
    return;
  }
  if (state.role === "USER") await loadUserDashboard();
  if (state.role === "EMPLOYEE") await loadEmployeeDashboard();
  if (state.role === "ADMIN") await loadAdminData();
  renderSession();
  if (state.role === "USER") renderUserDashboard();
  if (state.role === "EMPLOYEE") renderEmployeeDashboard();
  if (state.role === "ADMIN") renderAdminDashboard();
}

async function loadUserDashboard() {
  const [me, services, appointments] = await Promise.all([
    api("/users/me"),
    api("/services"),
    api(`/appointments/me${statusQuery()}`)
  ]);
  state.data.user = me;
  state.data.services = services;
  state.data.userAppointments = appointments;
  await loadAvailableEmployees(false);
}

async function loadAvailableEmployees(showMessage = true) {
  const date = state.selectedDate || today();
  const employees = await api(`/employees/available?date=${encodeURIComponent(date)}`);
  state.data.availableEmployees = employees;
  if (state.role === "USER") renderUserDashboard();
  if (showMessage) notify("可预约员工已刷新", "success");
}

function renderUserDashboard() {
  const user = state.data.user || {};
  const services = state.data.services || [];
  const employees = state.data.availableEmployees || [];
  const appointments = state.data.userAppointments || [];
  el.viewTitle.textContent = "老人用户工作台";
  el.viewActions.innerHTML = `<button class="btn secondary" type="button" data-action="refresh">刷新</button>`;
  el.content.innerHTML = `
    <div class="grid two">
      <section class="card">
        <h3>我的信息</h3>
        <p><strong>${escapeHtml(user.name)}</strong> <span class="badge">${escapeHtml(user.username)}</span></p>
        <p class="muted">电话：${escapeHtml(user.phone)}　年龄：${escapeHtml(user.age)}</p>
        <p class="muted">地址：${escapeHtml(user.address)}</p>
      </section>
      <section class="card">
        <h3>预约筛选</h3>
        <div class="split-form">
          <label class="field">
            <span>服务项目</span>
            <select id="serviceSelect">
              <option value="">请选择服务项目</option>
              ${services.map((item) => `<option value="${item.id}" ${String(item.id) === String(state.selectedServiceId) ? "selected" : ""}>${escapeHtml(item.name)} ${item.referencePrice ? "¥" + escapeHtml(item.referencePrice) : ""}</option>`).join("")}
            </select>
          </label>
          <label class="field">
            <span>预约日期</span>
            <input id="bookingDate" type="date" min="${today()}" value="${escapeHtml(state.selectedDate || today())}">
          </label>
        </div>
        <button class="btn secondary" type="button" data-action="load-available">查询可预约员工</button>
      </section>
    </div>

    <section class="card">
      <h3>可预约员工</h3>
      ${renderEmployees(employees)}
    </section>

    <section class="card">
      <div class="row">
        <h3 class="section-title">我的预约</h3>
        ${filterSelect()}
      </div>
      ${appointmentsTable(appointments, "user")}
    </section>
  `;
}

function renderEmployees(employees) {
  if (!employees.length) return `<div class="empty">当前日期暂无可预约员工，可以更换日期后重试。</div>`;
  return `
    <div class="list">
      ${employees.map((employee) => `
        <article class="employee-card">
          <div>
            <h4>${escapeHtml(employee.name)} <span class="badge success">可预约</span></h4>
            <p>年龄：${escapeHtml(employee.age)}　电话：${escapeHtml(employee.phone)}　薪资：¥${escapeHtml(employee.salary)}</p>
          </div>
          <button class="btn" type="button" data-action="book" data-employee-id="${employee.id}">预约</button>
        </article>
      `).join("")}
    </div>
  `;
}

async function bookEmployee(employeeId) {
  if (!state.selectedServiceId) throw new Error("请先选择服务项目");
  const date = state.selectedDate || today();
  await api("/appointments", {
    method: "POST",
    body: {
      employeeId: Number(employeeId),
      serviceId: Number(state.selectedServiceId),
      appointmentDate: date
    }
  });
  notify("预约成功", "success");
  await loadUserDashboard();
  renderUserDashboard();
}

async function cancelUserAppointment(id) {
  if (!confirm("确认取消这条预约吗？")) return;
  await api(`/appointments/${id}/cancel`, { method: "PATCH" });
  notify("预约已取消", "success");
  await loadUserDashboard();
  renderUserDashboard();
}

async function loadEmployeeDashboard() {
  const [me, appointments] = await Promise.all([
    api("/employees/me"),
    api(`/employees/me/appointments${statusQuery()}`)
  ]);
  state.data.employee = me;
  state.data.employeeAppointments = appointments;
}

function renderEmployeeDashboard() {
  const employee = state.data.employee || {};
  const appointments = state.data.employeeAppointments || [];
  el.viewTitle.textContent = "护工员工工作台";
  el.viewActions.innerHTML = `<button class="btn secondary" type="button" data-action="refresh">刷新</button>`;
  el.content.innerHTML = `
    <div class="grid two">
      <section class="card">
        <h3>我的信息</h3>
        <p><strong>${escapeHtml(employee.name)}</strong> <span class="badge">${escapeHtml(employee.username)}</span></p>
        <p class="muted">电话：${escapeHtml(employee.phone)}　年龄：${escapeHtml(employee.age)}　薪资：¥${escapeHtml(employee.salary)}</p>
      </section>
      <section class="card">
        <h3>接单状态</h3>
        <p>${employee.working ? `<span class="badge success">工作中，可接单</span>` : `<span class="badge warning">暂停工作</span>`}</p>
        <button class="btn ${employee.working ? "accent" : ""}" type="button" data-action="employee-status" data-working="${!employee.working}">
          ${employee.working ? "暂停接单" : "开始接单"}
        </button>
      </section>
    </div>

    <section class="card">
      <div class="row">
        <h3 class="section-title">分配给我的预约</h3>
        ${filterSelect()}
      </div>
      ${appointmentsTable(appointments, "employee")}
    </section>
  `;
}

async function updateEmployeeStatus(working) {
  await api("/employees/me/status", { method: "PATCH", body: { isWorking: working } });
  notify(working ? "已开始接单" : "已暂停接单", "success");
  await loadEmployeeDashboard();
  renderEmployeeDashboard();
}

async function employeeComplete(id) {
  if (!confirm("确认将这条预约标记为已完成吗？")) return;
  await api(`/employees/me/appointments/${id}/complete`, { method: "PATCH" });
  notify("预约已标记完成", "success");
  await loadEmployeeDashboard();
  renderEmployeeDashboard();
}

async function employeeCancel(id) {
  if (!confirm("确认取消这条分配给你的预约吗？")) return;
  await api(`/employees/me/appointments/${id}/cancel`, { method: "PATCH" });
  notify("预约已取消", "success");
  await loadEmployeeDashboard();
  renderEmployeeDashboard();
}

async function loadAdminData() {
  const query = statusQuery();
  const [services, users, employees, appointments] = await Promise.all([
    api("/admin/services"),
    api("/admin/users"),
    api("/admin/employees"),
    api(`/admin/appointments${query}`)
  ]);
  state.data.adminServices = services;
  state.data.adminUsers = users;
  state.data.adminEmployees = employees;
  state.data.adminAppointments = appointments;
}

function renderAdminDashboard() {
  const services = state.data.adminServices || [];
  const users = state.data.adminUsers || [];
  const employees = state.data.adminEmployees || [];
  const appointments = state.data.adminAppointments || [];
  el.viewTitle.textContent = "管理员后台";
  el.viewActions.innerHTML = `<button class="btn secondary" type="button" data-action="refresh">刷新</button>`;
  el.content.innerHTML = `
    <div class="grid three">
      <section class="card metric"><span class="muted">服务项目</span><strong>${services.length}</strong></section>
      <section class="card metric"><span class="muted">老人用户</span><strong>${users.length}</strong></section>
      <section class="card metric"><span class="muted">护工员工</span><strong>${employees.length}</strong></section>
    </div>
    <section class="card">
      <div class="tabs">
        ${adminTab("services", "服务项目")}
        ${adminTab("users", "用户管理")}
        ${adminTab("employees", "员工管理")}
        ${adminTab("appointments", "预约总览")}
      </div>
    </section>
    ${adminPanel()}
  `;
}

function adminTab(tab, label) {
  return `<button class="tab ${state.activeAdminTab === tab ? "active" : ""}" type="button" data-action="admin-tab" data-tab="${tab}">${label}</button>`;
}

function adminPanel() {
  if (state.activeAdminTab === "services") return adminServicesPanel();
  if (state.activeAdminTab === "users") return adminUsersPanel();
  if (state.activeAdminTab === "employees") return adminEmployeesPanel();
  return adminAppointmentsPanel();
}

function adminServicesPanel() {
  const services = state.data.adminServices || [];
  return `
    <section class="card">
      <h3>新增服务项目</h3>
      <form class="split-form" data-submit="create-service">
        <label class="field"><span>名称</span><input name="name" required></label>
        <label class="field"><span>参考价格</span><input name="referencePrice" type="number" min="0" step="0.01" value="0" required></label>
        <label class="field full"><span>说明</span><textarea name="description"></textarea></label>
        <label class="field"><span>预约状态</span><select name="active"><option value="true">可预约</option><option value="false">暂停预约</option></select></label>
        <div class="field row end"><span>&nbsp;</span><button class="btn" type="submit">新增</button></div>
      </form>
    </section>
    <section class="card">
      <h3>服务项目列表</h3>
      <div class="table-wrap">
        <table>
          <thead><tr><th>名称</th><th>说明</th><th>价格</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            ${services.map((item) => `
              <tr data-service-row="${item.id}">
                <td><input data-field="name" value="${attr(item.name)}"></td>
                <td><input data-field="description" value="${attr(item.description)}"></td>
                <td><input data-field="referencePrice" type="number" min="0" step="0.01" value="${attr(item.referencePrice || 0)}"></td>
                <td>
                  <select data-field="active">
                    <option value="true" ${item.active ? "selected" : ""}>可预约</option>
                    <option value="false" ${!item.active ? "selected" : ""}>暂停预约</option>
                  </select>
                </td>
                <td class="row">
                  <button class="btn secondary" type="button" data-action="save-service" data-id="${item.id}">保存</button>
                  <button class="btn danger" type="button" data-action="delete-service" data-id="${item.id}">删除</button>
                </td>
              </tr>
            `).join("")}
          </tbody>
        </table>
      </div>
    </section>
  `;
}

function adminUsersPanel() {
  const users = state.data.adminUsers || [];
  return `
    <section class="card">
      <h3>用户列表</h3>
      <div class="table-wrap">
        <table>
          <thead><tr><th>姓名</th><th>用户名</th><th>电话</th><th>地址</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>${users.map((user) => `
            <tr>
              <td>${escapeHtml(user.name)}</td>
              <td>${escapeHtml(user.username)}</td>
              <td>${escapeHtml(user.phone)}</td>
              <td>${escapeHtml(user.address)}</td>
              <td>${activeBadge(user.active)}</td>
              <td class="row">
                <button class="btn secondary" type="button" data-action="toggle-user" data-id="${user.id}" data-active="${!user.active}">${user.active ? "禁用" : "启用"}</button>
                <button class="btn danger" type="button" data-action="delete-user" data-id="${user.id}">删除</button>
              </td>
            </tr>
          `).join("")}</tbody>
        </table>
      </div>
    </section>
  `;
}

function adminEmployeesPanel() {
  const employees = state.data.adminEmployees || [];
  return `
    <section class="card">
      <h3>员工列表</h3>
      <div class="table-wrap">
        <table>
          <thead><tr><th>姓名</th><th>用户名</th><th>电话</th><th>薪资</th><th>接单</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>${employees.map((employee) => `
            <tr>
              <td>${escapeHtml(employee.name)}</td>
              <td>${escapeHtml(employee.username)}</td>
              <td>${escapeHtml(employee.phone)}</td>
              <td>¥${escapeHtml(employee.salary)}</td>
              <td>${employee.working ? `<span class="badge success">工作中</span>` : `<span class="badge warning">暂停</span>`}</td>
              <td>${activeBadge(employee.active)}</td>
              <td class="row">
                <button class="btn secondary" type="button" data-action="toggle-employee" data-id="${employee.id}" data-active="${!employee.active}">${employee.active ? "禁用" : "启用"}</button>
                <button class="btn danger" type="button" data-action="delete-employee" data-id="${employee.id}">删除</button>
              </td>
            </tr>
          `).join("")}</tbody>
        </table>
      </div>
    </section>
  `;
}

function adminAppointmentsPanel() {
  const appointments = state.data.adminAppointments || [];
  return `
    <section class="card">
      <div class="row">
        <h3 class="section-title">全部预约</h3>
        <select id="adminStatusFilter">
          ${statusOptions()}
        </select>
      </div>
      ${appointmentsTable(appointments, "admin")}
    </section>
  `;
}

async function createService(body) {
  body.referencePrice = Number(body.referencePrice || 0);
  body.active = body.active === "true";
  await api("/admin/services", { method: "POST", body });
  notify("服务项目已新增", "success");
  await loadAdminData();
  renderAdminDashboard();
}

async function saveService(id) {
  const row = Array.from(document.querySelectorAll("[data-service-row]"))
    .find((item) => item.dataset.serviceRow === String(id));
  if (!row) throw new Error("未找到要保存的服务项目");
  const body = {};
  row.querySelectorAll("[data-field]").forEach((input) => {
    body[input.dataset.field] = input.value;
  });
  body.referencePrice = Number(body.referencePrice || 0);
  body.active = body.active === "true";
  await api(`/admin/services/${id}`, { method: "PUT", body });
  notify("服务项目已保存", "success");
  await loadAdminData();
  renderAdminDashboard();
}

async function deleteService(id) {
  if (!confirm("确认删除这个服务项目吗？")) return;
  await api(`/admin/services/${id}`, { method: "DELETE" });
  notify("服务项目已删除", "success");
  await loadAdminData();
  renderAdminDashboard();
}

async function toggleUser(id, active) {
  await api(`/admin/users/${id}/active`, { method: "PATCH", body: { active } });
  notify("用户状态已更新", "success");
  await loadAdminData();
  renderAdminDashboard();
}

async function deleteUser(id) {
  if (!confirm("确认删除这个用户吗？")) return;
  await api(`/admin/users/${id}`, { method: "DELETE" });
  notify("用户已删除", "success");
  await loadAdminData();
  renderAdminDashboard();
}

async function toggleEmployee(id, active) {
  await api(`/admin/employees/${id}/active`, { method: "PATCH", body: { active } });
  notify("员工状态已更新", "success");
  await loadAdminData();
  renderAdminDashboard();
}

async function deleteEmployee(id) {
  if (!confirm("确认删除这个员工吗？")) return;
  await api(`/admin/employees/${id}`, { method: "DELETE" });
  notify("员工已删除", "success");
  await loadAdminData();
  renderAdminDashboard();
}

function appointmentsTable(appointments, mode) {
  if (!appointments.length) return `<div class="empty">暂无预约记录。</div>`;
  return `
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>日期</th><th>服务</th><th>老人</th><th>员工</th><th>状态</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          ${appointments.map((item) => `
            <tr>
              <td>${escapeHtml(item.appointmentDate)}</td>
              <td>${escapeHtml(item.serviceName)}</td>
              <td>${escapeHtml(item.userName)}<br><span class="small muted">${escapeHtml(item.userPhone)} ${escapeHtml(item.userAddress)}</span></td>
              <td>${escapeHtml(item.employeeName)}<br><span class="small muted">${escapeHtml(item.employeePhone)}</span></td>
              <td>${statusBadge(item.status)}</td>
              <td>${appointmentActions(item, mode)}</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function appointmentActions(item, mode) {
  if (item.status !== "PENDING") return `<span class="muted small">无可用操作</span>`;
  if (mode === "user") {
    return `<button class="btn danger" type="button" data-action="cancel-user" data-id="${item.id}">取消</button>`;
  }
  if (mode === "employee") {
    const navigationButton = item.userAddress
      ? `<button class="btn secondary" type="button" data-action="navigate" data-address="${attr(item.userAddress)}">高德导航</button>`
      : `<button class="btn secondary" type="button" disabled title="该预约没有可用地址">地址缺失</button>`;
    return `
      <div class="row">
        ${navigationButton}
        <button class="btn secondary" type="button" data-action="employee-complete" data-id="${item.id}">完成</button>
        <button class="btn danger" type="button" data-action="employee-cancel" data-id="${item.id}">取消</button>
      </div>
    `;
  }
  return `<span class="muted small">仅查看</span>`;
}

function filterSelect() {
  return `<select id="appointmentFilter">${statusOptions()}</select>`;
}

function statusOptions() {
  const opts = [
    ["", "全部状态"],
    ["PENDING", "待服务"],
    ["COMPLETED", "已完成"],
    ["CANCELLED", "已取消"]
  ];
  return opts.map(([value, label]) => `<option value="${value}" ${state.appointmentsFilter === value ? "selected" : ""}>${label}</option>`).join("");
}

function statusQuery() {
  return state.appointmentsFilter ? `?status=${encodeURIComponent(state.appointmentsFilter)}` : "";
}

function statusBadge(status) {
  if (status === "COMPLETED") return `<span class="badge success">已完成</span>`;
  if (status === "CANCELLED") return `<span class="badge danger">已取消</span>`;
  return `<span class="badge warning">待服务</span>`;
}

function activeBadge(active) {
  return active ? `<span class="badge success">启用</span>` : `<span class="badge danger">禁用</span>`;
}

async function api(path, options = {}) {
  const auth = options.auth !== false;
  const headers = { "Content-Type": "application/json" };
  if (auth && state.token) headers.Authorization = `Bearer ${state.token}`;

  const response = await fetch(`${state.apiBase}${path}`, {
    method: options.method || "GET",
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined
  });

  let payload = null;
  try {
    payload = await response.json();
  } catch (_) {
    payload = { code: response.status, message: response.statusText || "请求失败", data: null };
  }

  if (response.status === 401 && auth) {
    logout();
    throw new Error(payload.message || "登录已过期，请重新登录");
  }

  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || "请求失败");
  }

  return payload.data;
}

function formData(form) {
  return Object.fromEntries(new FormData(form).entries());
}

function setBusy(form, busy) {
  form.querySelectorAll("button, input, select, textarea").forEach((node) => {
    node.disabled = busy;
  });
}

function notify(message, type = "") {
  el.toast.textContent = message;
  el.toast.className = `toast ${type}`;
  window.clearTimeout(notify.timer);
  notify.timer = window.setTimeout(() => el.toast.classList.add("hidden"), 2600);
}

function openAmapNavigation(address) {
  const destination = String(address || "").trim();
  if (!destination) throw new Error("该预约没有可用地址");

  const params = new URLSearchParams({
    keyword: destination,
    view: "map",
    src: "eldercare_service_booking",
    callnative: "1"
  });
  window.open(`https://uri.amap.com/search?${params.toString()}`, "_blank", "noopener,noreferrer");
}

function roleName(role) {
  if (role === "USER") return "老人用户";
  if (role === "EMPLOYEE") return "护工员工";
  if (role === "ADMIN") return "管理员";
  return "未登录";
}

function today() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function attr(value) {
  return escapeHtml(value);
}
