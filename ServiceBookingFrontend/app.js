const DEFAULT_API_BASE = "http://121.40.96.66:8080/api";

const state = {
  apiBase: DEFAULT_API_BASE,
  token: localStorage.getItem("eldercare.token") || "",
  role: localStorage.getItem("eldercare.role") || "",
  name: localStorage.getItem("eldercare.name") || "",
  authMode: "login",
  activeAdminTab: "services",
  appointmentsFilter: "",
  userView: "home",
  employeeView: "today",
  bookingStep: 1,
  selectedDate: today(),
  selectedServiceId: "",
  selectedEmployeeId: "",
  data: {}
};

const el = {
  app: document.getElementById("app"),
  authPanel: document.getElementById("authPanel"),
  sessionPanel: document.getElementById("sessionPanel"),
  viewEyebrow: document.getElementById("viewEyebrow"),
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
    if (action === "start-booking") {
      startBooking();
      return;
    }
    if (action === "show-user-appointments") {
      await showUserAppointments();
      return;
    }
    if (action === "user-home") {
      await showUserHome();
      return;
    }
    if (action === "select-service") {
      selectBookingService(button.dataset.serviceId);
      return;
    }
    if (action === "select-date") {
      await selectBookingDate(button.dataset.date);
      return;
    }
    if (action === "use-custom-date") {
      await selectBookingDate(state.selectedDate);
      return;
    }
    if (action === "choose-employee") {
      chooseBookingEmployee(button.dataset.employeeId);
      return;
    }
    if (action === "confirm-booking") {
      await bookEmployee(state.selectedEmployeeId);
      return;
    }
    if (action === "booking-back") {
      bookingBack();
      return;
    }
    if (action === "user-appointment-filter") {
      await filterUserAppointments(button.dataset.status || "");
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
    if (action === "employee-view") {
      await showEmployeeView(button.dataset.view);
      return;
    }
    if (action === "employee-task-filter") {
      await filterEmployeeTasks(button.dataset.status || "");
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
  if (target.matches("[data-password-toggle]")) {
    const password = target.closest("form")?.querySelector('input[name="password"]');
    if (password) password.type = target.checked ? "text" : "password";
  }
  if (target.id === "employeeWorkingToggle") {
    target.disabled = true;
    try {
      await updateEmployeeStatus(target.checked);
    } catch (error) {
      target.checked = !target.checked;
      notify(error.message, "error");
    } finally {
      target.disabled = false;
    }
  }
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
  syncLayout();
  renderAuth();
  renderSession();
  if (!state.token) {
    el.viewTitle.textContent = "登录后开始";
    el.viewActions.innerHTML = "";
    el.content.innerHTML = "";
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
      <div class="auth-card-header">
        <p>新用户注册</p>
        <h2 id="auth-title">创建您的账号</h2>
      </div>
      <div class="tabs auth-tabs" role="tablist" aria-label="账号操作">
        <button class="tab" type="button" role="tab" aria-selected="false" data-action="switch-auth" data-mode="login">登录</button>
        <button class="tab active" type="button" role="tab" aria-selected="true" data-action="switch-auth" data-mode="register">注册</button>
      </div>
      <form data-submit="register">
        <label class="field">
          <span>角色</span>
          <select id="registerRole" name="role">
            <option value="USER">老人用户</option>
            <option value="EMPLOYEE">护工员工</option>
          </select>
        </label>
        <label class="field"><span>用户名</span><input name="username" required autocomplete="username" placeholder="请输入用户名"></label>
        <label class="field"><span>密码</span><input name="password" type="password" required autocomplete="new-password" placeholder="请输入密码"></label>
        <label class="password-toggle"><input type="checkbox" data-password-toggle>显示密码</label>
        <label class="field"><span>姓名</span><input name="name" required placeholder="请输入真实姓名"></label>
        <label class="field"><span>联系电话</span><input name="phone" type="tel" required inputmode="tel" placeholder="请输入联系电话"></label>
        <label class="field"><span>年龄</span><input name="age" type="number" min="1" max="120" required placeholder="请输入年龄"></label>
        <div id="roleFields"></div>
        <button class="btn auth-submit" type="submit">完成注册</button>
      </form>
    `;
    renderRegisterRoleFields("USER");
    return;
  }

  el.authPanel.innerHTML = `
    <div class="auth-card-header">
      <p>欢迎回来</p>
      <h2 id="auth-title">登录您的账号</h2>
    </div>
    <div class="tabs auth-tabs" role="tablist" aria-label="账号操作">
      <button class="tab active" type="button" role="tab" aria-selected="true" data-action="switch-auth" data-mode="login">登录</button>
      <button class="tab" type="button" role="tab" aria-selected="false" data-action="switch-auth" data-mode="register">注册</button>
    </div>
    <form data-submit="login">
      <label class="field"><span>用户名</span><input name="username" required autocomplete="username" placeholder="请输入用户名"></label>
      <label class="field"><span>密码</span><input name="password" type="password" required autocomplete="current-password" placeholder="请输入密码"></label>
      <label class="password-toggle"><input type="checkbox" data-password-toggle>显示密码</label>
      <button class="btn auth-submit" type="submit">立即登录</button>
    </form>
  `;
}

function renderRegisterRoleFields(role) {
  const box = document.getElementById("roleFields");
  if (!box) return;
  const ageInput = box.closest("form")?.querySelector('input[name="age"]');
  if (ageInput) {
    ageInput.min = role === "EMPLOYEE" ? "18" : "1";
    ageInput.placeholder = role === "EMPLOYEE" ? "护工年龄需满18岁" : "请输入年龄";
  }
  if (role === "EMPLOYEE") {
    box.innerHTML = `<label class="field"><span>薪资要求</span><input name="salary" type="number" min="0" step="0.01" required placeholder="请输入薪资要求"></label>`;
  } else {
    box.innerHTML = `<label class="field"><span>居住地址</span><textarea name="address" required placeholder="请输入详细居住地址"></textarea></label>`;
  }
}

function renderSession() {
  syncLayout();
  if (!state.token) {
    el.sessionPanel.classList.remove("senior-session");
    el.sessionPanel.classList.add("hidden");
    el.sessionPanel.innerHTML = "";
    return;
  }
  el.authPanel.classList.add("hidden");
  el.sessionPanel.classList.remove("hidden");
  el.sessionPanel.classList.toggle("senior-session", state.role === "USER");
  if (state.role === "USER") {
    el.sessionPanel.innerHTML = `
      <h2 id="session-title">您好</h2>
      <p><strong>${escapeHtml(state.name || "已登录")}</strong></p>
      <div class="divider"></div>
      <button class="btn secondary" type="button" data-action="user-home">返回服务首页</button>
      <button class="btn ghost" type="button" data-action="logout">退出登录</button>
    `;
    return;
  }
  el.sessionPanel.innerHTML = `
    <h2 id="session-title">当前账号</h2>
    <p><strong>${escapeHtml(state.name || "已登录")}</strong></p>
    <p class="muted small">${roleName(state.role)}</p>
    <div class="divider"></div>
    <button class="btn secondary" type="button" data-action="refresh">刷新数据</button>
    <button class="btn ghost" type="button" data-action="logout">退出登录</button>
  `;
}

function syncLayout() {
  el.app.classList.toggle("is-authenticated", Boolean(state.token));
  el.app.classList.toggle("is-senior", Boolean(state.token) && state.role === "USER");
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
  state.appointmentsFilter = "";
  state.userView = "home";
  state.employeeView = "today";
  state.bookingStep = 1;
  state.selectedEmployeeId = "";
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
}

async function loadAvailableEmployees(showMessage = true) {
  const date = state.selectedDate || today();
  const employees = await api(`/employees/available?date=${encodeURIComponent(date)}`);
  state.data.availableEmployees = employees;
  if (showMessage) notify("服务人员已更新", "success");
}

function renderUserDashboard() {
  const user = state.data.user || {};
  const services = state.data.services || [];
  const employees = state.data.availableEmployees || [];
  const appointments = state.data.userAppointments || [];
  el.viewEyebrow.textContent = "长辈服务首页";
  el.viewTitle.textContent = `您好，${user.name || state.name || "欢迎使用"}`;
  el.viewActions.innerHTML = "";

  if (state.userView === "booking") {
    el.content.innerHTML = renderBookingWizard(services, employees);
    return;
  }
  if (state.userView === "appointments") {
    el.content.innerHTML = renderUserAppointments(appointments);
    return;
  }

  const pendingCount = appointments.filter((item) => item.status === "PENDING").length;
  el.content.innerHTML = `
    <div class="senior-shell senior-home">
      <section class="senior-greeting">
        <p>今天需要什么帮助？</p>
        <h3>请选择您要办理的事情</h3>
      </section>
      <div class="senior-entry-list">
        <button class="senior-entry primary" type="button" data-action="start-booking">
          <span class="senior-entry-symbol" aria-hidden="true">＋</span>
          <span><strong>立即预约服务</strong><small>选择服务、日期和服务人员</small></span>
          <span class="senior-entry-arrow" aria-hidden="true">›</span>
        </button>
        <button class="senior-entry" type="button" data-action="show-user-appointments">
          <span class="senior-entry-symbol" aria-hidden="true">日</span>
          <span><strong>查看我的预约</strong><small>${pendingCount ? `您有 ${pendingCount} 个待服务预约` : "查看预约记录和服务状态"}</small></span>
          <span class="senior-entry-arrow" aria-hidden="true">›</span>
        </button>
      </div>
    </div>
  `;
}

function startBooking() {
  state.userView = "booking";
  state.bookingStep = 1;
  state.selectedServiceId = "";
  state.selectedEmployeeId = "";
  state.data.availableEmployees = [];
  renderUserDashboard();
}

async function showUserHome() {
  state.userView = "home";
  state.appointmentsFilter = "";
  await loadUserDashboard();
  renderSession();
  renderUserDashboard();
}

async function showUserAppointments() {
  state.userView = "appointments";
  state.appointmentsFilter = "";
  await loadUserDashboard();
  renderUserDashboard();
}

async function filterUserAppointments(status) {
  state.appointmentsFilter = status;
  await loadUserDashboard();
  renderUserDashboard();
}

function selectBookingService(serviceId) {
  state.selectedServiceId = serviceId;
  state.bookingStep = 2;
  renderUserDashboard();
}

async function selectBookingDate(date) {
  if (!date) throw new Error("请选择服务日期");
  state.selectedDate = date;
  state.bookingStep = 3;
  state.selectedEmployeeId = "";
  await loadAvailableEmployees(false);
  renderUserDashboard();
}

function chooseBookingEmployee(employeeId) {
  state.selectedEmployeeId = employeeId;
  state.bookingStep = 4;
  renderUserDashboard();
}

function bookingBack() {
  if (state.bookingStep <= 1) {
    state.userView = "home";
  } else {
    state.bookingStep -= 1;
  }
  renderUserDashboard();
}

function renderBookingWizard(services, employees) {
  const step = state.bookingStep;
  const headings = ["您需要什么服务？", "您希望哪天服务？", "选择服务人员", "确认预约信息"];
  let body = "";

  if (step === 1) body = renderServiceChoices(services);
  if (step === 2) body = renderDateChoices();
  if (step === 3) body = renderStaffChoices(employees);
  if (step === 4) body = renderBookingConfirmation(services, employees);

  return `
    <div class="senior-shell booking-wizard">
      <div class="booking-toolbar">
        <button class="senior-back" type="button" data-action="booking-back">← 返回上一步</button>
        <span>第 ${step} 步，共 4 步</span>
      </div>
      ${renderBookingProgress(step)}
      <header class="booking-heading"><h3>${headings[step - 1]}</h3></header>
      ${body}
    </div>
  `;
}

function renderBookingProgress(step) {
  const labels = ["选服务", "选日期", "选人员", "确认"];
  return `<div class="booking-progress" aria-label="预约进度">${labels.map((label, index) => `
    <div class="${index + 1 <= step ? "active" : ""}"><span>${index + 1}</span><small>${label}</small></div>
  `).join("")}</div>`;
}

function renderServiceChoices(services) {
  if (!services.length) return `<div class="senior-empty">暂时没有可预约的服务，请稍后再试。</div>`;
  return `<div class="service-choice-grid">${services.map((service) => `
    <button class="service-choice" type="button" data-action="select-service" data-service-id="${service.id}">
      <strong>${escapeHtml(service.name)}</strong>
      <span>${escapeHtml(service.description || "上门养老服务")}</span>
      <small>${Number(service.referencePrice) > 0 ? `参考价格 ¥${escapeHtml(service.referencePrice)}` : "价格以实际服务为准"}</small>
    </button>
  `).join("")}</div>`;
}

function renderDateChoices() {
  const dates = [0, 1, 2].map((offset) => offsetDate(offset));
  const names = ["今天", "明天", "后天"];
  return `
    <div class="date-choice-grid">
      ${dates.map((date, index) => `<button class="date-choice" type="button" data-action="select-date" data-date="${date}"><strong>${names[index]}</strong><span>${formatFriendlyDate(date)}</span></button>`).join("")}
    </div>
    <div class="custom-date-choice">
      <label class="field"><span>选择其他日期</span><input id="bookingDate" type="date" min="${today()}" value="${escapeHtml(state.selectedDate || today())}"></label>
      <button class="btn senior-primary" type="button" data-action="use-custom-date">下一步：选择服务人员</button>
    </div>
  `;
}

function renderStaffChoices(employees) {
  if (!employees.length) return `
    <div class="senior-empty">
      <strong>${formatFriendlyDate(state.selectedDate)} 暂无可预约人员</strong>
      <p>请返回上一步选择其他日期。</p>
    </div>
  `;
  return `<div class="staff-choice-list">${employees.map((employee) => `
    <button class="staff-choice" type="button" data-action="choose-employee" data-employee-id="${employee.id}">
      <span class="staff-avatar" aria-hidden="true">${escapeHtml(firstCharacter(employee.name))}</span>
      <span><strong>${escapeHtml(employee.name)}</strong><small>平台服务人员 · 当日可预约</small></span>
      <span class="staff-select">选择</span>
    </button>
  `).join("")}</div>`;
}

function renderBookingConfirmation(services, employees) {
  const service = services.find((item) => String(item.id) === String(state.selectedServiceId));
  const employee = employees.find((item) => String(item.id) === String(state.selectedEmployeeId));
  if (!service || !employee) return `<div class="senior-empty">预约信息不完整，请返回上一步重新选择。</div>`;
  return `
    <div class="booking-summary">
      <dl>
        <div><dt>服务项目</dt><dd>${escapeHtml(service.name)}</dd></div>
        <div><dt>服务日期</dt><dd>${formatFriendlyDate(state.selectedDate)}</dd></div>
        <div><dt>服务人员</dt><dd>${escapeHtml(employee.name)}</dd></div>
      </dl>
      <button class="btn senior-primary confirm-booking" type="button" data-action="confirm-booking">确认预约</button>
    </div>
  `;
}

function renderUserAppointments(appointments) {
  const filters = [["", "全部"], ["PENDING", "待服务"], ["COMPLETED", "已完成"], ["CANCELLED", "已取消"]];
  return `
    <div class="senior-shell senior-appointments">
      <div class="appointments-heading">
        <button class="senior-back" type="button" data-action="user-home">← 返回服务首页</button>
        <h3>我的预约</h3>
      </div>
      <div class="senior-filter" aria-label="预约状态筛选">
        ${filters.map(([value, label]) => `<button type="button" class="${state.appointmentsFilter === value ? "active" : ""}" data-action="user-appointment-filter" data-status="${value}">${label}</button>`).join("")}
      </div>
      ${appointments.length ? `<div class="appointment-card-list">${appointments.map(renderUserAppointmentCard).join("")}</div>` : `<div class="senior-empty">这里暂时没有预约记录。</div>`}
    </div>
  `;
}

function renderUserAppointmentCard(item) {
  return `
    <article class="senior-appointment-card">
      <div class="appointment-card-top">
        <h4>${escapeHtml(item.serviceName)}</h4>
        ${seniorStatusBadge(item.status)}
      </div>
      <p class="appointment-date">${formatFriendlyDate(item.appointmentDate)}</p>
      <div class="appointment-person">
        <p><span>服务人员</span><strong>${escapeHtml(item.employeeName)}</strong></p>
        ${item.employeePhone ? `<p><span>联系电话</span><strong>${escapeHtml(item.employeePhone)}</strong></p>` : ""}
      </div>
      ${item.status === "PENDING" ? `<div class="appointment-card-actions"><button class="btn danger" type="button" data-action="cancel-user" data-id="${item.id}">取消这个预约</button></div>` : ""}
    </article>
  `;
}

function seniorStatusBadge(status) {
  if (status === "COMPLETED") return `<span class="senior-status completed">✓ 服务已完成</span>`;
  if (status === "CANCELLED") return `<span class="senior-status cancelled">× 预约已取消</span>`;
  return `<span class="senior-status pending">● 已预约，等待服务</span>`;
}

async function bookEmployee(employeeId) {
  if (!state.selectedServiceId) throw new Error("请先选择服务项目");
  if (!employeeId) throw new Error("请选择服务人员");
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
  state.userView = "appointments";
  state.bookingStep = 1;
  state.appointmentsFilter = "";
  state.selectedEmployeeId = "";
  await loadUserDashboard();
  renderUserDashboard();
}

async function cancelUserAppointment(id) {
  if (!confirm("确定要取消这个预约吗？")) return;
  await api(`/appointments/${id}/cancel`, { method: "PATCH" });
  notify("预约已取消", "success");
  await loadUserDashboard();
  renderUserDashboard();
}

async function loadEmployeeDashboard() {
  const query = state.employeeView === "all" ? statusQuery() : "";
  const [me, appointments, allAppointments] = await Promise.all([
    api("/employees/me"),
    api(`/employees/me/appointments${query}`),
    query ? api("/employees/me/appointments") : Promise.resolve(null)
  ]);
  state.data.employee = me;
  state.data.employeeAppointments = appointments;
  state.data.employeeAllAppointments = allAppointments || appointments;
}

function renderEmployeeDashboard() {
  const employee = state.data.employee || {};
  const appointments = state.data.employeeAppointments || [];
  const allAppointments = state.data.employeeAllAppointments || appointments;
  const todayTasks = allAppointments.filter((item) => item.appointmentDate === today());
  const pendingTasks = allAppointments.filter((item) => item.status === "PENDING");
  const completedToday = todayTasks.filter((item) => item.status === "COMPLETED").length;
  const nextTask = [...pendingTasks]
    .sort((a, b) => String(a.appointmentDate).localeCompare(String(b.appointmentDate)))
    .find((item) => item.appointmentDate >= today()) || pendingTasks[0];

  el.viewEyebrow.textContent = "护工任务中心";
  el.viewTitle.textContent = `${timeGreeting()}，${employee.name || state.name || "您好"}`;
  el.viewActions.innerHTML = "";
  el.content.innerHTML = `
    <div class="employee-workbench">
      <section class="employee-overview">
        <div class="employee-metrics" aria-label="任务概况">
          <div><span>今日任务</span><strong>${todayTasks.length}</strong><small>项</small></div>
          <div><span>待服务</span><strong>${pendingTasks.length}</strong><small>项</small></div>
          <div><span>今日完成</span><strong>${completedToday}</strong><small>项</small></div>
        </div>
        <label class="employee-status-switch">
          <input id="employeeWorkingToggle" type="checkbox" ${employee.working ? "checked" : ""}>
          <span class="switch-track" aria-hidden="true"><span></span></span>
          <span class="switch-copy">
            <strong>${employee.working ? "接单中" : "暂停接单"}</strong>
            <small>${employee.working ? "可以接收新的服务预约" : "暂时不接收新的服务预约"}</small>
          </span>
        </label>
      </section>

      <nav class="employee-view-tabs" aria-label="任务视图">
        <button type="button" class="${state.employeeView === "today" ? "active" : ""}" data-action="employee-view" data-view="today">任务首页</button>
        <button type="button" class="${state.employeeView === "all" ? "active" : ""}" data-action="employee-view" data-view="all">全部任务</button>
      </nav>

      ${state.employeeView === "all"
        ? renderAllEmployeeTasks(appointments)
        : renderEmployeeTodayView(nextTask, todayTasks)}
    </div>
  `;
}

async function showEmployeeView(view) {
  state.employeeView = view === "all" ? "all" : "today";
  state.appointmentsFilter = "";
  await loadEmployeeDashboard();
  renderEmployeeDashboard();
}

async function filterEmployeeTasks(status) {
  state.employeeView = "all";
  state.appointmentsFilter = status;
  await loadEmployeeDashboard();
  renderEmployeeDashboard();
}

function renderEmployeeTodayView(nextTask, todayTasks) {
  return `
    <section class="next-task-section">
      <div class="employee-section-heading">
        <div><p>优先处理</p><h3>下一项待服务任务</h3></div>
      </div>
      ${nextTask ? renderEmployeeNextTask(nextTask) : `<div class="employee-empty"><strong>当前没有待服务任务</strong><p>新的预约任务会显示在这里。</p></div>`}
    </section>
    <section class="today-task-section">
      <div class="employee-section-heading"><div><p>${formatFriendlyDate(today())}</p><h3>今日任务</h3></div></div>
      ${todayTasks.length ? `<div class="employee-task-list">${todayTasks.map((task) => renderEmployeeTaskCard(task, true)).join("")}</div>` : `<div class="employee-empty">今天暂时没有服务任务。</div>`}
    </section>
  `;
}

function renderEmployeeNextTask(task) {
  return `
    <article class="next-task-card">
      <div class="next-task-main">
        <div class="task-date-block"><span>服务日期</span><strong>${formatFriendlyDate(task.appointmentDate)}</strong></div>
        <div><p class="task-service-name">${escapeHtml(task.serviceName)}</p><p class="task-client">服务对象：<strong>${escapeHtml(task.userName)}</strong></p></div>
        ${employeeTaskStatus(task.status)}
      </div>
      <div class="task-address"><span>服务地点</span><strong>${escapeHtml(task.userAddress || "预约中未填写地址")}</strong></div>
      ${renderEmployeeTaskActions(task, true)}
    </article>
  `;
}

function renderAllEmployeeTasks(appointments) {
  const filters = [["", "全部"], ["PENDING", "待服务"], ["COMPLETED", "已完成"], ["CANCELLED", "已取消"]];
  return `
    <section class="all-task-section">
      <div class="employee-section-heading"><div><p>任务记录</p><h3>全部任务</h3></div></div>
      <div class="employee-task-filter">
        ${filters.map(([value, label]) => `<button type="button" class="${state.appointmentsFilter === value ? "active" : ""}" data-action="employee-task-filter" data-status="${value}">${label}</button>`).join("")}
      </div>
      ${appointments.length ? `<div class="employee-task-list">${appointments.map((task) => renderEmployeeTaskCard(task)).join("")}</div>` : `<div class="employee-empty">当前筛选条件下没有任务。</div>`}
    </section>
  `;
}

function renderEmployeeTaskCard(task, compact = false) {
  return `
    <article class="employee-task-card ${compact ? "compact" : ""}">
      <div class="employee-task-date"><strong>${formatFriendlyDate(task.appointmentDate)}</strong><span>${escapeHtml(task.serviceName)}</span></div>
      <div class="employee-task-client"><span>服务对象</span><strong>${escapeHtml(task.userName)}</strong><small>${escapeHtml(task.userAddress || "未填写地址")}</small></div>
      ${employeeTaskStatus(task.status)}
      ${renderEmployeeTaskActions(task)}
    </article>
  `;
}

function renderEmployeeTaskActions(task, prominent = false) {
  if (task.status !== "PENDING") return "";
  return `
    <div class="employee-task-actions ${prominent ? "prominent" : ""}">
      ${task.userPhone ? `<a class="btn secondary" href="tel:${attr(task.userPhone)}">电话联系</a>` : ""}
      ${task.userAddress ? `<button class="btn secondary" type="button" data-action="navigate" data-address="${attr(task.userAddress)}">查看路线</button>` : ""}
      <button class="btn" type="button" data-action="employee-complete" data-id="${task.id}">确认完成服务</button>
      <button class="btn danger" type="button" data-action="employee-cancel" data-id="${task.id}">取消任务</button>
    </div>
  `;
}

function employeeTaskStatus(status) {
  if (status === "COMPLETED") return `<span class="employee-task-status completed">✓ 已完成</span>`;
  if (status === "CANCELLED") return `<span class="employee-task-status cancelled">× 已取消</span>`;
  return `<span class="employee-task-status pending">● 待服务</span>`;
}

async function updateEmployeeStatus(working) {
  await api("/employees/me/status", { method: "PATCH", body: { isWorking: working } });
  notify(working ? "已进入接单状态" : "已暂停接收新预约", "success");
  await loadEmployeeDashboard();
  renderEmployeeDashboard();
}

async function employeeComplete(id) {
  if (!confirm("确定这项服务已经完成吗？")) return;
  await api(`/employees/me/appointments/${id}/complete`, { method: "PATCH" });
  notify("预约已标记完成", "success");
  await loadEmployeeDashboard();
  renderEmployeeDashboard();
}

async function employeeCancel(id) {
  if (!confirm("确定要取消这项任务吗？")) return;
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
  el.viewEyebrow.textContent = "当前工作台";
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

function offsetDate(offset) {
  const date = new Date();
  date.setDate(date.getDate() + offset);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function formatFriendlyDate(value) {
  if (!value) return "未选择日期";
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return String(value);
  const weekdays = ["星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"];
  return `${date.getMonth() + 1}月${date.getDate()}日 ${weekdays[date.getDay()]}`;
}

function firstCharacter(value) {
  return Array.from(String(value || "服务"))[0] || "服";
}

function timeGreeting() {
  const hour = new Date().getHours();
  if (hour < 11) return "上午好";
  if (hour < 14) return "中午好";
  if (hour < 18) return "下午好";
  return "晚上好";
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
